package com.smartsleeptimer.app.engine;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.NonNull;

import com.smartsleeptimer.app.model.TimerState;

/**
 * Core countdown timer engine.
 * Uses SystemClock.elapsedRealtime() for monotonic, drift-free time calculations.
 */
public class SleepTimerEngine {

    public interface TimeProvider {
        long getElapsedRealtime();
    }

    private static final long TICK_INTERVAL_MS = 250L;

    private final Handler mainHandler;
    private final TimeProvider timeProvider;
    private TimerListener listener;

    private long selectedDurationMillis = 30 * 60 * 1000L; // default 30 min
    private long totalDurationMillis = 30 * 60 * 1000L;
    private long remainingMillis = 30 * 60 * 1000L;
    private long endTimeRealtime = 0L;

    private TimerState state = TimerState.READY;

    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            synchronized (SleepTimerEngine.this) {
                if (state != TimerState.RUNNING) {
                    return;
                }

                long now = timeProvider.getElapsedRealtime();
                long remaining = endTimeRealtime - now;

                if (remaining <= 0) {
                    remainingMillis = 0L;
                    state = TimerState.COMPLETED;
                    dispatchTick(0L, 0.0f);
                    dispatchCompleted();
                    dispatchStateChanged(TimerState.COMPLETED);
                } else {
                    remainingMillis = remaining;
                    float progressFraction = (totalDurationMillis > 0)
                            ? Math.max(0f, Math.min(1.0f, (float) remainingMillis / (float) totalDurationMillis))
                            : 0f;
                    dispatchTick(remainingMillis, progressFraction);
                    if (mainHandler != null) {
                        mainHandler.postDelayed(this, TICK_INTERVAL_MS);
                    }
                }
            }
        }
    };

    public SleepTimerEngine() {
        this(createDefaultHandler(), createDefaultTimeProvider());
    }

    public SleepTimerEngine(Handler handler, TimeProvider timeProvider) {
        this.mainHandler = handler;
        this.timeProvider = timeProvider != null ? timeProvider : createDefaultTimeProvider();
    }

    private static Handler createDefaultHandler() {
        try {
            Looper looper = Looper.getMainLooper();
            if (looper != null) {
                return new Handler(looper);
            }
        } catch (Throwable ignored) {
            // Stubs in standard JVM test
        }
        return null;
    }

    private static TimeProvider createDefaultTimeProvider() {
        return () -> {
            try {
                return SystemClock.elapsedRealtime();
            } catch (Throwable t) {
                return System.currentTimeMillis();
            }
        };
    }

    public synchronized void setListener(TimerListener listener) {
        this.listener = listener;
    }

    public synchronized void setDuration(long durationMillis) {
        if (durationMillis <= 0) {
            throw new IllegalArgumentException("Duration must be strictly positive");
        }

        stopTicker();
        this.selectedDurationMillis = durationMillis;
        this.totalDurationMillis = durationMillis;
        this.remainingMillis = durationMillis;
        this.state = TimerState.READY;

        dispatchStateChanged(TimerState.READY);
        dispatchTick(remainingMillis, 1.0f);
    }

    /**
     * Starts or restarts the timer.
     * Prevents multiple concurrent countdowns.
     */
    public synchronized boolean start() {
        if (state == TimerState.RUNNING) {
            return false; // Prevent duplicate starts
        }

        if (state == TimerState.READY || state == TimerState.COMPLETED) {
            this.remainingMillis = selectedDurationMillis;
            this.totalDurationMillis = selectedDurationMillis;
        }

        this.endTimeRealtime = timeProvider.getElapsedRealtime() + remainingMillis;
        this.state = TimerState.RUNNING;

        dispatchStateChanged(TimerState.RUNNING);
        dispatchTick(remainingMillis, getProgressFraction());

        stopTicker();
        if (mainHandler != null) {
            mainHandler.post(tickRunnable);
        }
        return true;
    }

    /**
     * Pauses the timer and preserves remaining countdown time.
     */
    public synchronized boolean pause() {
        if (state != TimerState.RUNNING) {
            return false;
        }

        stopTicker();
        long now = timeProvider.getElapsedRealtime();
        this.remainingMillis = Math.max(0L, endTimeRealtime - now);
        this.state = TimerState.PAUSED;

        dispatchStateChanged(TimerState.PAUSED);
        dispatchTick(remainingMillis, getProgressFraction());
        return true;
    }

    /**
     * Resumes the timer from the paused duration.
     */
    public synchronized boolean resume() {
        if (state != TimerState.PAUSED) {
            return false;
        }

        if (remainingMillis <= 0) {
            this.state = TimerState.COMPLETED;
            dispatchStateChanged(TimerState.COMPLETED);
            dispatchTick(0L, 0.0f);
            dispatchCompleted();
            return false;
        }

        this.endTimeRealtime = timeProvider.getElapsedRealtime() + remainingMillis;
        this.state = TimerState.RUNNING;

        dispatchStateChanged(TimerState.RUNNING);
        dispatchTick(remainingMillis, getProgressFraction());

        stopTicker();
        if (mainHandler != null) {
            mainHandler.post(tickRunnable);
        }
        return true;
    }

    /**
     * Cancels the active timer and resets back to the configured duration.
     */
    public synchronized void cancel() {
        stopTicker();
        this.remainingMillis = selectedDurationMillis;
        this.totalDurationMillis = selectedDurationMillis;
        this.state = TimerState.READY;

        dispatchStateChanged(TimerState.READY);
        dispatchTick(remainingMillis, 1.0f);
    }

    /**
     * Refreshes the current countdown calculation, useful after activity rotation or app resume.
     */
    public synchronized void refreshTick() {
        if (state == TimerState.RUNNING) {
            long now = timeProvider.getElapsedRealtime();
            long remaining = endTimeRealtime - now;
            if (remaining <= 0) {
                remainingMillis = 0L;
                state = TimerState.COMPLETED;
                stopTicker();
                dispatchTick(0L, 0.0f);
                dispatchCompleted();
                dispatchStateChanged(TimerState.COMPLETED);
            } else {
                remainingMillis = remaining;
                dispatchTick(remainingMillis, getProgressFraction());
            }
        } else {
            dispatchTick(remainingMillis, getProgressFraction());
            dispatchStateChanged(state);
        }
    }

    private void stopTicker() {
        if (mainHandler != null) {
            mainHandler.removeCallbacks(tickRunnable);
        }
    }

    private float getProgressFraction() {
        if (totalDurationMillis <= 0) {
            return 0f;
        }
        return Math.max(0f, Math.min(1.0f, (float) remainingMillis / (float) totalDurationMillis));
    }

    private void dispatchTick(long remaining, float progressFraction) {
        if (listener != null) {
            listener.onTick(remaining, progressFraction);
        }
    }

    private void dispatchStateChanged(TimerState newState) {
        if (listener != null) {
            listener.onStateChanged(newState);
        }
    }

    private void dispatchCompleted() {
        if (listener != null) {
            listener.onCompleted();
        }
    }

    public synchronized TimerState getState() {
        return state;
    }

    public synchronized long getRemainingMillis() {
        return remainingMillis;
    }

    public synchronized long getTotalDurationMillis() {
        return totalDurationMillis;
    }

    public synchronized long getSelectedDurationMillis() {
        return selectedDurationMillis;
    }

    public synchronized void cleanup() {
        stopTicker();
        listener = null;
    }
}
