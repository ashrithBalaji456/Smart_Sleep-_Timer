package com.smartsleeptimer.app.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.core.content.ContextCompat;

/**
 * BroadcastReceiver triggered by AlarmManager.setExactAndAllowWhileIdle() when the countdown deadline expires.
 * Ensures timer completion triggers reliably even under deep Doze mode.
 */
public class TimerCompletionReceiver extends BroadcastReceiver {

    private static final String TAG = "TimerCompletionReceiver";
    public static final String ACTION_ALARM_COMPLETED = "com.smartsleeptimer.app.ACTION_ALARM_COMPLETED";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_ALARM_COMPLETED.equals(intent.getAction())) {
            return;
        }

        Log.i(TAG, "AlarmManager completion trigger received under idle/Doze mode.");

        Intent serviceIntent = new Intent(context, SleepTimerService.class);
        serviceIntent.setAction(SleepTimerService.ACTION_EXPIRE);
        try {
            ContextCompat.startForegroundService(context, serviceIntent);
        } catch (Exception e) {
            Log.e(TAG, "Error routing alarm completion to service: " + e.getMessage());
        }
    }
}
