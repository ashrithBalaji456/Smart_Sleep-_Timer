package com.smartsleeptimer.app.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.smartsleeptimer.app.media.MediaControlManager;
import com.smartsleeptimer.app.media.MediaControlResult;
import com.smartsleeptimer.app.media.MediaSessionInfo;

/**
 * ViewModel managing media playback control state and session inspection.
 * Decoupled from the timer engine to uphold clean separation of concerns.
 */
public class MediaControlViewModel extends AndroidViewModel implements MediaControlManager.MediaControlListener {

    private final MediaControlManager mediaControlManager;

    private final MutableLiveData<Boolean> isPermissionGrantedLiveData = new MutableLiveData<>();
    private final MutableLiveData<MediaSessionInfo> detectedSessionLiveData = new MutableLiveData<>();
    private final MutableLiveData<MediaControlResult> controlResultLiveData = new MutableLiveData<>();

    public MediaControlViewModel(@NonNull Application application) {
        super(application);
        this.mediaControlManager = new MediaControlManager(application);
        this.mediaControlManager.setListener(this);

        boolean granted = mediaControlManager.checkPermission();
        isPermissionGrantedLiveData.setValue(granted);
        if (!granted) {
            controlResultLiveData.setValue(MediaControlResult.accessRequired());
        }
    }

    public LiveData<Boolean> isPermissionGranted() {
        return isPermissionGrantedLiveData;
    }

    public LiveData<MediaSessionInfo> getDetectedSession() {
        return detectedSessionLiveData;
    }

    public LiveData<MediaControlResult> getControlResult() {
        return controlResultLiveData;
    }

    public void checkAndRefresh() {
        boolean granted = mediaControlManager.checkPermission();
        isPermissionGrantedLiveData.setValue(granted);
        if (granted) {
            mediaControlManager.registerSessionListener();
            mediaControlManager.refreshActiveSessions();
        } else {
            mediaControlManager.unregisterSessionListener();
            detectedSessionLiveData.setValue(null);
            controlResultLiveData.setValue(MediaControlResult.accessRequired());
        }
    }

    public void onStart() {
        mediaControlManager.registerSessionListener();
        if (mediaControlManager.checkPermission()) {
            mediaControlManager.refreshActiveSessions();
        }
    }

    public void onStop() {
        mediaControlManager.unregisterSessionListener();
    }

    public void refreshSessions() {
        mediaControlManager.refreshActiveSessions();
    }

    public void pauseActiveMedia() {
        mediaControlManager.pauseActiveMedia();
    }

    @Override
    public void onSessionDetected(@Nullable MediaSessionInfo sessionInfo) {
        detectedSessionLiveData.postValue(sessionInfo);
    }

    @Override
    public void onControlResult(@NonNull MediaControlResult result) {
        controlResultLiveData.postValue(result);
    }

    @Override
    public void onPermissionStatusChanged(boolean isGranted) {
        isPermissionGrantedLiveData.postValue(isGranted);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        mediaControlManager.cleanup();
    }
}
