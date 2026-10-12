package com.smartsleeptimer.app.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

import androidx.annotation.NonNull;

import com.smartsleeptimer.app.model.TimerState;

/**
 * Manages persistent storage of timer deadlines and states using SharedPreferences.
 * Enables accurate recovery if the Activity or application process is killed by Android.
 */
public class TimerPreferences {

    private static final String PREFS_NAME = "smart_sleep_timer_prefs";

    private static final String KEY_STATE = "timer_state";
    private static final String KEY_TOTAL_DURATION_MS = "total_duration_ms";
    private static final String KEY_REMAINING_MS = "remaining_ms";
    private static final String KEY_END_TIME_EPOCH_MS = "end_time_epoch_ms";
    private static final String KEY_END_TIME_ELAPSED_MS = "end_time_elapsed_ms";

    private final SharedPreferences prefs;

    public TimerPreferences(@NonNull Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Persists an active running timer session with both monotonic and wall-clock deadlines.
     */
    public void saveRunningState(long totalDurationMs, long remainingMs, long endTimeElapsedRealtime) {
        long nowEpoch = System.currentTimeMillis();
        long endTimeEpoch = nowEpoch + remainingMs;

        prefs.edit()
                .putString(KEY_STATE, TimerState.RUNNING.name())
                .putLong(KEY_TOTAL_DURATION_MS, totalDurationMs)
                .putLong(KEY_REMAINING_MS, remainingMs)
                .putLong(KEY_END_TIME_ELAPSED_MS, endTimeElapsedRealtime)
                .putLong(KEY_END_TIME_EPOCH_MS, endTimeEpoch)
                .apply();
    }

    /**
     * Persists a paused timer session preserving the exact remaining duration.
     */
    public void savePausedState(long totalDurationMs, long remainingMs) {
        prefs.edit()
                .putString(KEY_STATE, TimerState.PAUSED.name())
                .putLong(KEY_TOTAL_DURATION_MS, totalDurationMs)
                .putLong(KEY_REMAINING_MS, remainingMs)
                .putLong(KEY_END_TIME_ELAPSED_MS, 0L)
                .putLong(KEY_END_TIME_EPOCH_MS, 0L)
                .apply();
    }

    /**
     * Persists ready or completed state with reset deadlines.
     */
    public void saveInactiveState(TimerState state, long selectedDurationMs) {
        prefs.edit()
                .putString(KEY_STATE, state.name())
                .putLong(KEY_TOTAL_DURATION_MS, selectedDurationMs)
                .putLong(KEY_REMAINING_MS, selectedDurationMs)
                .putLong(KEY_END_TIME_ELAPSED_MS, 0L)
                .putLong(KEY_END_TIME_EPOCH_MS, 0L)
                .apply();
    }

    public TimerState getSavedState() {
        String stateStr = prefs.getString(KEY_STATE, TimerState.READY.name());
        try {
            return TimerState.valueOf(stateStr);
        } catch (Exception e) {
            return TimerState.READY;
        }
    }

    public long getTotalDurationMs(long defaultVal) {
        return prefs.getLong(KEY_TOTAL_DURATION_MS, defaultVal);
    }

    public long getRemainingMs(long defaultVal) {
        return prefs.getLong(KEY_REMAINING_MS, defaultVal);
    }

    public long getEndTimeElapsedRealtime() {
        return prefs.getLong(KEY_END_TIME_ELAPSED_MS, 0L);
    }

    public long getEndTimeEpoch() {
        return prefs.getLong(KEY_END_TIME_EPOCH_MS, 0L);
    }

    /**
     * Computes the remaining time considering process survival across monotonic or epoch clocks.
     */
    public long calculateCurrentRemainingMs() {
        TimerState state = getSavedState();
        if (state == TimerState.PAUSED) {
            return getRemainingMs(0L);
        }
        if (state != TimerState.RUNNING) {
            return getTotalDurationMs(30 * 60 * 1000L);
        }

        // Running: check monotonic first if available and consistent
        long endElapsed = getEndTimeElapsedRealtime();
        long nowElapsed = SystemClock.elapsedRealtime();

        if (endElapsed > 0 && endElapsed >= nowElapsed) {
            return endElapsed - nowElapsed;
        }

        // Fallback to epoch (handles cases where device rebooted or process recreated after elapsed reset)
        long endEpoch = getEndTimeEpoch();
        long nowEpoch = System.currentTimeMillis();

        if (endEpoch > 0) {
            long remaining = endEpoch - nowEpoch;
            return Math.max(0L, remaining);
        }

        return 0L;
    }
}
