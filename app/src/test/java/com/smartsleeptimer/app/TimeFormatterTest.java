package com.smartsleeptimer.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.smartsleeptimer.app.util.TimeFormatter;

import org.junit.Test;

/**
 * Unit tests for TimeFormatter utility methods.
 */
public class TimeFormatterTest {

    @Test
    public void testFormatTime_MinutesAndSeconds() {
        assertEquals("00:00", TimeFormatter.formatTime(0));
        assertEquals("00:30", TimeFormatter.formatTime(30 * 1000L));
        assertEquals("15:00", TimeFormatter.formatTime(15 * 60 * 1000L));
        assertEquals("45:30", TimeFormatter.formatTime((45 * 60 + 30) * 1000L));
    }

    @Test
    public void testFormatTime_WithHours() {
        assertEquals("01:00:00", TimeFormatter.formatTime(60 * 60 * 1000L));
        assertEquals("01:30:15", TimeFormatter.formatTime((90 * 60 + 15) * 1000L));
        assertEquals("02:00:00", TimeFormatter.formatTime(120 * 60 * 1000L));
    }

    @Test
    public void testFormatTime_NegativeClampsToZero() {
        assertEquals("00:00", TimeFormatter.formatTime(-5000L));
    }

    @Test
    public void testToMillis_Valid() {
        assertEquals(30 * 60 * 1000L, TimeFormatter.toMillis(0, 30));
        assertEquals(60 * 60 * 1000L, TimeFormatter.toMillis(1, 0));
        assertEquals(90 * 60 * 1000L, TimeFormatter.toMillis(1, 30));
    }

    @Test
    public void testToMillis_NegativeThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> TimeFormatter.toMillis(-1, 30));
        assertThrows(IllegalArgumentException.class, () -> TimeFormatter.toMillis(0, -5));
    }

    @Test
    public void testFormatDurationLabel() {
        assertEquals("30 sec", TimeFormatter.formatDurationLabel(30 * 1000L));
        assertEquals("15 min", TimeFormatter.formatDurationLabel(15 * 60 * 1000L));
        assertEquals("1 hour", TimeFormatter.formatDurationLabel(60 * 60 * 1000L));
        assertEquals("2 hours", TimeFormatter.formatDurationLabel(120 * 60 * 1000L));
        assertEquals("1h 15m", TimeFormatter.formatDurationLabel(75 * 60 * 1000L));
    }
}
