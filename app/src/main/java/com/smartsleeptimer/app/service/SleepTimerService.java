package com.smartsleeptimer.app.service;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.smartsleeptimer.app.MainActivity;
import com.smartsleeptimer.app.R;
import com.smartsleeptimer.app.model.TimerState;
import com.smartsleeptimer.app.util.TimeFormatter;
import com.smartsleeptimer.app.util.TimerPreferences;

/**
 * Foreground Service that manages reliable background countdown execution,
 * persists session state, schedules exact AlarmManager wakeups for Doze mode,
 * and maintains a persistent notification.
 */
public class SleepTimerService extends Service {

    private static final String TAG = "SleepTimerService";

    public static final String ACTION_START = "com.smartsleeptimer.app.ACTION_START";
    public static final String ACTION_PAUSE = "com.smartsleeptimer.app.ACTION_PAUSE";
    public static final String ACTION_RESUME = "com.smartsleeptimer.app.ACTION_RESUME";
    public static final String ACTION_CANCEL = "com.smartsleeptimer.app.ACTION_CANCEL";
    public static final String ACTION_EXPIRE = "com.smartsleeptimer.app.ACTION_EXPIRE";
    public static final String EXTRA_DURATION_MS = "extra_duration_ms";

    public static final String CHANNEL_ID = "sleep_timer_channel";
    private static final int NOTIFICATION_ID = 1001;
    private static final long UI_TICK_INTERVAL_MS = 500L;

    // Observable LiveData streams for UI synchronization
    private static final MutableLiveData<Long> remainingMillisLiveData = new MutableLiveData<>(30 * 60 * 1000L);
    private static final MutableLiveData<Float> progressFractionLiveData = new MutableLiveData<>(1.0f);
    private static final MutableLiveData<TimerState> timerStateLiveData = new MutableLiveData<>(TimerState.READY);
    private static final MutableLiveData<Long> selectedDurationLiveData = new MutableLiveData<>(30 * 60 * 1000L);

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private TimerPreferences preferences;
    private AlarmManager alarmManager;
    private PendingIntent alarmPendingIntent;

    private long totalDurationMillis = 30 * 60 * 1000L;
    private long remainingMillis = 30 * 60 * 1000L;
    private long endTimeElapsedRealtime = 0L;
    private TimerState currentState = TimerState.READY;

    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            if (currentState != TimerState.RUNNING) {
                return;
            }

            long nowElapsed = SystemClock.elapsedRealtime();
            long remaining = endTimeElapsedRealtime - nowElapsed;

            if (remaining <= 0) {
                completeTimer();
            } else {
                remainingMillis = remaining;
                float fraction = totalDurationMillis > 0
                        ? Math.max(0f, Math.min(1.0f, (float) remainingMillis / (float) totalDurationMillis))
                        : 0f;
                updateLiveData(remainingMillis, fraction, TimerState.RUNNING);
                mainHandler.postDelayed(this, UI_TICK_INTERVAL_MS);
            }
        }
    };

    // Public LiveData getters for ViewModel
    public static LiveData<Long> getRemainingMillisLiveData() {
        return remainingMillisLiveData;
    }

    public static LiveData<Float> getProgressFractionLiveData() {
        return progressFractionLiveData;
    }

    public static LiveData<TimerState> getTimerStateLiveData() {
        return timerStateLiveData;
    }

    public static LiveData<Long> getSelectedDurationLiveData() {
        return selectedDurationLiveData;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        preferences = new TimerPreferences(this);
        alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        createNotificationChannel();
        initAlarmPendingIntent();
        restorePersistedState();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.app_name);
            String description = "Persistent notification for active sleep timer";
            int importance = NotificationManager.IMPORTANCE_LOW; // Silent, non-intrusive
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            channel.setShowBadge(false);
            channel.enableLights(false);
            channel.enableVibration(false);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private void initAlarmPendingIntent() {
        Intent intent = new Intent(this, TimerCompletionReceiver.class);
        intent.setAction(TimerCompletionReceiver.ACTION_ALARM_COMPLETED);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        alarmPendingIntent = PendingIntent.getBroadcast(this, 0, intent, flags);
    }

    private void restorePersistedState() {
        currentState = preferences.getSavedState();
        totalDurationMillis = preferences.getTotalDurationMs(30 * 60 * 1000L);
        remainingMillis = preferences.calculateCurrentRemainingMs();

        if (currentState == TimerState.RUNNING) {
            if (remainingMillis <= 0) {
                currentState = TimerState.COMPLETED;
                preferences.saveInactiveState(TimerState.COMPLETED, totalDurationMillis);
            } else {
                endTimeElapsedRealtime = SystemClock.elapsedRealtime() + remainingMillis;
            }
        }

        float fraction = totalDurationMillis > 0
                ? Math.max(0f, Math.min(1.0f, (float) remainingMillis / (float) totalDurationMillis))
                : 0f;
        updateLiveData(remainingMillis, fraction, currentState);
        selectedDurationLiveData.postValue(totalDurationMillis);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            return START_STICKY;
        }

        String action = intent.getAction();
        switch (action) {
            case ACTION_START:
                long duration = intent.getLongExtra(EXTRA_DURATION_MS, totalDurationMillis);
                handleStart(duration);
                break;
            case ACTION_PAUSE:
                handlePause();
                break;
            case ACTION_RESUME:
                handleResume();
                break;
            case ACTION_CANCEL:
                handleCancel();
                break;
            case ACTION_EXPIRE:
                completeTimer();
                break;
        }

        return START_STICKY;
    }

    private void handleStart(long durationMs) {
        if (durationMs > 0) {
            this.totalDurationMillis = durationMs;
            this.remainingMillis = durationMs;
            selectedDurationLiveData.postValue(durationMs);
        }

        this.endTimeElapsedRealtime = SystemClock.elapsedRealtime() + remainingMillis;
        this.currentState = TimerState.RUNNING;

        preferences.saveRunningState(totalDurationMillis, remainingMillis, endTimeElapsedRealtime);
        scheduleExactAlarm(endTimeElapsedRealtime);

        startForegroundNotification(true);

        mainHandler.removeCallbacks(tickRunnable);
        mainHandler.post(tickRunnable);
    }

    private void handlePause() {
        if (currentState != TimerState.RUNNING) {
            return;
        }

        mainHandler.removeCallbacks(tickRunnable);
        cancelExactAlarm();

        long nowElapsed = SystemClock.elapsedRealtime();
        remainingMillis = Math.max(0L, endTimeElapsedRealtime - nowElapsed);
        currentState = TimerState.PAUSED;

        preferences.savePausedState(totalDurationMillis, remainingMillis);

        float fraction = totalDurationMillis > 0
                ? Math.max(0f, Math.min(1.0f, (float) remainingMillis / (float) totalDurationMillis))
                : 0f;
        updateLiveData(remainingMillis, fraction, TimerState.PAUSED);

        updateForegroundNotification(false);
    }

    private void handleResume() {
        if (currentState != TimerState.PAUSED || remainingMillis <= 0) {
            return;
        }

        this.endTimeElapsedRealtime = SystemClock.elapsedRealtime() + remainingMillis;
        this.currentState = TimerState.RUNNING;

        preferences.saveRunningState(totalDurationMillis, remainingMillis, endTimeElapsedRealtime);
        scheduleExactAlarm(endTimeElapsedRealtime);

        startForegroundNotification(true);

        mainHandler.removeCallbacks(tickRunnable);
        mainHandler.post(tickRunnable);
    }

    private void handleCancel() {
        mainHandler.removeCallbacks(tickRunnable);
        cancelExactAlarm();

        remainingMillis = totalDurationMillis;
        currentState = TimerState.READY;

        preferences.saveInactiveState(TimerState.READY, totalDurationMillis);

        updateLiveData(remainingMillis, 1.0f, TimerState.READY);

        stopForeground(true);
        stopSelf();
    }

    private void completeTimer() {
        mainHandler.removeCallbacks(tickRunnable);
        cancelExactAlarm();

        remainingMillis = 0L;
        currentState = TimerState.COMPLETED;

        preferences.saveInactiveState(TimerState.COMPLETED, totalDurationMillis);

        updateLiveData(0L, 0.0f, TimerState.COMPLETED);

        // Update notification with completion message before dismissing
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            Notification notification = buildNotification(false, true);
            nm.notify(NOTIFICATION_ID, notification);
        }

        stopForeground(false);
        stopSelf();
    }

    private void scheduleExactAlarm(long triggerElapsedRealtime) {
        if (alarmManager == null || alarmPendingIntent == null) {
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerElapsedRealtime,
                        alarmPendingIntent
                );
            } else {
                alarmManager.setExact(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerElapsedRealtime,
                        alarmPendingIntent
                );
            }
            Log.d(TAG, "Exact alarm scheduled for Doze wake-up at elapsed: " + triggerElapsedRealtime);
        } catch (SecurityException e) {
            Log.w(TAG, "SecurityException scheduling exact alarm: " + e.getMessage());
            // Fallback to inexact if exact alarm permission was restricted by OS
            alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerElapsedRealtime, alarmPendingIntent);
        }
    }

    private void cancelExactAlarm() {
        if (alarmManager != null && alarmPendingIntent != null) {
            try {
                alarmManager.cancel(alarmPendingIntent);
            } catch (Exception ignored) {
            }
        }
    }

    private void startForegroundNotification(boolean isRunning) {
        Notification notification = buildNotification(isRunning, false);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android 14+ (API 34)
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error entering foreground mode: " + e.getMessage());
        }
    }

    private void updateForegroundNotification(boolean isRunning) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            Notification notification = buildNotification(isRunning, false);
            nm.notify(NOTIFICATION_ID, notification);
        }
    }

    @NonNull
    private Notification buildNotification(boolean isRunning, boolean isCompleted) {
        Intent openAppIntent = new Intent(this, MainActivity.class);
        openAppIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int pFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent contentIntent = PendingIntent.getActivity(this, 10, openAppIntent, pFlags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_moon_headphones)
                .setContentTitle(getString(R.string.app_name))
                .setContentIntent(contentIntent)
                .setOngoing(isRunning)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

        if (isCompleted) {
            builder.setContentText(getString(R.string.timer_completed_toast));
            builder.setOngoing(false);
            return builder.build();
        }

        if (isRunning) {
            // Uses system chronometer count-down to update the UI on the lockscreen & status bar
            // without waking the CPU every second!
            long targetEpoch = System.currentTimeMillis() + remainingMillis;
            builder.setContentText("Sleep timer running • " + TimeFormatter.formatTime(remainingMillis));
            builder.setUsesChronometer(true);
            builder.setChronometerCountDown(true);
            builder.setWhen(targetEpoch);

            // Notification Action: Pause
            Intent pauseIntent = new Intent(this, SleepTimerService.class).setAction(ACTION_PAUSE);
            PendingIntent pausePi = PendingIntent.getService(this, 21, pauseIntent, pFlags);
            builder.addAction(R.drawable.ic_pause, getString(R.string.action_pause), pausePi);

            // Notification Action: Cancel
            Intent cancelIntent = new Intent(this, SleepTimerService.class).setAction(ACTION_CANCEL);
            PendingIntent cancelPi = PendingIntent.getService(this, 22, cancelIntent, pFlags);
            builder.addAction(R.drawable.ic_stop, getString(R.string.action_cancel), cancelPi);
        } else {
            // Paused
            builder.setContentText("Timer paused at " + TimeFormatter.formatTime(remainingMillis));
            builder.setUsesChronometer(false);

            // Notification Action: Resume
            Intent resumeIntent = new Intent(this, SleepTimerService.class).setAction(ACTION_RESUME);
            PendingIntent resumePi = PendingIntent.getService(this, 23, resumeIntent, pFlags);
            builder.addAction(R.drawable.ic_play_arrow, getString(R.string.action_resume), resumePi);

            // Notification Action: Cancel
            Intent cancelIntent = new Intent(this, SleepTimerService.class).setAction(ACTION_CANCEL);
            PendingIntent cancelPi = PendingIntent.getService(this, 24, cancelIntent, pFlags);
            builder.addAction(R.drawable.ic_stop, getString(R.string.action_cancel), cancelPi);
        }

        return builder.build();
    }

    private void updateLiveData(long remaining, float fraction, TimerState state) {
        remainingMillisLiveData.postValue(remaining);
        progressFractionLiveData.postValue(fraction);
        timerStateLiveData.postValue(state);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mainHandler.removeCallbacks(tickRunnable);
        cancelExactAlarm();
    }
}
