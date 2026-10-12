package com.smartsleeptimer.app.viewmodel;

import android.app.Application;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.smartsleeptimer.app.model.TimerState;
import com.smartsleeptimer.app.service.SleepTimerService;
import com.smartsleeptimer.app.util.TimerPreferences;

/**
 * ViewModel coordinating between the UI and SleepTimerService.
 * Survives configuration changes and process recreations by observing
 * the service's static state and persistent TimerPreferences.
 */
public class SleepTimerViewModel extends AndroidViewModel {

    private final TimerPreferences preferences;

    private long lastActionClickTime = 0L;
    private static final long DEBOUNCE_THRESHOLD_MS = 300L;

    public SleepTimerViewModel(@NonNull Application application) {
        super(application);
        this.preferences = new TimerPreferences(application);
    }

    public LiveData<Long> getRemainingMillis() {
        return SleepTimerService.getRemainingMillisLiveData();
    }

    public LiveData<Float> getProgressFraction() {
        return SleepTimerService.getProgressFractionLiveData();
    }

    public LiveData<TimerState> getTimerState() {
        return SleepTimerService.getTimerStateLiveData();
    }

    public LiveData<Long> getSelectedDuration() {
        return SleepTimerService.getSelectedDurationLiveData();
    }

    private boolean isDebounced() {
        long now = System.currentTimeMillis();
        if (now - lastActionClickTime < DEBOUNCE_THRESHOLD_MS) {
            return true;
        }
        lastActionClickTime = now;
        return false;
    }

    public void setDuration(long durationMillis) {
        if (durationMillis <= 0) {
            return;
        }
        TimerState currentState = getTimerState().getValue();
        if (currentState == TimerState.RUNNING) {
            // Cancel existing running timer before switching duration
            cancelTimer();
        }
        preferences.saveInactiveState(TimerState.READY, durationMillis);

        Intent intent = new Intent(getApplication(), SleepTimerService.class);
        intent.setAction(SleepTimerService.ACTION_START);
        intent.putExtra(SleepTimerService.EXTRA_DURATION_MS, durationMillis);
        // Reset without running
        cancelTimer();
        preferences.saveInactiveState(TimerState.READY, durationMillis);
    }

    public boolean startTimer() {
        if (isDebounced()) {
            return false;
        }
        TimerState state = getTimerState().getValue();
        if (state == TimerState.RUNNING) {
            return false; // Prevent duplicate timers
        }

        long duration = preferences.getTotalDurationMs(30 * 60 * 1000L);
        Long currentSelected = getSelectedDuration().getValue();
        if (currentSelected != null && currentSelected > 0) {
            duration = currentSelected;
        }

        Intent intent = new Intent(getApplication(), SleepTimerService.class);
        intent.setAction(SleepTimerService.ACTION_START);
        intent.putExtra(SleepTimerService.EXTRA_DURATION_MS, duration);
        ContextCompat.startForegroundService(getApplication(), intent);
        return true;
    }

    public boolean pauseTimer() {
        if (isDebounced()) {
            return false;
        }
        TimerState state = getTimerState().getValue();
        if (state != TimerState.RUNNING) {
            return false;
        }

        Intent intent = new Intent(getApplication(), SleepTimerService.class);
        intent.setAction(SleepTimerService.ACTION_PAUSE);
        getApplication().startService(intent);
        return true;
    }

    public boolean resumeTimer() {
        if (isDebounced()) {
            return false;
        }
        TimerState state = getTimerState().getValue();
        if (state != TimerState.PAUSED) {
            return false;
        }

        Intent intent = new Intent(getApplication(), SleepTimerService.class);
        intent.setAction(SleepTimerService.ACTION_RESUME);
        ContextCompat.startForegroundService(getApplication(), intent);
        return true;
    }

    public void cancelTimer() {
        if (isDebounced()) {
            return;
        }

        Intent intent = new Intent(getApplication(), SleepTimerService.class);
        intent.setAction(SleepTimerService.ACTION_CANCEL);
        getApplication().startService(intent);
    }

    public void refreshTimer() {
        // Recalculates remaining time if process or activity was recreated
        long remaining = preferences.calculateCurrentRemainingMs();
        TimerState state = preferences.getSavedState();
        if (state == TimerState.RUNNING && remaining <= 0) {
            Intent intent = new Intent(getApplication(), SleepTimerService.class);
            intent.setAction(SleepTimerService.ACTION_EXPIRE);
            getApplication().startService(intent);
        }
    }
}
