package com.smartsleeptimer.app.media;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Encapsulates metadata and playback state of a detected external media session.
 */
public class MediaSessionInfo {

    private final String packageName;
    private final String appName;
    private final boolean isPlaying;
    private final boolean canPause;
    private final String trackTitle;
    private final String artist;
    private final String playbackStateText;

    public MediaSessionInfo(@NonNull String packageName,
                            @NonNull String appName,
                            boolean isPlaying,
                            boolean canPause,
                            @Nullable String trackTitle,
                            @Nullable String artist,
                            @NonNull String playbackStateText) {
        this.packageName = packageName;
        this.appName = appName;
        this.isPlaying = isPlaying;
        this.canPause = canPause;
        this.trackTitle = trackTitle;
        this.artist = artist;
        this.playbackStateText = playbackStateText;
    }

    @NonNull
    public String getPackageName() {
        return packageName;
    }

    @NonNull
    public String getAppName() {
        return appName;
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    public boolean canPause() {
        return canPause;
    }

    @Nullable
    public String getTrackTitle() {
        return trackTitle;
    }

    @Nullable
    public String getArtist() {
        return artist;
    }

    @NonNull
    public String getPlaybackStateText() {
        return playbackStateText;
    }

    @NonNull
    public String getDisplayTitle() {
        if (trackTitle != null && !trackTitle.isEmpty()) {
            if (artist != null && !artist.isEmpty()) {
                return trackTitle + " • " + artist;
            }
            return trackTitle;
        }
        return playbackStateText;
    }
}
