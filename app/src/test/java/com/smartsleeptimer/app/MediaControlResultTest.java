package com.smartsleeptimer.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.smartsleeptimer.app.media.MediaControlResult;

import org.junit.Test;

/**
 * Unit tests for MediaControlResult factory methods, status messages,
 * and Phase 4 completion states.
 */
public class MediaControlResultTest {

    @Test
    public void testAccessRequired() {
        MediaControlResult result = MediaControlResult.accessRequired();
        assertEquals(MediaControlResult.Status.MEDIA_ACCESS_REQUIRED, result.getStatus());
        assertTrue(result.getDisplayMessage().contains("Media access required"));
        assertNull(result.getTargetAppName());
    }

    @Test
    public void testNoSessionFound() {
        MediaControlResult result = MediaControlResult.noSessionFound();
        assertEquals(MediaControlResult.Status.NO_ACTIVE_SESSION_FOUND, result.getStatus());
        assertTrue(result.getDisplayMessage().contains("No active media session found"));
        assertNull(result.getTargetAppName());
    }

    @Test
    public void testSessionDetected() {
        MediaControlResult result = MediaControlResult.sessionDetected("Spotify");
        assertEquals(MediaControlResult.Status.ACTIVE_SESSION_DETECTED, result.getStatus());
        assertEquals("Spotify", result.getTargetAppName());
        assertTrue(result.getDisplayMessage().contains("Spotify"));
    }

    @Test
    public void testPauseCommandSent() {
        MediaControlResult result = MediaControlResult.pauseCommandSent("YouTube");
        assertEquals(MediaControlResult.Status.PAUSE_COMMAND_SENT, result.getStatus());
        assertEquals("YouTube", result.getTargetAppName());
        assertTrue(result.getDisplayMessage().contains("Pause command sent to YouTube"));
    }

    @Test
    public void testPauseUnavailable() {
        MediaControlResult result = MediaControlResult.pauseUnavailable("Amazon Music");
        assertEquals(MediaControlResult.Status.PAUSE_COMMAND_UNAVAILABLE, result.getStatus());
        assertEquals("Amazon Music", result.getTargetAppName());
        assertTrue(result.getDisplayMessage().contains("Pause command unavailable"));
    }

    @Test
    public void testUnableToControl() {
        MediaControlResult result = MediaControlResult.unableToControl("Unknown Player");
        assertEquals(MediaControlResult.Status.UNABLE_TO_CONTROL_SESSION, result.getStatus());
        assertEquals("Unknown Player", result.getTargetAppName());
        assertTrue(result.getDisplayMessage().contains("Unable to control this media session"));
    }

    // Phase 4 Completion Tests
    @Test
    public void testTimerCompletedPlaybackPaused() {
        MediaControlResult result = MediaControlResult.timerCompletedPlaybackPaused("Spotify");
        assertEquals(MediaControlResult.Status.TIMER_COMPLETED_PAUSED_CONFIRMED, result.getStatus());
        assertEquals("Spotify", result.getTargetAppName());
        assertTrue(result.getDisplayMessage().contains("Playback paused in Spotify"));
    }

    @Test
    public void testTimerCompletedPauseUnconfirmed() {
        MediaControlResult result = MediaControlResult.timerCompletedPauseUnconfirmed("YouTube");
        assertEquals(MediaControlResult.Status.TIMER_COMPLETED_PAUSE_UNCONFIRMED, result.getStatus());
        assertEquals("YouTube", result.getTargetAppName());
        assertTrue(result.getDisplayMessage().contains("unconfirmed state"));
    }

    @Test
    public void testTimerCompletedNoSession() {
        MediaControlResult result = MediaControlResult.timerCompletedNoSession();
        assertEquals(MediaControlResult.Status.TIMER_COMPLETED_NO_SESSION, result.getStatus());
        assertNull(result.getTargetAppName());
        assertTrue(result.getDisplayMessage().contains("No active media player found"));
    }

    @Test
    public void testTimerCompletedAccessMissing() {
        MediaControlResult result = MediaControlResult.timerCompletedAccessMissing();
        assertEquals(MediaControlResult.Status.TIMER_COMPLETED_ACCESS_MISSING, result.getStatus());
        assertNull(result.getTargetAppName());
        assertTrue(result.getDisplayMessage().contains("Media access permission is missing"));
    }

    @Test
    public void testPauseConfirmed() {
        MediaControlResult result = MediaControlResult.pauseConfirmed("Spotify");
        assertEquals(MediaControlResult.Status.PAUSE_CONFIRMED, result.getStatus());
        assertEquals("Spotify", result.getTargetAppName());
        assertTrue(result.getDisplayMessage().contains("Playback paused in Spotify"));
    }

    @Test
    public void testPauseUnconfirmedStandard() {
        MediaControlResult result = MediaControlResult.pauseUnconfirmed("YouTube");
        assertEquals(MediaControlResult.Status.PAUSE_UNCONFIRMED, result.getStatus());
        assertEquals("YouTube", result.getTargetAppName());
        assertTrue(result.getDisplayMessage().contains("unconfirmed state"));
    }

    @Test
    public void testPauseUnconfirmedPrimeVideo() {
        MediaControlResult result = MediaControlResult.pauseUnconfirmed("Prime Video");
        assertEquals(MediaControlResult.Status.PAUSE_UNCONFIRMED, result.getStatus());
        assertEquals("Prime Video", result.getTargetAppName());
        assertTrue(result.getDisplayMessage().contains("Prime Video may restrict third-party pause controls"));
    }

    @Test
    public void testPauseUnavailablePrimeVideo() {
        MediaControlResult result = MediaControlResult.pauseUnavailable("Prime Video");
        assertEquals(MediaControlResult.Status.PAUSE_COMMAND_UNAVAILABLE, result.getStatus());
        assertEquals("Prime Video", result.getTargetAppName());
        assertTrue(result.getDisplayMessage().contains("Prime Video media session does not expose a remote pause action"));
    }

    @Test
    public void testTimerCompletedPauseUnconfirmedPrimeVideo() {
        MediaControlResult result = MediaControlResult.timerCompletedPauseUnconfirmed("Prime Video");
        assertEquals(MediaControlResult.Status.TIMER_COMPLETED_PAUSE_UNCONFIRMED, result.getStatus());
        assertEquals("Prime Video", result.getTargetAppName());
        assertTrue(result.getDisplayMessage().contains("Prime Video may restrict third-party pause controls"));
    }

    @Test
    public void testTimerCompletedPauseFailedPrimeVideo() {
        MediaControlResult result = MediaControlResult.timerCompletedPauseFailed("Prime Video");
        assertEquals(MediaControlResult.Status.TIMER_COMPLETED_PAUSE_FAILED, result.getStatus());
        assertEquals("Prime Video", result.getTargetAppName());
        assertTrue(result.getDisplayMessage().contains("Prime Video does not allow remote pause"));
    }

    @Test
    public void testTimerCompletedPauseFailed() {
        MediaControlResult result = MediaControlResult.timerCompletedPauseFailed("Amazon Music");
        assertEquals(MediaControlResult.Status.TIMER_COMPLETED_PAUSE_FAILED, result.getStatus());
        assertEquals("Amazon Music", result.getTargetAppName());
        assertTrue(result.getDisplayMessage().contains("unavailable or failed in Amazon Music"));
    }
}
