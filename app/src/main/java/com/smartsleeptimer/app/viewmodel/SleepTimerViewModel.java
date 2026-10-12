package com.smartsleeptimer.app.viewmodel;

import android.app.Application;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.smartsleeptimer.app.media.MediaControlResult;
import com.smartsleeptimer.app.model.TimerState;
import com.smartsleeptimer.app.service.SleepTimerService;
import com.smartsleeptimer.app.util.TimerPreferences;

/**
 * ViewModel coordinating between the UI, SleepTimerService, and TimerPreferences.
 * Exposes live countdown, timer states, and Phase 4 completion results.
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

    public LiveData<MediaControlResult> getLastCompletionResult() {
        return SleepTimerService.getLastCompletionResultLiveData();
    }

    public MediaControlResult getPersistedCompletionResult() {
        return preferences.getLastCompletionResult();
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
        if (currentState == TimerState.RUNNING || currentState == TimerState.PAUSED) {
            cancelTimer();
        }
        preferences.saveInactiveState(TimerState.READY, durationMillis);
        SleepTimerService.updateIdleDuration(durationMillis);
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
        try {
            ContextCompat.startForegroundService(getApplication(), intent);
            return true;
        } catch (Exception e) {
            android.util.Log.e("SleepTimerViewModel", "Could not start timer service: " + e.getMessage(), e);
            return false;
        }
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
        try {
            getApplication().startService(intent);
            return true;
        } catch (Exception e) {
            android.util.Log.e("SleepTimerViewModel", "Could not pause timer service: " + e.getMessage(), e);
            return false;
        }
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
        try {
            ContextCompat.startForegroundService(getApplication(), intent);
            return true;
        } catch (Exception e) {
            android.util.Log.e("SleepTimerViewModel", "Could not resume timer service: " + e.getMessage(), e);
            return false;
        }
    }

    public void cancelTimer() {
        if (isDebounced()) {
            return;
        }

        Intent intent = new Intent(getApplication(), SleepTimerService.class);
        intent.setAction(SleepTimerService.ACTION_CANCEL);
        try {
            getApplication().startService(intent);
        } catch (Exception e) {
            android.util.Log.e("SleepTimerViewModel", "Could not cancel timer service: " + e.getMessage(), e);
        }
    }

    public void refreshTimer() {
        // Recalculates remaining time if process or activity was recreated
        long remaining = preferences.calculateCurrentRemainingMs();
        TimerState state = preferences.getSavedState();
        if (state == TimerState.RUNNING && remaining <= 0) {
            Intent intent = new Intent(getApplication(), SleepTimerService.class);
            intent.setAction(SleepTimerService.ACTION_EXPIRE);
            try {
                getApplication().startService(intent);
            } catch (Exception e) {
                android.util.Log.e("SleepTimerViewModel", "Could not trigger expired timer: " + e.getMessage(), e);
            }
        }
    }
}
