package com.zyvo.autosend;

import android.content.Context;
import android.content.SharedPreferences;

public final class Settings {
    private static final String PREF = "autosend_prefs";

    public static boolean enabled(Context c) {
        return prefs(c).getBoolean("enabled", false);
    }

    public static int hour(Context c) {
        return prefs(c).getInt("hour", 8);
    }

    public static int minute(Context c) {
        return prefs(c).getInt("minute", 30);
    }

    public static String chat(Context c) {
        return prefs(c).getString("chat", "");
    }

    public static String message(Context c) {
        return prefs(c).getString("message", "");
    }

    // Default ON: with the screen off there is no interactive window, so the
    // old behaviour could not drive WhatsApp's UI at all.
    public static boolean screenOff(Context c) {
        return prefs(c).getBoolean("screen_off", true);
    }

    // Best-effort secure-lock auto-unlock. The password lives only in this
    // app's private prefs — never in source, logs, or the public repo.
    public static boolean autoUnlock(Context c) {
        return prefs(c).getBoolean("auto_unlock", false);
    }

    public static String unlockPassword(Context c) {
        return prefs(c).getString("unlock_password", "");
    }

    public static void saveUnlock(Context c, boolean on, String password, boolean clear) {
        String existing = unlockPassword(c);
        String value = existing;
        if (clear) value = "";
        else if (password != null && !password.isEmpty()) value = password;
        prefs(c).edit()
                .putBoolean("auto_unlock", on)
                .putString("unlock_password", value)
                .apply();
    }

    public static void save(Context c, boolean enabled, int hour, int minute,
                            String chat, String message) {
        save(c, enabled, hour, minute, chat, message, screenOff(c));
    }

    public static void save(Context c, boolean enabled, int hour, int minute,
                            String chat, String message, boolean screenOff) {
        prefs(c).edit()
                .putBoolean("enabled", enabled)
                .putInt("hour", hour)
                .putInt("minute", minute)
                .putString("chat", chat == null ? "" : chat)
                .putString("message", message == null ? "" : message)
                .putBoolean("screen_off", screenOff)
                .apply();
    }

    public static String chatsJson(Context c) {
        return prefs(c).getString("chats_json", "[]");
    }

    public static long lastScan(Context c) {
        return prefs(c).getLong("chats_scan", 0L);
    }

    public static void saveChats(Context c, String json) {
        prefs(c).edit()
                .putString("chats_json", json == null ? "[]" : json)
                .putLong("chats_scan", System.currentTimeMillis())
                .apply();
    }

    public static String lastResult(Context c) {
        return prefs(c).getString("last_result", "");
    }

    public static void saveResult(Context c, String text) {
        prefs(c).edit()
                .putString("last_result", text == null ? "" : text)
                .putLong("result_ts", System.currentTimeMillis())
                .apply();
    }

    public static long resultTs(Context c) {
        return prefs(c).getLong("result_ts", 0L);
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }
}
