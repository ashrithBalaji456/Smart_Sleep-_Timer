package com.smartsleeptimer.app.engine;

import com.smartsleeptimer.app.model.TimerState;

/**
 * Callback listener for timer events.
 */
public interface TimerListener {
    /**
     * Called on each clock update.
     *
     * @param remainingMillis remaining duration in milliseconds
     * @param progressPercent progress from 0 to 100
     */
    void onTick(long remainingMillis, float progressFraction);

    /**
     * Called when the timer state transitions.
     *
     * @param newState new timer state
     */
    void onStateChanged(TimerState newState);

    /**
     * Called when the countdown completes (hits 0).
     */
    void onCompleted();
}
