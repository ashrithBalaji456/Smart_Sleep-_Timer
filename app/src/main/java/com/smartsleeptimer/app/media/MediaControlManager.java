package com.smartsleeptimer.app.media;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages detection and interaction with active external media sessions.
 * Interacts with MediaSessionManager and MediaController APIs without force-stopping or
 * modifying external apps.
 */
public class MediaControlManager {

    private static final String TAG = "MediaControlManager";

    public interface MediaControlListener {
        void onSessionDetected(@Nullable MediaSessionInfo sessionInfo);
        void onControlResult(@NonNull MediaControlResult result);
        void onPermissionStatusChanged(boolean isGranted);
    }

    private final Context appContext;
    private final Handler mainHandler;
    private final MediaSessionManager mediaSessionManager;
    private final ComponentName listenerComponent;
    private MediaControlListener listener;

    private boolean isListenerRegistered = false;

    private final MediaSessionManager.OnActiveSessionsChangedListener activeSessionsListener =
            new MediaSessionManager.OnActiveSessionsChangedListener() {
                @Override
                public void onActiveSessionsChanged(@Nullable List<MediaController> controllers) {
                    mainHandler.post(() -> {
                        MediaSessionInfo sessionInfo = findPrimarySession(controllers);
                        if (listener != null) {
                            listener.onSessionDetected(sessionInfo);
                        }
                    });
                }
            };

    public MediaControlManager(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.mediaSessionManager = (MediaSessionManager) appContext.getSystemService(Context.MEDIA_SESSION_SERVICE);
        this.listenerComponent = MediaAccessHelper.getNotificationListenerComponentName(appContext);
    }

    public void setListener(MediaControlListener listener) {
        this.listener = listener;
    }

    /**
     * Checks if notification access is granted and informs listener.
     */
    public boolean checkPermission() {
        boolean granted = MediaAccessHelper.isNotificationListenerAccessGranted(appContext);
        if (listener != null) {
            listener.onPermissionStatusChanged(granted);
        }
        return granted;
    }

    /**
     * Registers system listener for active media session changes.
     */
    public synchronized void registerSessionListener() {
        if (isListenerRegistered) {
            return;
        }

        if (!MediaAccessHelper.isNotificationListenerAccessGranted(appContext)) {
            Log.d(TAG, "Notification listener access not granted. Skipping session listener registration.");
            return;
        }

        try {
            if (mediaSessionManager != null) {
                mediaSessionManager.addOnActiveSessionsChangedListener(activeSessionsListener, listenerComponent);
                isListenerRegistered = true;
                Log.d(TAG, "Active sessions changed listener registered.");
            }
        } catch (SecurityException e) {
            Log.w(TAG, "SecurityException registering active sessions listener: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Error registering active sessions listener: " + e.getMessage());
        }
    }

    /**
     * Unregisters the session listener to prevent memory leaks and duplicate callbacks.
     */
    public synchronized void unregisterSessionListener() {
        if (!isListenerRegistered) {
            return;
        }

        try {
            if (mediaSessionManager != null) {
                mediaSessionManager.removeOnActiveSessionsChangedListener(activeSessionsListener);
            }
        } catch (Exception e) {
            Log.w(TAG, "Error unregistering active sessions listener: " + e.getMessage());
        } finally {
            isListenerRegistered = false;
        }
    }

    /**
     * Queries active media sessions and returns the primary candidate session info.
     */
    @Nullable
    public MediaSessionInfo refreshActiveSessions() {
        if (!checkPermission()) {
            if (listener != null) {
                listener.onControlResult(MediaControlResult.accessRequired());
                listener.onSessionDetected(null);
            }
            return null;
        }

        List<MediaController> controllers = getActiveControllers();
        MediaSessionInfo primary = findPrimarySession(controllers);

        if (listener != null) {
            listener.onSessionDetected(primary);
            if (primary != null) {
                listener.onControlResult(MediaControlResult.sessionDetected(primary.getAppName()));
            } else {
                listener.onControlResult(MediaControlResult.noSessionFound());
            }
        }

        return primary;
    }

    /**
     * Attempts to send a pause command to the active media session.
     */
    @NonNull
    public MediaControlResult pauseActiveMedia() {
        if (!checkPermission()) {
            MediaControlResult result = MediaControlResult.accessRequired();
            if (listener != null) listener.onControlResult(result);
            return result;
        }

        List<MediaController> controllers = getActiveControllers();
        if (controllers.isEmpty()) {
            MediaControlResult result = MediaControlResult.noSessionFound();
            if (listener != null) listener.onControlResult(result);
            return result;
        }

        MediaController targetController = selectTargetController(controllers);
        if (targetController == null) {
            MediaControlResult result = MediaControlResult.noSessionFound();
            if (listener != null) listener.onControlResult(result);
            return result;
        }

        String appName = resolveAppName(targetController.getPackageName());
        PlaybackState playbackState = targetController.getPlaybackState();

        long actions = playbackState != null ? playbackState.getActions() : 0L;
        boolean supportsPause = (actions & (PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_STOP)) != 0;

        try {
            MediaController.TransportControls controls = targetController.getTransportControls();
            if (controls != null) {
                controls.pause();
                Log.i(TAG, "Sent transportControls.pause() to " + targetController.getPackageName());
            } else {
                // Secondary fallback via Media Button KeyEvents
                dispatchMediaKeyEvent(targetController, KeyEvent.KEYCODE_MEDIA_PAUSE);
            }

            MediaControlResult result = MediaControlResult.pauseCommandSent(appName);
            if (listener != null) listener.onControlResult(result);
            return result;
        } catch (Exception e) {
            Log.e(TAG, "Exception attempting to pause media session for " + appName + ": " + e.getMessage());
            MediaControlResult result = supportsPause
                    ? MediaControlResult.unableToControl(appName)
                    : MediaControlResult.pauseUnavailable(appName);
            if (listener != null) listener.onControlResult(result);
            return result;
        }
    }

    private void dispatchMediaKeyEvent(@NonNull MediaController controller, int keyCode) {
        try {
            long now = android.os.SystemClock.uptimeMillis();
            controller.dispatchMediaButtonEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0));
            controller.dispatchMediaButtonEvent(new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0));
        } catch (Exception e) {
            Log.w(TAG, "Error dispatching media key event: " + e.getMessage());
        }
    }

    @NonNull
    private List<MediaController> getActiveControllers() {
        List<MediaController> controllers = new ArrayList<>();
        if (mediaSessionManager == null) {
            return controllers;
        }

        try {
            List<MediaController> result = mediaSessionManager.getActiveSessions(listenerComponent);
            if (result != null) {
                controllers.addAll(result);
            }
        } catch (SecurityException e) {
            Log.w(TAG, "SecurityException retrieving active media sessions: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Error retrieving active media sessions: " + e.getMessage());
        }
        return controllers;
    }

    @Nullable
    private MediaController selectTargetController(@NonNull List<MediaController> controllers) {
        MediaController firstPlaying = null;
        MediaController firstWithPauseAction = null;

        for (MediaController controller : controllers) {
            PlaybackState state = controller.getPlaybackState();
            if (state != null) {
                if (state.getState() == PlaybackState.STATE_PLAYING) {
                    firstPlaying = controller;
                    break;
                }
                long actions = state.getActions();
                if ((actions & PlaybackState.ACTION_PAUSE) != 0 && firstWithPauseAction == null) {
                    firstWithPauseAction = controller;
                }
            }
        }

        if (firstPlaying != null) return firstPlaying;
        if (firstWithPauseAction != null) return firstWithPauseAction;
        return controllers.isEmpty() ? null : controllers.get(0);
    }

    @Nullable
    private MediaSessionInfo findPrimarySession(@Nullable List<MediaController> controllers) {
        if (controllers == null || controllers.isEmpty()) {
            return null;
        }

        MediaController target = selectTargetController(controllers);
        if (target == null) {
            return null;
        }

        String packageName = target.getPackageName();
        String appName = resolveAppName(packageName);
        PlaybackState state = target.getPlaybackState();

        boolean isPlaying = false;
        boolean canPause = false;
        String stateText = "Idle";

        if (state != null) {
            int playbackStateInt = state.getState();
            isPlaying = (playbackStateInt == PlaybackState.STATE_PLAYING ||
                    playbackStateInt == PlaybackState.STATE_BUFFERING ||
                    playbackStateInt == PlaybackState.STATE_CONNECTING);

            long actions = state.getActions();
            canPause = (actions & (PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_STOP)) != 0;

            switch (playbackStateInt) {
                case PlaybackState.STATE_PLAYING:
                    stateText = "Playing";
                    break;
                case PlaybackState.STATE_PAUSED:
                    stateText = "Paused";
                    break;
                case PlaybackState.STATE_BUFFERING:
                    stateText = "Buffering";
                    break;
                case PlaybackState.STATE_STOPPED:
                    stateText = "Stopped";
                    break;
                default:
                    stateText = isPlaying ? "Playing" : "Inactive";
                    break;
            }
        }

        MediaMetadata metadata = target.getMetadata();
        String trackTitle = null;
        String artist = null;

        if (metadata != null) {
            trackTitle = metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
            artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
            if (artist == null) {
                artist = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
            }
        }

        return new MediaSessionInfo(packageName, appName, isPlaying, canPause, trackTitle, artist, stateText);
    }

    @NonNull
    public String resolveAppName(@NonNull String packageName) {
        // Direct known friendly names
        if ("com.spotify.music".equalsIgnoreCase(packageName)) return "Spotify";
        if ("com.google.android.youtube".equalsIgnoreCase(packageName)) return "YouTube";
        if ("com.google.android.apps.youtube.music".equalsIgnoreCase(packageName)) return "YouTube Music";
        if ("com.amazon.mp3".equalsIgnoreCase(packageName)) return "Amazon Music";
        if ("com.apple.android.music".equalsIgnoreCase(packageName)) return "Apple Music";
        if ("com.soundcloud.android".equalsIgnoreCase(packageName)) return "SoundCloud";

        try {
            PackageManager pm = appContext.getPackageManager();
            ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
            CharSequence label = pm.getApplicationLabel(appInfo);
            if (label != null) {
                return label.toString();
            }
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        return packageName;
    }

    public void cleanup() {
        unregisterSessionListener();
        listener = null;
    }
}
