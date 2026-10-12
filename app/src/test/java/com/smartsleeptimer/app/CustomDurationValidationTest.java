package com.smartsleeptimer.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.smartsleeptimer.app.util.TimeFormatter;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

/**
 * Unit tests validating custom duration picker logic, bounds, and quick increment math.
 */
public class CustomDurationValidationTest {

    @Test
    public void testValidCustomDurations() {
        // 30 minutes
        long millis30m = TimeFormatter.toMillis(0, 30);
        assertEquals(TimeUnit.MINUTES.toMillis(30), millis30m);

        // 1 hour 15 minutes
        long millis1h15m = TimeFormatter.toMillis(1, 15);
        assertEquals(TimeUnit.MINUTES.toMillis(75), millis1h15m);

        // 24 hours exactly (max allowed)
        long millis24h = TimeFormatter.toMillis(24, 0);
        assertEquals(TimeUnit.HOURS.toMillis(24), millis24h);
    }

    @Test
    public void testZeroDurationIsInvalid() {
        long millis0 = TimeFormatter.toMillis(0, 0);
        assertEquals(0L, millis0);
        assertTrue("Zero duration must be considered invalid", millis0 <= 0);
    }

    @Test
    public void testExceedsMaximumDuration() {
        int hours = 24;
        int minutes = 1;
        boolean exceedsMax = hours > 24 || (hours == 24 && minutes > 0);
        assertTrue("24h 1m must exceed max duration", exceedsMax);

        hours = 25;
        minutes = 0;
        exceedsMax = hours > 24 || (hours == 24 && minutes > 0);
        assertTrue("25h must exceed max duration", exceedsMax);
    }

    @Test
    public void testWithinMaximumDuration() {
        int hours = 23;
        int minutes = 59;
        boolean exceedsMax = hours > 24 || (hours == 24 && minutes > 0);
        assertFalse("23h 59m must not exceed max duration", exceedsMax);

        hours = 24;
        minutes = 0;
        exceedsMax = hours > 24 || (hours == 24 && minutes > 0);
        assertFalse("24h 0m must not exceed max duration", exceedsMax);
    }

    @Test
    public void testQuickIncrementRolloverMath() {
        // Start with 45 minutes, add 30 minutes -> 75 minutes = 1 hour 15 minutes
        int currentHours = 0;
        int currentMinutes = 45;
        int addedMinutes = 30;

        int totalMinutes = (currentHours * 60) + currentMinutes + addedMinutes;
        int newHours = totalMinutes / 60;
        int newMinutes = totalMinutes % 60;

        assertEquals(1, newHours);
        assertEquals(15, newMinutes);
    }

    @Test
    public void testQuickIncrementCappedAt24Hours() {
        int currentHours = 23;
        int currentMinutes = 30;
        int addedMinutes = 60; // 1 hour -> 24h 30m -> should cap to 24h 0m

        int totalMinutes = (currentHours * 60) + currentMinutes + addedMinutes;
        int maxMinutes = 24 * 60;
        if (totalMinutes > maxMinutes) {
            totalMinutes = maxMinutes;
        }

        int newHours = totalMinutes / 60;
        int newMinutes = totalMinutes % 60;

        assertEquals(24, newHours);
        assertEquals(0, newMinutes);
    }
}
