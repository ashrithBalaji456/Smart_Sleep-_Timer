package com.smartsleeptimer.app.media;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Result model representing the outcome of querying, manually testing,
 * or automatically pausing an active media session upon timer completion.
 */
public class MediaControlResult {

    public enum Status {
        // Phase 2 manual testing statuses
        MEDIA_ACCESS_REQUIRED,
        NO_ACTIVE_SESSION_FOUND,
        ACTIVE_SESSION_DETECTED,
        PAUSE_COMMAND_SENT,
        PAUSE_CONFIRMED,
        PAUSE_UNCONFIRMED,
        PAUSE_COMMAND_UNAVAILABLE,
        UNABLE_TO_CONTROL_SESSION,

        // Phase 4 timer completion statuses
        TIMER_COMPLETED_PAUSED_CONFIRMED,
        TIMER_COMPLETED_PAUSE_UNCONFIRMED,
        TIMER_COMPLETED_NO_SESSION,
        TIMER_COMPLETED_ACCESS_MISSING,
        TIMER_COMPLETED_PAUSE_FAILED
    }

    private final Status status;
    private final String displayMessage;
    private final String targetAppName;

    public MediaControlResult(@NonNull Status status,
                              @NonNull String displayMessage,
                              @Nullable String targetAppName) {
        this.status = status;
        this.displayMessage = displayMessage;
        this.targetAppName = targetAppName;
    }

    // Phase 2 Factory Methods
    public static MediaControlResult accessRequired() {
        return new MediaControlResult(
                Status.MEDIA_ACCESS_REQUIRED,
                "Media access required. Enable Notification Access in Settings.",
                null
        );
    }

    public static MediaControlResult noSessionFound() {
        return new MediaControlResult(
                Status.NO_ACTIVE_SESSION_FOUND,
                "No active media session found.",
                null
        );
    }

    public static MediaControlResult sessionDetected(@NonNull String appName) {
        return new MediaControlResult(
                Status.ACTIVE_SESSION_DETECTED,
                "Active session detected: " + appName,
                appName
        );
    }

    public static MediaControlResult pauseCommandSent(@NonNull String appName) {
        return new MediaControlResult(
                Status.PAUSE_COMMAND_SENT,
                "Pause command sent to " + appName + " (Playback halt depends on the media app).",
                appName
        );
    }

    public static MediaControlResult pauseConfirmed(@NonNull String appName) {
        return new MediaControlResult(
                Status.PAUSE_CONFIRMED,
                "Playback paused in " + appName + ".",
                appName
        );
    }

    public static MediaControlResult pauseUnconfirmed(@NonNull String appName) {
        String msg;
        if ("Prime Video".equalsIgnoreCase(appName)) {
            msg = "Pause command sent to Prime Video, but playback could not be verified paused. Prime Video may restrict third-party pause controls.";
        } else {
            msg = "Pause command sent to " + appName + " (unconfirmed state).";
        }
        return new MediaControlResult(
                Status.PAUSE_UNCONFIRMED,
                msg,
                appName
        );
    }

    public static MediaControlResult pauseUnavailable(@NonNull String appName) {
        String msg;
        if ("Prime Video".equalsIgnoreCase(appName)) {
            msg = "Pause command unavailable for Prime Video (Prime Video media session does not expose a remote pause action).";
        } else {
            msg = "Pause command unavailable for " + appName + ".";
        }
        return new MediaControlResult(
                Status.PAUSE_COMMAND_UNAVAILABLE,
                msg,
                appName
        );
    }

    public static MediaControlResult unableToControl(@Nullable String appName) {
        String target = appName != null ? " (" + appName + ")" : "";
        return new MediaControlResult(
                Status.UNABLE_TO_CONTROL_SESSION,
                "Unable to control this media session" + target + ".",
                appName
        );
    }

    // Phase 4 Timer Completion Factory Methods
    public static MediaControlResult timerCompletedPlaybackPaused(@NonNull String appName) {
        return new MediaControlResult(
                Status.TIMER_COMPLETED_PAUSED_CONFIRMED,
                "Sleep timer finished. Playback paused in " + appName + ".",
                appName
        );
    }

    public static MediaControlResult timerCompletedPauseUnconfirmed(@NonNull String appName) {
        String msg;
        if ("Prime Video".equalsIgnoreCase(appName)) {
            msg = "Sleep timer finished. Pause command sent to Prime Video, but playback could not be verified paused (Prime Video may restrict third-party pause controls).";
        } else {
            msg = "Sleep timer finished. Pause command sent to " + appName + " (unconfirmed state).";
        }
        return new MediaControlResult(
                Status.TIMER_COMPLETED_PAUSE_UNCONFIRMED,
                msg,
                appName
        );
    }

    public static MediaControlResult timerCompletedNoSession() {
        return new MediaControlResult(
                Status.TIMER_COMPLETED_NO_SESSION,
                "Sleep timer finished. No active media player found.",
                null
        );
    }

    public static MediaControlResult timerCompletedAccessMissing() {
        return new MediaControlResult(
                Status.TIMER_COMPLETED_ACCESS_MISSING,
                "Sleep timer finished. Media access permission is missing.",
                null
        );
    }

    public static MediaControlResult timerCompletedPauseFailed(@Nullable String appName) {
        String target = appName != null ? " in " + appName : "";
        String msg;
        if ("Prime Video".equalsIgnoreCase(appName)) {
            msg = "Sleep timer finished. Pause command unavailable or failed in Prime Video (Prime Video does not allow remote pause).";
        } else {
            msg = "Sleep timer finished. Pause command unavailable or failed" + target + ".";
        }
        return new MediaControlResult(
                Status.TIMER_COMPLETED_PAUSE_FAILED,
                msg,
                appName
        );
    }

    @NonNull
    public Status getStatus() {
        return status;
    }

    @NonNull
    public String getDisplayMessage() {
        return displayMessage;
    }

    @Nullable
    public String getTargetAppName() {
        return targetAppName;
    }
}
