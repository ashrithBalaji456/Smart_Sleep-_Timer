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
 * Manages detection, manual testing (Phase 2), and automatic timer completion pausing
 * with state verification (Phase 4).
 * Communicates with MediaSessionManager and MediaController APIs without force-stopping
 * or modifying external apps.
 */
public class MediaControlManager {

    private static final String TAG = "MediaControlManager";
    private static final long VERIFICATION_TIMEOUT_MS = 600L;

    public interface MediaControlListener {
        void onSessionDetected(@Nullable MediaSessionInfo sessionInfo);
        void onControlResult(@NonNull MediaControlResult result);
        void onPermissionStatusChanged(boolean isGranted);
    }

    public interface OnCompletionMediaPauseListener {
        void onCompletionResult(@NonNull MediaControlResult result);
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
     * Manual Phase 2 pause test method.
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

    /**
     * Phase 4: Automatic Media Pause at Timer Completion.
     * Uses documented session selection strategy, dispatches pause command,
     * and performs a timed verification check to determine if playback confirmed paused.
     */
    public void pauseAtTimerCompletion(@NonNull OnCompletionMediaPauseListener callback) {
        if (!checkPermission()) {
            callback.onCompletionResult(MediaControlResult.timerCompletedAccessMissing());
            return;
        }

        List<MediaController> controllers = getActiveControllers();
        if (controllers.isEmpty()) {
            callback.onCompletionResult(MediaControlResult.timerCompletedNoSession());
            return;
        }

        MediaController targetController = selectTargetController(controllers);
        if (targetController == null) {
            callback.onCompletionResult(MediaControlResult.timerCompletedNoSession());
            return;
        }

        String appName = resolveAppName(targetController.getPackageName());
        PlaybackState playbackState = targetController.getPlaybackState();

        long actions = playbackState != null ? playbackState.getActions() : 0L;
        boolean supportsPause = (actions & (PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_STOP)) != 0;

        try {
            MediaController.TransportControls controls = targetController.getTransportControls();
            if (controls != null) {
                controls.pause();
                Log.i(TAG, "Auto-pause: sent transportControls.pause() to " + appName);
            } else {
                dispatchMediaKeyEvent(targetController, KeyEvent.KEYCODE_MEDIA_PAUSE);
                Log.i(TAG, "Auto-pause: dispatched media key event to " + appName);
            }

            // Schedule a post-check verification with 600ms timeout
            mainHandler.postDelayed(() -> {
                try {
                    PlaybackState newState = targetController.getPlaybackState();
                    if (newState != null) {
                        int stateCode = newState.getState();
                        if (stateCode == PlaybackState.STATE_PAUSED ||
                                stateCode == PlaybackState.STATE_STOPPED ||
                                stateCode == PlaybackState.STATE_NONE) {
                            Log.i(TAG, "Auto-pause confirmed: playback is paused/stopped in " + appName);
                            callback.onCompletionResult(MediaControlResult.timerCompletedPlaybackPaused(appName));
                            return;
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Error checking verification state for " + appName + ": " + e.getMessage());
                }

                // If state remains PLAYING or is unconfirmed
                Log.i(TAG, "Auto-pause unconfirmed: command sent but final state not verified in " + appName);
                callback.onCompletionResult(MediaControlResult.timerCompletedPauseUnconfirmed(appName));
            }, VERIFICATION_TIMEOUT_MS);

        } catch (Exception e) {
            Log.e(TAG, "Exception during auto-pause for " + appName + ": " + e.getMessage());
            callback.onCompletionResult(MediaControlResult.timerCompletedPauseFailed(appName));
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

    /**
     * Documented Selection Strategy when multiple media sessions exist:
     * 1. Prioritize sessions actively in STATE_PLAYING, STATE_BUFFERING, or STATE_CONNECTING.
     * 2. Among playing sessions, prioritize one that explicitly advertises ACTION_PAUSE or ACTION_PLAY_PAUSE.
     * 3. If no session is actively playing, select the first session advertising ACTION_PAUSE that was
     *    recently paused (avoiding random inactive apps).
     * 4. If none match, fallback to the primary active session in the system list.
     */
    @Nullable
    private MediaController selectTargetController(@NonNull List<MediaController> controllers) {
        MediaController firstPlaying = null;
        MediaController firstWithPauseAction = null;

        for (MediaController controller : controllers) {
            if (controller == null) continue;
            try {
                PlaybackState state = controller.getPlaybackState();
                if (state != null) {
                    int stateCode = state.getState();
                    boolean isPlaying = (stateCode == PlaybackState.STATE_PLAYING ||
                            stateCode == PlaybackState.STATE_BUFFERING ||
                            stateCode == PlaybackState.STATE_CONNECTING);

                    if (isPlaying) {
                        firstPlaying = controller;
                        break;
                    }
                    long actions = state.getActions();
                    if ((actions & PlaybackState.ACTION_PAUSE) != 0 && firstWithPauseAction == null) {
                        firstWithPauseAction = controller;
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Transient error reading media controller state: " + e.getMessage());
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
        PlaybackState state = null;
        try {
            state = target.getPlaybackState();
        } catch (Exception e) {
            Log.w(TAG, "Could not retrieve playback state for " + appName + ": " + e.getMessage());
        }

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

        MediaMetadata metadata = null;
        try {
            metadata = target.getMetadata();
        } catch (Exception e) {
            Log.w(TAG, "Could not retrieve metadata for " + appName + ": " + e.getMessage());
        }

        String trackTitle = null;
        String artist = null;

        if (metadata != null) {
            try {
                trackTitle = metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
                artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
                if (artist == null) {
                    artist = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
                }
            } catch (Exception ignored) {
            }
        }

        return new MediaSessionInfo(packageName, appName, isPlaying, canPause, trackTitle, artist, stateText);
    }

    @NonNull
    public String resolveAppName(@NonNull String packageName) {
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
