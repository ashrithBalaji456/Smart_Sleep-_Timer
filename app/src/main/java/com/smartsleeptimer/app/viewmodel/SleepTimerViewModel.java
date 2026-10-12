package com.smartsleeptimer.app.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.smartsleeptimer.app.engine.SleepTimerEngine;
import com.smartsleeptimer.app.engine.TimerListener;
import com.smartsleeptimer.app.model.TimerState;

/**
 * ViewModel that hosts the SleepTimerEngine and exposes LiveData streams to the UI.
 * Survives configuration changes such as device rotation and activity recreation.
 */
public class SleepTimerViewModel extends ViewModel implements TimerListener {

    private final SleepTimerEngine timerEngine;

    private final MutableLiveData<Long> remainingMillisLiveData = new MutableLiveData<>();
    private final MutableLiveData<Float> progressFractionLiveData = new MutableLiveData<>();
    private final MutableLiveData<TimerState> timerStateLiveData = new MutableLiveData<>();
    private final MutableLiveData<Long> selectedDurationLiveData = new MutableLiveData<>();

    private long lastActionClickTime = 0L;
    private static final long DEBOUNCE_THRESHOLD_MS = 300L;

    public SleepTimerViewModel() {
        this.timerEngine = new SleepTimerEngine();
        this.timerEngine.setListener(this);

        // Initialize state
        remainingMillisLiveData.setValue(timerEngine.getRemainingMillis());
        progressFractionLiveData.setValue(1.0f);
        timerStateLiveData.setValue(timerEngine.getState());
        selectedDurationLiveData.setValue(timerEngine.getSelectedDurationMillis());
    }

    public LiveData<Long> getRemainingMillis() {
        return remainingMillisLiveData;
    }

    public LiveData<Float> getProgressFraction() {
        return progressFractionLiveData;
    }

    public LiveData<TimerState> getTimerState() {
        return timerStateLiveData;
    }

    public LiveData<Long> getSelectedDuration() {
        return selectedDurationLiveData;
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
        timerEngine.setDuration(durationMillis);
        selectedDurationLiveData.setValue(durationMillis);
    }

    public boolean startTimer() {
        if (isDebounced()) {
            return false;
        }
        return timerEngine.start();
    }

    public boolean pauseTimer() {
        if (isDebounced()) {
            return false;
        }
        return timerEngine.pause();
    }

    public boolean resumeTimer() {
        if (isDebounced()) {
            return false;
        }
        return timerEngine.resume();
    }

    public void cancelTimer() {
        if (isDebounced()) {
            return;
        }
        timerEngine.cancel();
    }

    public void refreshTimer() {
        timerEngine.refreshTick();
    }

    @Override
    public void onTick(long remainingMillis, float progressFraction) {
        remainingMillisLiveData.postValue(remainingMillis);
        progressFractionLiveData.postValue(progressFraction);
    }

    @Override
    public void onStateChanged(TimerState newState) {
        timerStateLiveData.postValue(newState);
    }

    @Override
    public void onCompleted() {
        remainingMillisLiveData.postValue(0L);
        progressFractionLiveData.postValue(0.0f);
        timerStateLiveData.postValue(TimerState.COMPLETED);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        timerEngine.cleanup();
    }
}
