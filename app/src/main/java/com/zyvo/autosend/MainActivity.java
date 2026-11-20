package com.zyvo.autosend;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;

public class MainActivity extends Activity {
    private static final int REQUEST_NOTIFICATIONS = 7;
    private WebView web;
    private long lastScan;
    private boolean chatsReceiverRegistered;
    private boolean resultReceiverRegistered;

    private final BroadcastReceiver chatsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            injectChatsIfNew();
        }
    };

    private final BroadcastReceiver resultReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            String msg = i != null ? i.getStringExtra(WhatsappAccessibilityService.EXTRA_RESULT) : null;
            if (msg != null) showResult(msg);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        web = new WebView(this);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new JsBridge(), "Android");
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");

        lastScan = Settings.lastScan(this);
        IntentFilter filter = new IntentFilter(ChatPicker.ACTION_CHATS_READY);
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(chatsReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(chatsReceiver, filter);
            }
            chatsReceiverRegistered = true;
        } catch (Throwable ignored) {}

        IntentFilter resultFilter = new IntentFilter(WhatsappAccessibilityService.ACTION_RESULT);
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(resultReceiver, resultFilter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(resultReceiver, resultFilter);
            }
            resultReceiverRegistered = true;
        } catch (Throwable ignored) {}

        if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
        }

        if (Build.VERSION.SDK_INT >= 31) {
            AlarmManager alarm = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (alarm != null && !alarm.canScheduleExactAlarms()) {
                try {
                    startActivity(new Intent(
                            android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:" + getPackageName())));
                } catch (Throwable ignored) {}
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        injectChatsIfNew();
        String last = Settings.lastResult(this);
        if (last != null && !last.isEmpty()) showResult(last);
        web.post(() -> web.evaluateJavascript(
                "if (typeof refreshPerms === 'function') refreshPerms();", null));
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        injectChatsIfNew();
    }

    @Override
    protected void onDestroy() {
        if (chatsReceiverRegistered) {
            try { unregisterReceiver(chatsReceiver); } catch (Throwable ignored) {}
            chatsReceiverRegistered = false;
        }
        if (resultReceiverRegistered) {
            try { unregisterReceiver(resultReceiver); } catch (Throwable ignored) {}
            resultReceiverRegistered = false;
        }
        super.onDestroy();
    }

    private void showResult(String msg) {
        final String safe = msg.replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\r", "")
                .replace("\n", "\\n");
        web.post(() -> web.evaluateJavascript(
                "if (typeof showResult === 'function') showResult('" + safe + "');", null));
    }

    private void injectChatsIfNew() {
        long current = Settings.lastScan(this);
        if (current == 0L || current == lastScan) return;
        lastScan = current;
        String json = Settings.chatsJson(this);
        if (json == null) json = "[]";
        final String safe = json.replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\r", "")
                .replace("\n", "\\n");
        web.post(() -> web.evaluateJavascript(
                "if (typeof onChatsListed === 'function') onChatsListed('" + safe + "');", null));
    }

    private class JsBridge {
        @JavascriptInterface
        public String getSettings() {
            try {
                JSONObject o = new JSONObject();
                o.put("enabled", Settings.enabled(MainActivity.this));
                o.put("hour", Settings.hour(MainActivity.this));
                o.put("minute", Settings.minute(MainActivity.this));
                o.put("chat", Settings.chat(MainActivity.this));
                o.put("message", Settings.message(MainActivity.this));
                o.put("screenOff", Settings.screenOff(MainActivity.this));
                return o.toString();
            } catch (Throwable t) {
                return "{}";
            }
        }

        @JavascriptInterface
        public void save(String enabled, String hour, String minute, String chat,
                         String message, String screenOff) {
            int h = 8;
            int m = 30;
            try { h = Integer.parseInt(hour); } catch (Throwable ignored) {}
            try { m = Integer.parseInt(minute); } catch (Throwable ignored) {}
            boolean off = screenOff == null || Boolean.parseBoolean(screenOff);
            Settings.save(MainActivity.this, Boolean.parseBoolean(enabled), h, m, chat, message, off);
            Scheduler.schedule(MainActivity.this);
        }

        @JavascriptInterface
        public boolean isAccessibility() {
            return WhatsappAccessibilityService.isEnabled(MainActivity.this);
        }

        @JavascriptInterface
        public boolean canAlarms() {
            return Scheduler.canSchedule(MainActivity.this);
        }

        @JavascriptInterface
        public boolean hasNotifications() {
            return Build.VERSION.SDK_INT < 33 ||
                    ContextCompat.checkSelfPermission(MainActivity.this,
                            Manifest.permission.POST_NOTIFICATIONS)
                            == PackageManager.PERMISSION_GRANTED;
        }

        @JavascriptInterface
        public void openAccessibility() {
            try {
                startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS));
            } catch (Throwable ignored) {}
        }

        @JavascriptInterface
        public void openAlarms() {
            if (Build.VERSION.SDK_INT >= 31) {
                try {
                    startActivity(new Intent(
                            android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:" + getPackageName())));
                } catch (Throwable ignored) {}
            }
        }

        @JavascriptInterface
        public void openNotifications() {
            if (Build.VERSION.SDK_INT < 33) return;
            if (ContextCompat.checkSelfPermission(MainActivity.this,
                    Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
            } else {
                try {
                    startActivity(new Intent(
                            android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,
                                    getPackageName()));
                } catch (Throwable ignored) {}
            }
        }

        @JavascriptInterface
        public String getChats() {
            return Settings.chatsJson(MainActivity.this);
        }

        @JavascriptInterface
        public String getLastResult() {
            return Settings.lastResult(MainActivity.this);
        }

        @JavascriptInterface
        public void pickChats() {
            if (!WhatsappAccessibilityService.isEnabled(MainActivity.this)) {
                web.post(() -> web.evaluateJavascript(
                        "if (typeof toast === 'function') toast('Enable Accessibility first');", null));
                try {
                    startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS));
                } catch (Throwable ignored) {}
                return;
            }
            ChatPicker.requestScan(MainActivity.this);
            web.post(() -> web.evaluateJavascript(
                    "if (typeof toast === 'function') toast('Opening WhatsApp to read chats...');", null));
        }

        @JavascriptInterface
        public void testSend() {
            Trigger.send(MainActivity.this, Settings.chat(MainActivity.this),
                    Settings.message(MainActivity.this));
        }
    }
}
