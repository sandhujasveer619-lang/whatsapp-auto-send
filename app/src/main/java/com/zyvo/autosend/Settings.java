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

    public static void save(Context c, boolean enabled, int hour, int minute,
                            String chat, String message) {
        prefs(c).edit()
                .putBoolean("enabled", enabled)
                .putInt("hour", hour)
                .putInt("minute", minute)
                .putString("chat", chat == null ? "" : chat)
                .putString("message", message == null ? "" : message)
                .apply();
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }
}
