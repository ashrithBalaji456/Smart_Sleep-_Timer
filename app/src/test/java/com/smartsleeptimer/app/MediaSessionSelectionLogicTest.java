package com.smartsleeptimer.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.media.session.PlaybackState;

import com.smartsleeptimer.app.media.MediaControlManager;
import com.smartsleeptimer.app.media.MediaSessionInfo;

import org.junit.Test;

/**
 * Unit tests validating session capability inspection, pause action support,
 * playback state classification, and multi-session prioritization logic.
 */
public class MediaSessionSelectionLogicTest {

    @Test
    public void testSupportsPauseActionWithActionPause() {
        long actions = PlaybackState.ACTION_PAUSE; // 2L
        assertTrue(MediaControlManager.supportsPauseAction(actions));
    }

    @Test
    public void testSupportsPauseActionWithActionPlayPause() {
        long actions = PlaybackState.ACTION_PLAY_PAUSE; // 512L
        assertTrue(MediaControlManager.supportsPauseAction(actions));
    }

    @Test
    public void testSupportsPauseActionWithActionStop() {
        long actions = PlaybackState.ACTION_STOP; // 1L
        assertTrue(MediaControlManager.supportsPauseAction(actions));
    }

    @Test
    public void testSupportsPauseActionWithCombinedFlags() {
        long actions = PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_SKIP_TO_NEXT;
        assertTrue(MediaControlManager.supportsPauseAction(actions));
    }

    @Test
    public void testSupportsPauseActionWithoutPauseSupport() {
        // e.g., only PLAY or SKIP or 0L (common in restricted video players)
        long actionsOnlyPlay = PlaybackState.ACTION_PLAY;
        assertFalse(MediaControlManager.supportsPauseAction(actionsOnlyPlay));

        long actionsZero = 0L;
        assertFalse(MediaControlManager.supportsPauseAction(actionsZero));

        long actionsSeekOnly = PlaybackState.ACTION_SEEK_TO | PlaybackState.ACTION_FAST_FORWARD;
        assertFalse(MediaControlManager.supportsPauseAction(actionsSeekOnly));
    }

    @Test
    public void testIsPlayingStateClassification() {
        assertTrue(MediaControlManager.isPlayingState(PlaybackState.STATE_PLAYING));
        assertTrue(MediaControlManager.isPlayingState(PlaybackState.STATE_BUFFERING));
        assertTrue(MediaControlManager.isPlayingState(PlaybackState.STATE_CONNECTING));

        assertFalse(MediaControlManager.isPlayingState(PlaybackState.STATE_PAUSED));
        assertFalse(MediaControlManager.isPlayingState(PlaybackState.STATE_STOPPED));
        assertFalse(MediaControlManager.isPlayingState(PlaybackState.STATE_NONE));
        assertFalse(MediaControlManager.isPlayingState(PlaybackState.STATE_ERROR));
    }

    @Test
    public void testIsPausedOrStoppedClassification() {
        assertTrue(MediaControlManager.isPausedOrStopped(PlaybackState.STATE_PAUSED));
        assertTrue(MediaControlManager.isPausedOrStopped(PlaybackState.STATE_STOPPED));
        assertTrue(MediaControlManager.isPausedOrStopped(PlaybackState.STATE_NONE));

        assertFalse(MediaControlManager.isPausedOrStopped(PlaybackState.STATE_PLAYING));
        assertFalse(MediaControlManager.isPausedOrStopped(PlaybackState.STATE_BUFFERING));
        assertFalse(MediaControlManager.isPausedOrStopped(PlaybackState.STATE_CONNECTING));
    }

    @Test
    public void testFormatStateCodeOutputs() {
        assertEquals("STATE_PLAYING", MediaControlManager.formatStateCode(PlaybackState.STATE_PLAYING));
        assertEquals("STATE_PAUSED", MediaControlManager.formatStateCode(PlaybackState.STATE_PAUSED));
        assertEquals("STATE_STOPPED", MediaControlManager.formatStateCode(PlaybackState.STATE_STOPPED));
        assertEquals("STATE_NONE", MediaControlManager.formatStateCode(PlaybackState.STATE_NONE));
        assertEquals("STATE_BUFFERING", MediaControlManager.formatStateCode(PlaybackState.STATE_BUFFERING));
    }

    @Test
    public void testMediaSessionInfoCapabilities() {
        // Session with ACTION_PAUSE advertises canPause == true
        MediaSessionInfo controllableSession = new MediaSessionInfo(
                "com.spotify.music",
                "Spotify",
                true,
                true,
                PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE,
                "Song Title",
                "Artist Name",
                "Playing"
        );
        assertTrue(controllableSession.isPlaying());
        assertTrue(controllableSession.canPause());
        assertEquals("Spotify", controllableSession.getAppName());
        assertEquals(PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE, controllableSession.getActions());

        // Session without ACTION_PAUSE (e.g., restricted video session) advertises canPause == false
        MediaSessionInfo restrictedSession = new MediaSessionInfo(
                "com.amazon.avod.thirdpartyclient",
                "Prime Video",
                true,
                false,
                0L,
                "Movie Title",
                "Prime Video",
                "Playing"
        );
        assertTrue(restrictedSession.isPlaying());
        assertFalse(restrictedSession.canPause());
        assertEquals("Prime Video", restrictedSession.getAppName());
        assertEquals(0L, restrictedSession.getActions());
    }
}
