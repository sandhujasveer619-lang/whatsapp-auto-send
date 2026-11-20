package com.zyvo.autosend;

import android.content.Context;
import android.content.Intent;

public final class ChatPicker {
    private ChatPicker() {}

    public static final String ACTION_SCAN = "com.zyvo.autosend.SCAN_CHATS";
    public static final String ACTION_CHATS_READY = "com.zyvo.autosend.CHATS_READY";

    public static void requestScan(Context c) {
        Intent i = new Intent(c, WhatsappAccessibilityService.class);
        i.setAction(ACTION_SCAN);
        try { c.startService(i); } catch (Throwable ignored) {}

        Intent b = new Intent(ACTION_SCAN);
        b.setPackage(c.getPackageName());
        try { c.sendBroadcast(b); } catch (Throwable ignored) {}
    }
}
