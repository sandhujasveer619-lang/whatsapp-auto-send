package com.zyvo.autosend;

import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;

public final class Trigger {
    private Trigger() {}

    // Set while a screen-off send is in flight. Consumed once by the service
    // (prelude) and once when the send reaches a terminal result (re-lock).
    private static volatile boolean screenOffPrelude;
    private static volatile boolean screenWasOff;
    private static volatile PowerManager.WakeLock wakeLock;

    public static void send(Context c, String chat, String message) {
        if (chat == null || message == null) return;
        if (chat.trim().isEmpty() || message.trim().isEmpty()) return;

        WhatsappAccessibilityService.requestSend(chat, message);

        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        boolean off = Settings.screenOff(c) && pm != null && !pm.isInteractive();
        acquireLock(pm, off);
        screenOffPrelude = off;
        screenWasOff = off;

        Intent b = new Intent(WhatsappAccessibilityService.ACTION_TRIGGER);
        b.setPackage(c.getPackageName());
        c.sendBroadcast(b);
    }

    private static synchronized void acquireLock(PowerManager pm, boolean screenOn) {
        releaseWakeLock();
        if (pm == null) return;
        try {
            int level = screenOn
                    ? PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP
                    : PowerManager.PARTIAL_WAKE_LOCK;
            PowerManager.WakeLock lock = pm.newWakeLock(level, "autosend:send");
            lock.setReferenceCounted(false);
            lock.acquire(120000L);
            wakeLock = lock;
        } catch (Throwable ignored) {
            wakeLock = null;
        }
    }

    static synchronized void releaseWakeLock() {
        PowerManager.WakeLock lock = wakeLock;
        wakeLock = null;
        if (lock == null) return;
        try { if (lock.isHeld()) lock.release(); } catch (Throwable ignored) {}
    }

    static boolean consumeScreenOffPrelude() {
        boolean value = screenOffPrelude;
        screenOffPrelude = false;
        return value;
    }

    static boolean consumeScreenWasOff() {
        boolean value = screenWasOff;
        screenWasOff = false;
        return value;
    }
}
