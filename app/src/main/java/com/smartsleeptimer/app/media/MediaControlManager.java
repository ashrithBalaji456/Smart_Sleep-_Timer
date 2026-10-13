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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages detection, manual testing, and automatic timer completion pausing
 * with capability inspection and state verification.
 * Communicates strictly with MediaSessionManager and MediaController APIs without
 * force-stopping apps or using undocumented/accessibility APIs.
 */
public class MediaControlManager {

    private static final String TAG = "MediaControlManager";
    private static final long VERIFICATION_TIMEOUT_MS = 800L;

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
                if (primary.canPause()) {
                    listener.onControlResult(MediaControlResult.sessionDetected(primary.getAppName()));
                } else {
                    listener.onControlResult(MediaControlResult.pauseUnavailable(primary.getAppName()));
                }
            } else {
                listener.onControlResult(MediaControlResult.noSessionFound());
            }
        }

        return primary;
    }

    /**
     * Manual pause test method with callback. Shares the corrected session-selection
     * and verification logic with timer expiry auto-pause.
     */
    public void pauseActiveMedia(@Nullable OnCompletionMediaPauseListener callback) {
        performPauseWithVerification(false, callback != null ? callback : result -> {
            // Completed
        });
    }

    /**
     * Backward-compatible overload for manual pause testing.
     */
    @NonNull
    public MediaControlResult pauseActiveMedia() {
        pauseActiveMedia((OnCompletionMediaPauseListener) null);
        return MediaControlResult.noSessionFound();
    }

    /**
     * Automatic Media Pause at Timer Completion.
     * Uses corrected session selection strategy, verifies capabilities, dispatches pause command,
     * and performs a callback/timed verification check to determine if playback confirmed paused.
     */
    public void pauseAtTimerCompletion(@NonNull OnCompletionMediaPauseListener callback) {
        performPauseWithVerification(true, callback);
    }

    /**
     * Core execution pipeline shared by both manual pause testing and timer completion.
     */
    private void performPauseWithVerification(
            boolean isTimerCompletion,
            @NonNull OnCompletionMediaPauseListener callback
    ) {
        if (!checkPermission()) {
            MediaControlResult result = isTimerCompletion
                    ? MediaControlResult.timerCompletedAccessMissing()
                    : MediaControlResult.accessRequired();
            if (listener != null) listener.onControlResult(result);
            callback.onCompletionResult(result);
            return;
        }

        List<MediaController> controllers = getActiveControllers();
        if (controllers.isEmpty()) {
            MediaControlResult result = isTimerCompletion
                    ? MediaControlResult.timerCompletedNoSession()
                    : MediaControlResult.noSessionFound();
            if (listener != null) listener.onControlResult(result);
            callback.onCompletionResult(result);
            return;
        }

        MediaController targetController = selectTargetController(controllers);
        if (targetController == null) {
            MediaControlResult result = isTimerCompletion
                    ? MediaControlResult.timerCompletedNoSession()
                    : MediaControlResult.noSessionFound();
            if (listener != null) listener.onControlResult(result);
            callback.onCompletionResult(result);
            return;
        }

        String appName = resolveAppName(targetController.getPackageName());
        logSessionDetails(isTimerCompletion ? "AutoPauseExpiry" : "ManualTestPause", targetController);

        PlaybackState playbackState = null;
        try {
            playbackState = targetController.getPlaybackState();
        } catch (Exception e) {
            Log.w(TAG, "Error retrieving playback state for " + appName + ": " + e.getMessage());
        }

        long actions = playbackState != null ? playbackState.getActions() : 0L;
        boolean hasPause = (actions & PlaybackState.ACTION_PAUSE) != 0;
        boolean hasPlayPause = (actions & PlaybackState.ACTION_PLAY_PAUSE) != 0;
        boolean hasStop = (actions & PlaybackState.ACTION_STOP) != 0;
        boolean supportsPause = hasPause || hasPlayPause || hasStop;

        // Check capabilities before sending. Do not treat a non-null TransportControls object as proof that pause is supported.
        if (!supportsPause) {
            Log.w(TAG, String.format(
                    "Session for %s (%s) does not advertise a supported pause action (actions=0x%x). Returning unsupported result.",
                    appName, targetController.getPackageName(), actions
            ));
            MediaControlResult result = isTimerCompletion
                    ? MediaControlResult.timerCompletedPauseFailed(appName)
                    : MediaControlResult.pauseUnavailable(appName);
            if (listener != null) listener.onControlResult(result);
            callback.onCompletionResult(result);
            return;
        }

        MediaController.TransportControls controls = null;
        try {
            controls = targetController.getTransportControls();
        } catch (Exception e) {
            Log.w(TAG, "Error getting TransportControls for " + appName + ": " + e.getMessage());
        }

        if (controls == null) {
            Log.e(TAG, "TransportControls is null for " + appName);
            MediaControlResult result = isTimerCompletion
                    ? MediaControlResult.timerCompletedPauseFailed(appName)
                    : MediaControlResult.unableToControl(appName);
            if (listener != null) listener.onControlResult(result);
            callback.onCompletionResult(result);
            return;
        }

        // Inform listener that pause command is actively being dispatched
        if (listener != null && !isTimerCompletion) {
            listener.onControlResult(MediaControlResult.pauseCommandSent(appName));
        }

        try {
            // Use Android's supported session API. Send TransportControls.pause(). Do not blindly send media key events.
            if (hasPause) {
                controls.pause();
                Log.i(TAG, "Dispatched controls.pause() to " + appName);
            } else if (hasPlayPause) {
                controls.pause();
                Log.i(TAG, "Dispatched controls.pause() (fallback from ACTION_PLAY_PAUSE) to " + appName);
            } else {
                controls.stop();
                Log.i(TAG, "Dispatched controls.stop() (fallback from ACTION_STOP) to " + appName);
            }

            // Observe target controller playback state and recheck after reasonable timeout
            verifyPauseOutcome(targetController, appName, isTimerCompletion, callback);

        } catch (Exception e) {
            Log.e(TAG, "Exception dispatching pause to " + appName + ": " + e.getMessage(), e);
            MediaControlResult result = isTimerCompletion
                    ? MediaControlResult.timerCompletedPauseFailed(appName)
                    : MediaControlResult.unableToControl(appName);
            if (listener != null) listener.onControlResult(result);
            callback.onCompletionResult(result);
        }
    }

    /**
     * Registers a MediaController.Callback to observe state changes promptly,
     * with a timeout fallback for unconfirmed states.
     */
    private void verifyPauseOutcome(
            @NonNull MediaController targetController,
            @NonNull String appName,
            boolean isTimerCompletion,
            @NonNull OnCompletionMediaPauseListener callback
    ) {
        final AtomicBoolean isResolved = new AtomicBoolean(false);
        final Runnable[] timeoutHolder = new Runnable[1];

        final MediaController.Callback controllerCallback = new MediaController.Callback() {
            @Override
            public void onPlaybackStateChanged(@Nullable PlaybackState state) {
                if (state != null && isPausedOrStopped(state.getState())) {
                    if (isResolved.compareAndSet(false, true)) {
                        mainHandler.removeCallbacks(timeoutHolder[0]);
                        safelyUnregisterCallback(targetController, this);
                        Log.i(TAG, String.format(
                                "Pause confirmed via Callback for %s: state=%s(%d)",
                                appName, formatStateCode(state.getState()), state.getState()
                        ));
                        MediaControlResult confirmed = isTimerCompletion
                                ? MediaControlResult.timerCompletedPlaybackPaused(appName)
                                : MediaControlResult.pauseConfirmed(appName);
                        if (listener != null) listener.onControlResult(confirmed);
                        callback.onCompletionResult(confirmed);
                    }
                }
            }
        };

        try {
            targetController.registerCallback(controllerCallback, mainHandler);
        } catch (Exception e) {
            Log.w(TAG, "Could not register MediaController.Callback for " + appName + ": " + e.getMessage());
        }

        timeoutHolder[0] = () -> {
            if (isResolved.compareAndSet(false, true)) {
                safelyUnregisterCallback(targetController, controllerCallback);

                PlaybackState finalState = null;
                try {
                    finalState = targetController.getPlaybackState();
                } catch (Exception e) {
                    Log.w(TAG, "Error checking final playback state for " + appName + ": " + e.getMessage());
                }

                int finalCode = finalState != null ? finalState.getState() : PlaybackState.STATE_NONE;
                Log.i(TAG, String.format(
                        "Timeout verification for %s: final state=%s(%d)",
                        appName, formatStateCode(finalCode), finalCode
                ));

                MediaControlResult result;
                if (finalState != null && isPausedOrStopped(finalCode)) {
                    Log.i(TAG, "Pause confirmed on post-check for " + appName);
                    result = isTimerCompletion
                            ? MediaControlResult.timerCompletedPlaybackPaused(appName)
                            : MediaControlResult.pauseConfirmed(appName);
                } else {
                    Log.w(TAG, "Pause unconfirmed for " + appName + ": state remained " + formatStateCode(finalCode));
                    result = isTimerCompletion
                            ? MediaControlResult.timerCompletedPauseUnconfirmed(appName)
                            : MediaControlResult.pauseUnconfirmed(appName);
                }

                if (listener != null) listener.onControlResult(result);
                callback.onCompletionResult(result);
            }
        };

        mainHandler.postDelayed(timeoutHolder[0], VERIFICATION_TIMEOUT_MS);
    }

    private void safelyUnregisterCallback(@NonNull MediaController controller, @NonNull MediaController.Callback callback) {
        try {
            controller.unregisterCallback(callback);
        } catch (Exception ignored) {
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
     * Corrected Selection Strategy when multiple media sessions exist:
     * 1. Prioritize a session that is actively PLAYING and explicitly supports ACTION_PAUSE / ACTION_PLAY_PAUSE / ACTION_STOP.
     * 2. If no playing session supports pause, check if any playing session exists.
     * 3. If no session is playing, check for a non-playing session that supports pause.
     * 4. Otherwise, fallback to the first controller in the list.
     */
    @Nullable
    public MediaController selectTargetController(@NonNull List<MediaController> controllers) {
        MediaController playingWithPause = null;
        MediaController playingWithoutPause = null;
        MediaController nonPlayingWithPause = null;

        for (MediaController controller : controllers) {
            if (controller == null) continue;
            try {
                PlaybackState state = controller.getPlaybackState();
                int stateCode = state != null ? state.getState() : PlaybackState.STATE_NONE;
                long actions = state != null ? state.getActions() : 0L;

                boolean playing = isPlayingState(stateCode);
                boolean canPause = supportsPauseAction(actions);

                if (playing && canPause) {
                    playingWithPause = controller;
                    break; // Found optimal candidate
                } else if (playing && playingWithoutPause == null) {
                    playingWithoutPause = controller;
                } else if (canPause && nonPlayingWithPause == null) {
                    nonPlayingWithPause = controller;
                }
            } catch (Exception e) {
                Log.w(TAG, "Transient error inspecting controller: " + e.getMessage());
            }
        }

        if (playingWithPause != null) return playingWithPause;
        if (playingWithoutPause != null) return playingWithoutPause;
        if (nonPlayingWithPause != null) return nonPlayingWithPause;
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
        logSessionDetails("InspectSession", target);

        PlaybackState state = null;
        try {
            state = target.getPlaybackState();
        } catch (Exception e) {
            Log.w(TAG, "Could not retrieve playback state for " + appName + ": " + e.getMessage());
        }

        boolean isPlaying = false;
        boolean canPause = false;
        long actions = 0L;
        String stateText = "Idle";

        if (state != null) {
            int playbackStateInt = state.getState();
            isPlaying = isPlayingState(playbackStateInt);
            actions = state.getActions();
            canPause = supportsPauseAction(actions);
            stateText = formatStateCode(playbackStateInt).replace("STATE_", "");
            if (!stateText.isEmpty()) {
                stateText = stateText.substring(0, 1).toUpperCase() + stateText.substring(1).toLowerCase();
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

        return new MediaSessionInfo(packageName, appName, isPlaying, canPause, actions, trackTitle, artist, stateText);
    }

    /**
     * Logs the selected session's package name, playback state, supported action flags,
     * and explicit capabilities for diagnostic traceability.
     */
    public void logSessionDetails(@NonNull String contextTag, @NonNull MediaController controller) {
        String pkg = controller.getPackageName();
        String appName = resolveAppName(pkg);
        PlaybackState state = null;
        try {
            state = controller.getPlaybackState();
        } catch (Exception e) {
            Log.w(TAG, contextTag + ": error reading playback state for " + pkg + ": " + e.getMessage());
        }

        if (state != null) {
            long actions = state.getActions();
            boolean hasPause = (actions & PlaybackState.ACTION_PAUSE) != 0;
            boolean hasPlayPause = (actions & PlaybackState.ACTION_PLAY_PAUSE) != 0;
            boolean hasStop = (actions & PlaybackState.ACTION_STOP) != 0;
            int stateCode = state.getState();
            String stateStr = formatStateCode(stateCode);
            Log.i(TAG, String.format(
                    "[%s] Target Session: App='%s', Pkg='%s', State=%s(%d), Actions=0x%x (ACTION_PAUSE=%b, ACTION_PLAY_PAUSE=%b, ACTION_STOP=%b)",
                    contextTag, appName, pkg, stateStr, stateCode, actions, hasPause, hasPlayPause, hasStop
            ));
        } else {
            Log.i(TAG, String.format("[%s] Target Session: App='%s', Pkg='%s' has null PlaybackState", contextTag, appName, pkg));
        }
    }

    @NonNull
    public String resolveAppName(@NonNull String packageName) {
        if ("com.spotify.music".equalsIgnoreCase(packageName)) return "Spotify";
        if ("com.google.android.youtube".equalsIgnoreCase(packageName)) return "YouTube";
        if ("com.google.android.apps.youtube.music".equalsIgnoreCase(packageName)) return "YouTube Music";
        if ("com.amazon.mp3".equalsIgnoreCase(packageName)) return "Amazon Music";
        if ("com.amazon.avod.thirdpartyclient".equalsIgnoreCase(packageName)
                || "com.amazon.amazonvideo.livingroom".equalsIgnoreCase(packageName)
                || "com.amazon.primevideo.anywhere".equalsIgnoreCase(packageName)) {
            return "Prime Video";
        }
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

    public static boolean supportsPauseAction(long actions) {
        return (actions & (PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_STOP)) != 0;
    }

    public static boolean isPlayingState(int stateCode) {
        return stateCode == PlaybackState.STATE_PLAYING
                || stateCode == PlaybackState.STATE_BUFFERING
                || stateCode == PlaybackState.STATE_CONNECTING;
    }

    public static boolean isPausedOrStopped(int stateCode) {
        return stateCode == PlaybackState.STATE_PAUSED
                || stateCode == PlaybackState.STATE_STOPPED
                || stateCode == PlaybackState.STATE_NONE;
    }

    public static String formatStateCode(int stateCode) {
        switch (stateCode) {
            case PlaybackState.STATE_PLAYING: return "STATE_PLAYING";
            case PlaybackState.STATE_PAUSED: return "STATE_PAUSED";
            case PlaybackState.STATE_STOPPED: return "STATE_STOPPED";
            case PlaybackState.STATE_BUFFERING: return "STATE_BUFFERING";
            case PlaybackState.STATE_CONNECTING: return "STATE_CONNECTING";
            case PlaybackState.STATE_FAST_FORWARDING: return "STATE_FAST_FORWARDING";
            case PlaybackState.STATE_REWINDING: return "STATE_REWINDING";
            case PlaybackState.STATE_SKIPPING_TO_NEXT: return "STATE_SKIPPING_TO_NEXT";
            case PlaybackState.STATE_SKIPPING_TO_PREVIOUS: return "STATE_SKIPPING_TO_PREVIOUS";
            case PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM: return "STATE_SKIPPING_TO_QUEUE_ITEM";
            case PlaybackState.STATE_ERROR: return "STATE_ERROR";
            case PlaybackState.STATE_NONE: return "STATE_NONE";
            default: return "STATE_UNKNOWN(" + stateCode + ")";
        }
    }

    public void cleanup() {
        unregisterSessionListener();
        listener = null;
    }
}
