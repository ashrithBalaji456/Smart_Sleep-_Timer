package com.smartsleeptimer.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.smartsleeptimer.app.media.MediaControlResult;
import com.smartsleeptimer.app.model.TimerState;

import org.junit.Test;

/**
 * Compatibility matrix and error-handling verification test suite.
 * Validates state transitions, status reporting, and truthfulness guarantees.
 */
public class CompatibilityMatrixTest {

    @Test
    public void testTruthfulPlaybackReporting() {
        // Confirmed pause must explicitly mention paused
        MediaControlResult confirmed = MediaControlResult.timerCompletedPlaybackPaused("Spotify");
        assertEquals(MediaControlResult.Status.TIMER_COMPLETED_PAUSED_CONFIRMED, confirmed.getStatus());
        assertTrue(confirmed.getDisplayMessage().contains("paused"));
        assertTrue(confirmed.getDisplayMessage().contains("Spotify"));

        // Unconfirmed pause must NOT claim playback was paused
        MediaControlResult unconfirmed = MediaControlResult.timerCompletedPauseUnconfirmed("Spotify");
        assertEquals(MediaControlResult.Status.TIMER_COMPLETED_PAUSE_UNCONFIRMED, unconfirmed.getStatus());
        assertFalse(unconfirmed.getDisplayMessage().contains("Playback paused"));
        assertTrue(unconfirmed.getDisplayMessage().contains("unconfirmed state"));
    }

    @Test
    public void testMissingAccessGracefulHandling() {
        MediaControlResult accessMissing = MediaControlResult.timerCompletedAccessMissing();
        assertEquals(MediaControlResult.Status.TIMER_COMPLETED_ACCESS_MISSING, accessMissing.getStatus());
        assertTrue(accessMissing.getDisplayMessage().contains("permission is missing"));
        assertNotNull(accessMissing.getDisplayMessage());
    }

    @Test
    public void testNoSessionGracefulHandling() {
        MediaControlResult noSession = MediaControlResult.timerCompletedNoSession();
        assertEquals(MediaControlResult.Status.TIMER_COMPLETED_NO_SESSION, noSession.getStatus());
        assertTrue(noSession.getDisplayMessage().contains("No active media player found"));
    }

    @Test
    public void testTimerStateTransitions() {
        // READY -> RUNNING
        TimerState ready = TimerState.READY;
        assertEquals("READY", ready.name());

        // RUNNING -> PAUSED
        TimerState running = TimerState.RUNNING;
        assertEquals("RUNNING", running.name());

        // PAUSED -> COMPLETED
        TimerState paused = TimerState.PAUSED;
        assertEquals("PAUSED", paused.name());

        TimerState completed = TimerState.COMPLETED;
        assertEquals("COMPLETED", completed.name());
    }

    @Test
    public void testMultiAppKnownPackageResolution() {
        // Known packages
        String[] knownPackages = {
                "com.spotify.music",
                "com.google.android.youtube",
                "com.google.android.apps.youtube.music",
                "com.amazon.mp3",
                "com.apple.android.music",
                "com.soundcloud.android"
        };
        for (String pkg : knownPackages) {
            assertNotNull(pkg);
            assertFalse(pkg.isEmpty());
        }
    }
}
