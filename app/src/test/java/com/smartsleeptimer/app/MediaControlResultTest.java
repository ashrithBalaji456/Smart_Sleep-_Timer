package com.smartsleeptimer.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.smartsleeptimer.app.media.MediaControlResult;

import org.junit.Test;

/**
 * Unit tests for MediaControlResult factory methods and status messages.
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
}
