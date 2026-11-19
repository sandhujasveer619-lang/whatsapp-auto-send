package com.zyvo.autosend;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;

public final class Trigger {
    private Trigger() {}

    public static void send(Context c, String chat, String message) {
        if (chat == null || message == null) return;
        if (chat.trim().isEmpty() || message.trim().isEmpty()) return;

        WhatsappAccessibilityService.requestSend(chat, message);

        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            PowerManager.WakeLock lock = pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK, "autosend:send");
            lock.acquire(60000L);
        }

        Intent b = new Intent(WhatsappAccessibilityService.ACTION_TRIGGER);
        b.setPackage(c.getPackageName());
        c.sendBroadcast(b);
    }
}
