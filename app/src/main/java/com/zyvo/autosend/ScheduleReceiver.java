package com.zyvo.autosend;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ScheduleReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        Scheduler.schedule(c);
        if (!Settings.enabled(c)) return;
        Trigger.send(c, Settings.chat(c), Settings.message(c));
    }
}
