package com.zyvo.autosend;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

public class WhatsappAccessibilityService extends AccessibilityService {
    public static final String ACTION_TRIGGER = "com.zyvo.autosend.TRIGGER";

    private static volatile String pendingChat;
    private static volatile String pendingMessage;
    private static volatile boolean pending;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean registered;

    private final BroadcastReceiver triggerReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            onTrigger();
        }
    };

    public static void requestSend(String chat, String message) {
        pendingChat = chat;
        pendingMessage = message;
        pending = true;
    }

    public static boolean isEnabled(Context c) {
        try {
            String flat = c.getPackageName() + "/" + WhatsappAccessibilityService.class.getName();
            String names = android.provider.Settings.Secure.getString(
                    c.getContentResolver(),
                    android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return names != null && names.contains(flat);
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        if (!registered) {
            IntentFilter filter = new IntentFilter(ACTION_TRIGGER);
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(triggerReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
                } else {
                    registerReceiver(triggerReceiver, filter);
                }
                registered = true;
            } catch (Throwable t) {
                Log.w("AutoSend", "receiver register failed", t);
            }
        }
        if (pending) handler.postDelayed(this::onTrigger, 400);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_TRIGGER.equals(intent.getAction())) {
            onTrigger();
        } else if (pending) {
            handler.postDelayed(this::onTrigger, 300);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (registered) {
            try { unregisterReceiver(triggerReceiver); } catch (Throwable ignored) {}
            registered = false;
        }
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Work is driven by the trigger broadcast; events are only used as a wake-up hint.
        if (pending && event != null) {
            CharSequence pkg = event.getPackageName();
            if (pkg != null && (pkg.toString().contains("whatsapp"))) {
                onTrigger();
            }
        }
    }

    @Override
    public void onInterrupt() {}

    private void onTrigger() {
        if (!pending) return;
        pending = false;
        final String chat = pendingChat;
        final String message = pendingMessage;
        if (chat == null || message == null) return;
        if (chat.trim().isEmpty() || message.trim().isEmpty()) return;

        launchWhatsApp();
        handler.postDelayed(() -> openChat(chat, message, 0), 2500);
    }

    private void launchWhatsApp() {
        PackageManager pm = getPackageManager();
        Intent in = pm.getLaunchIntentForPackage("com.whatsapp");
        if (in == null) in = pm.getLaunchIntentForPackage("com.whatsapp.w4b");
        if (in != null) {
            in.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(in);
            } catch (Throwable t) {
                Log.w("AutoSend", "launch failed", t);
            }
        }
    }

    private void openChat(final String chat, final String message, final int attempt) {
        if (attempt > 8) {
            Log.w("AutoSend", "could not find search in WhatsApp");
            return;
        }
        AccessibilityNodeInfo node = findByDesc("Search", "Search chats", "search");
        if (node == null) {
            node = findById("search", "action_search", "conversations_search", "search_button");
        }
        if (node == null) node = findByText("Search chats");

        if (node != null && click(node)) {
            handler.postDelayed(() -> typeSearch(chat, message, 0), 900);
            return;
        }

        AccessibilityNodeInfo direct = findByText(chat);
        if (direct != null && click(direct)) {
            handler.postDelayed(() -> typeMessage(message, 0), 1200);
            return;
        }

        handler.postDelayed(() -> openChat(chat, message, attempt + 1), 800);
    }

    private void typeSearch(final String chat, final String message, final int attempt) {
        if (attempt > 8) {
            Log.w("AutoSend", "could not find search input");
            return;
        }
        AccessibilityNodeInfo input = findById("search_input", "search_src_text", "search_query",
                "search_edit_text", "search_field");
        if (input == null) input = findEditable();

        if (input != null && setText(input, chat)) {
            handler.postDelayed(() -> pickResult(chat, message, 0), 1300);
        } else {
            handler.postDelayed(() -> typeSearch(chat, message, attempt + 1), 700);
        }
    }

    private void pickResult(final String chat, final String message, final int attempt) {
        if (attempt > 9) {
            Log.w("AutoSend", "could not find chat: " + chat);
            return;
        }
        AccessibilityNodeInfo node = findByText(chat);
        if (node == null) node = findByDesc(chat);
        if (node != null && click(node)) {
            handler.postDelayed(() -> typeMessage(message, 0), 1400);
        } else {
            handler.postDelayed(() -> pickResult(chat, message, attempt + 1), 700);
        }
    }

    private void typeMessage(final String message, final int attempt) {
        if (attempt > 9) {
            Log.w("AutoSend", "could not find message box");
            return;
        }
        AccessibilityNodeInfo input = findById("entry", "composer_input", "message_input",
                "chat_input", "input", "edit_text", "composer_edit_text");
        if (input == null) input = findEditable();

        if (input != null && setText(input, message)) {
            handler.postDelayed(() -> tapSend(message, 0), 600);
        } else {
            handler.postDelayed(() -> typeMessage(message, attempt + 1), 700);
        }
    }

    private void tapSend(final String message, final int attempt) {
        if (attempt > 9) {
            Log.w("AutoSend", "could not find send button");
            return;
        }
        AccessibilityNodeInfo send = findById("send", "send_button", "composer_send", "send_message");
        if (send == null) send = findByDesc("Send", "Send message");
        if (send == null) send = findByText("Send");
        if (send != null && click(send)) {
            Log.i("AutoSend", "Message sent to " + pendingChat);
        } else {
            handler.postDelayed(() -> tapSend(message, attempt + 1), 600);
        }
    }

    private AccessibilityNodeInfo root() {
        return getRootInActiveWindow();
    }

    private AccessibilityNodeInfo findById(String... ids) {
        return findById(root(), ids);
    }

    private AccessibilityNodeInfo findById(AccessibilityNodeInfo node, String... ids) {
        if (node == null) return null;
        String id = node.getViewIdResourceName();
        if (id != null) {
            for (String candidate : ids) {
                if (id.equals(candidate) || id.endsWith("/" + candidate)) return node;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findById(node.getChild(i), ids);
            if (found != null) return found;
        }
        return null;
    }

    private AccessibilityNodeInfo findByText(String text) {
        return findByText(root(), text);
    }

    private AccessibilityNodeInfo findByText(AccessibilityNodeInfo node, String text) {
        if (node == null) return null;
        CharSequence value = node.getText();
        if (value != null && value.toString().trim().equalsIgnoreCase(text.trim())) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findByText(node.getChild(i), text);
            if (found != null) return found;
        }
        return null;
    }

    private AccessibilityNodeInfo findByDesc(String... descriptions) {
        return findByDesc(root(), descriptions);
    }

    private AccessibilityNodeInfo findByDesc(AccessibilityNodeInfo node, String... descriptions) {
        if (node == null) return null;
        CharSequence desc = node.getContentDescription();
        if (desc != null) {
            for (String candidate : descriptions) {
                if (desc.toString().trim().equalsIgnoreCase(candidate.trim())) return node;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findByDesc(node.getChild(i), descriptions);
            if (found != null) return found;
        }
        return null;
    }

    private AccessibilityNodeInfo findEditable() {
        return findEditable(root());
    }

    private AccessibilityNodeInfo findEditable(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isEditable() && node.isFocusable()) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findEditable(node.getChild(i));
            if (found != null) return found;
        }
        return null;
    }

    private boolean click(AccessibilityNodeInfo node) {
        if (node == null) return false;
        if (node.isClickable()) return node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        AccessibilityNodeInfo parent = node.getParent();
        if (parent != null && parent.isClickable()) {
            return parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        }
        return false;
    }

    private boolean setText(AccessibilityNodeInfo node, String text) {
        if (node == null) return false;
        Bundle args = new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return true;
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
    }
}
