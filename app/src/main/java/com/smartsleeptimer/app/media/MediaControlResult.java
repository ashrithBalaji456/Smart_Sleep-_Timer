package com.smartsleeptimer.app.media;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Result model representing the outcome of querying or attempting to pause an active media session.
 */
public class MediaControlResult {

    public enum Status {
        MEDIA_ACCESS_REQUIRED,
        NO_ACTIVE_SESSION_FOUND,
        ACTIVE_SESSION_DETECTED,
        PAUSE_COMMAND_SENT,
        PAUSE_COMMAND_UNAVAILABLE,
        UNABLE_TO_CONTROL_SESSION
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

    public static MediaControlResult pauseUnavailable(@NonNull String appName) {
        return new MediaControlResult(
                Status.PAUSE_COMMAND_UNAVAILABLE,
                "Pause command unavailable for " + appName + ".",
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
