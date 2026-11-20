package com.zyvo.autosend;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Window;
import android.view.WindowManager;

/**
 * Invisible activity used only while the screen was asleep when the alarm
 * fired. It turns the display on and asks the system to dismiss the keyguard
 * (works without a PIN/pattern); the accessibility service then drives
 * WhatsApp and locks the screen again when the send finishes.
 */
public class SendBridgeActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean done;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window w = getWindow();
        int flags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
        w.addFlags(flags);
        if (Build.VERSION.SDK_INT >= 27) {
            try { setShowWhenLocked(true); } catch (Throwable ignored) {}
            try { setTurnScreenOn(true); } catch (Throwable ignored) {}
        }

        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        if (km != null && km.isKeyguardLocked() && Build.VERSION.SDK_INT >= 26) {
            try {
                km.requestDismissKeyguard(this, new KeyguardManager.KeyguardDismissCallback() {
                    @Override public void onDismissSucceeded() { finishSoon(); }
                    @Override public void onDismissCancelled() { finishSoon(); }
                    @Override public void onDismissError() { finishSoon(); }
                });
            } catch (Throwable ignored) {
                finishSoon();
            }
        }
        // Fallback: a secure lock screen may never call back while unattended.
        handler.postDelayed(this::finishSoon, 1800);
    }

    private void finishSoon() {
        if (done) return;
        done = true;
        handler.postDelayed(this::finish, 250);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
