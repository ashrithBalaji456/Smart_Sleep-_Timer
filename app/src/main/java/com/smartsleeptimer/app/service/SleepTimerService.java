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
import com.smartsleeptimer.app.media.MediaControlManager;
import com.smartsleeptimer.app.media.MediaControlResult;
import com.smartsleeptimer.app.model.TimerState;
import com.smartsleeptimer.app.util.TimeFormatter;
import com.smartsleeptimer.app.util.TimerPreferences;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Foreground Service that manages reliable background countdown execution,
 * persists session state, schedules exact AlarmManager wakeups for Doze mode,
 * automatically pauses media at timer completion with state verification (Phase 4),
 * and issues dedicated completion notifications.
 */
public class SleepTimerService extends Service {

    private static final String TAG = "SleepTimerService";

    public static final String ACTION_START = "com.smartsleeptimer.app.ACTION_START";
    public static final String ACTION_PAUSE = "com.smartsleeptimer.app.ACTION_PAUSE";
    public static final String ACTION_RESUME = "com.smartsleeptimer.app.ACTION_RESUME";
    public static final String ACTION_CANCEL = "com.smartsleeptimer.app.ACTION_CANCEL";
    public static final String ACTION_EXPIRE = "com.smartsleeptimer.app.ACTION_EXPIRE";
    public static final String EXTRA_DURATION_MS = "extra_duration_ms";

    public static final String CHANNEL_ID_TIMER = "sleep_timer_channel";
    public static final String CHANNEL_ID_COMPLETION = "sleep_timer_completion_channel";
    private static final int NOTIFICATION_ID_TIMER = 1001;
    private static final int NOTIFICATION_ID_COMPLETION = 1002;
    private static final long UI_TICK_INTERVAL_MS = 500L;

    // Observable LiveData streams for UI synchronization
    private static final MutableLiveData<Long> remainingMillisLiveData = new MutableLiveData<>(30 * 60 * 1000L);
    private static final MutableLiveData<Float> progressFractionLiveData = new MutableLiveData<>(1.0f);
    private static final MutableLiveData<TimerState> timerStateLiveData = new MutableLiveData<>(TimerState.READY);
    private static final MutableLiveData<Long> selectedDurationLiveData = new MutableLiveData<>(30 * 60 * 1000L);
    private static final MutableLiveData<MediaControlResult> lastCompletionResultLiveData = new MutableLiveData<>();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean isCompleting = new AtomicBoolean(false);

    private TimerPreferences preferences;
    private MediaControlManager mediaControlManager;
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

    public static LiveData<MediaControlResult> getLastCompletionResultLiveData() {
        return lastCompletionResultLiveData;
    }

    /**
     * Updates LiveData streams when duration is modified while the timer service is idle.
     */
    public static void updateIdleDuration(long durationMs) {
        if (durationMs <= 0) return;
        selectedDurationLiveData.postValue(durationMs);
        remainingMillisLiveData.postValue(durationMs);
        progressFractionLiveData.postValue(1.0f);
        timerStateLiveData.postValue(TimerState.READY);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        preferences = new TimerPreferences(this);
        mediaControlManager = new MediaControlManager(this);
        alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

        createNotificationChannels();
        initAlarmPendingIntent();
        restorePersistedState();
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm == null) return;

            // Channel 1: Ongoing Countdown (Silent, Low Priority)
            CharSequence nameTimer = getString(R.string.app_name);
            String descTimer = "Persistent notification for active countdown timer";
            NotificationChannel channelTimer = new NotificationChannel(CHANNEL_ID_TIMER, nameTimer, NotificationManager.IMPORTANCE_LOW);
            channelTimer.setDescription(descTimer);
            channelTimer.setShowBadge(false);
            channelTimer.enableLights(false);
            channelTimer.enableVibration(false);
            nm.createNotificationChannel(channelTimer);

            // Channel 2: Completion Alert (Default/High Priority with alert)
            CharSequence nameComp = getString(R.string.notification_completion_channel_name);
            String descComp = getString(R.string.notification_completion_channel_desc);
            NotificationChannel channelComp = new NotificationChannel(CHANNEL_ID_COMPLETION, nameComp, NotificationManager.IMPORTANCE_DEFAULT);
            channelComp.setDescription(descComp);
            channelComp.setShowBadge(true);
            channelComp.enableLights(true);
            channelComp.enableVibration(true);
            nm.createNotificationChannel(channelComp);
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

        // Restore any existing completion result
        MediaControlResult savedResult = preferences.getLastCompletionResult();
        if (savedResult != null) {
            lastCompletionResultLiveData.postValue(savedResult);
        }

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
        isCompleting.set(false);

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
        isCompleting.set(false);

        this.endTimeElapsedRealtime = SystemClock.elapsedRealtime() + remainingMillis;
        this.currentState = TimerState.RUNNING;

        preferences.saveRunningState(totalDurationMillis, remainingMillis, endTimeElapsedRealtime);
        scheduleExactAlarm(endTimeElapsedRealtime);

        startForegroundNotification(true);

        mainHandler.removeCallbacks(tickRunnable);
        mainHandler.post(tickRunnable);
    }

    private void handleCancel() {
        isCompleting.set(false);
        mainHandler.removeCallbacks(tickRunnable);
        cancelExactAlarm();

        remainingMillis = totalDurationMillis;
        currentState = TimerState.READY;

        preferences.saveInactiveState(TimerState.READY, totalDurationMillis);
        updateLiveData(remainingMillis, 1.0f, TimerState.READY);

        stopForeground(true);
        stopSelf();
    }

    /**
     * Phase 4: Timer completion logic executed exactly once per timer run.
     * Stops the ongoing countdown, transitions to COMPLETED, attempts automatic media pause
     * with verification, posts completion notification, and shuts down the service.
     */
    private void completeTimer() {
        if (!isCompleting.compareAndSet(false, true)) {
            Log.d(TAG, "completeTimer: already completing/completed this session. Ignoring duplicate call.");
            return;
        }

        mainHandler.removeCallbacks(tickRunnable);
        cancelExactAlarm();

        remainingMillis = 0L;
        currentState = TimerState.COMPLETED;

        preferences.saveInactiveState(TimerState.COMPLETED, totalDurationMillis);
        updateLiveData(0L, 0.0f, TimerState.COMPLETED);

        // Remove the ongoing countdown notification from status bar
        stopForeground(true);

        Log.i(TAG, "completeTimer: Timer deadline reached. Triggering automatic media pause with verification.");

        // Automatically attempt media pause with state verification
        mediaControlManager.pauseAtTimerCompletion(result -> {
            Log.i(TAG, "completeTimer result: " + result.getDisplayMessage());

            // Persist the result and update LiveData
            preferences.saveCompletionResult(result);
            lastCompletionResultLiveData.postValue(result);

            // Post dedicated completion notification
            postCompletionNotification(result);

            // Stop service cleanly
            stopSelf();
        });
    }

    private void postCompletionNotification(@NonNull MediaControlResult result) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        Intent openAppIntent = new Intent(this, MainActivity.class);
        openAppIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int pFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent contentIntent = PendingIntent.getActivity(this, 12, openAppIntent, pFlags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID_COMPLETION)
                .setSmallIcon(R.drawable.ic_moon_headphones)
                .setContentTitle(getString(R.string.notification_completion_title))
                .setContentText(result.getDisplayMessage())
                .setStyle(new NotificationCompat.BigTextStyle().bigText(result.getDisplayMessage()))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

        try {
            nm.notify(NOTIFICATION_ID_COMPLETION, builder.build());
        } catch (Exception e) {
            Log.w(TAG, "Could not post completion notification: " + e.getMessage());
        }
    }

    private void scheduleExactAlarm(long triggerElapsedRealtime) {
        if (alarmManager == null || alarmPendingIntent == null) {
            return;
        }
        try {
            boolean canScheduleExact = true;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                canScheduleExact = alarmManager.canScheduleExactAlarms();
            }

            if (canScheduleExact) {
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
            } else {
                Log.w(TAG, "Exact alarm permission not granted. Falling back to setAndAllowWhileIdle.");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(
                            AlarmManager.ELAPSED_REALTIME_WAKEUP,
                            triggerElapsedRealtime,
                            alarmPendingIntent
                    );
                } else {
                    alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerElapsedRealtime, alarmPendingIntent);
                }
            }
        } catch (SecurityException e) {
            Log.w(TAG, "SecurityException scheduling exact alarm: " + e.getMessage() + ". Falling back to inexact idle alarm.");
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setAndAllowWhileIdle(
                            AlarmManager.ELAPSED_REALTIME_WAKEUP,
                            triggerElapsedRealtime,
                            alarmPendingIntent
                    );
                } else {
                    alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerElapsedRealtime, alarmPendingIntent);
                }
            } catch (Exception ex) {
                Log.e(TAG, "Fallback alarm scheduling also failed: " + ex.getMessage());
            }
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error scheduling alarm: " + e.getMessage());
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
        Notification notification = buildOngoingNotification(isRunning);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID_TIMER, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTIFICATION_ID_TIMER, notification);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error entering foreground mode: " + e.getMessage());
        }
    }

    private void updateForegroundNotification(boolean isRunning) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            Notification notification = buildOngoingNotification(isRunning);
            nm.notify(NOTIFICATION_ID_TIMER, notification);
        }
    }

    @NonNull
    private Notification buildOngoingNotification(boolean isRunning) {
        Intent openAppIntent = new Intent(this, MainActivity.class);
        openAppIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int pFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent contentIntent = PendingIntent.getActivity(this, 10, openAppIntent, pFlags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID_TIMER)
                .setSmallIcon(R.drawable.ic_moon_headphones)
                .setContentTitle(getString(R.string.app_name))
                .setContentIntent(contentIntent)
                .setOngoing(isRunning)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

        if (isRunning) {
            long targetEpoch = System.currentTimeMillis() + remainingMillis;
            builder.setContentText("Sleep timer running • " + TimeFormatter.formatTime(remainingMillis));
            builder.setUsesChronometer(true);
            builder.setChronometerCountDown(true);
            builder.setWhen(targetEpoch);

            // Action: Pause
            Intent pauseIntent = new Intent(this, SleepTimerService.class).setAction(ACTION_PAUSE);
            PendingIntent pausePi = PendingIntent.getService(this, 21, pauseIntent, pFlags);
            builder.addAction(R.drawable.ic_pause, getString(R.string.action_pause), pausePi);

            // Action: Cancel
            Intent cancelIntent = new Intent(this, SleepTimerService.class).setAction(ACTION_CANCEL);
            PendingIntent cancelPi = PendingIntent.getService(this, 22, cancelIntent, pFlags);
            builder.addAction(R.drawable.ic_stop, getString(R.string.action_cancel), cancelPi);
        } else {
            // Paused
            builder.setContentText("Timer paused at " + TimeFormatter.formatTime(remainingMillis));
            builder.setUsesChronometer(false);

            // Action: Resume
            Intent resumeIntent = new Intent(this, SleepTimerService.class).setAction(ACTION_RESUME);
            PendingIntent resumePi = PendingIntent.getService(this, 23, resumeIntent, pFlags);
            builder.addAction(R.drawable.ic_play_arrow, getString(R.string.action_resume), resumePi);

            // Action: Cancel
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
        if (mediaControlManager != null) {
            mediaControlManager.cleanup();
        }
    }
}
