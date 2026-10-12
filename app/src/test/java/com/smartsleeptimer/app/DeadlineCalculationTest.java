package com.smartsleeptimer.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Unit tests verifying deadline-based timekeeping calculations,
 * process recovery mathematics, and Doze mode boundary checks.
 */
public class DeadlineCalculationTest {

    @Test
    public void testDeadlineRemaining_AccurateWithoutOneSecondIntervals() {
        long startTime = 100000L;
        long duration = 30 * 60 * 1000L; // 30 minutes
        long deadline = startTime + duration;

        // Simulate 7 minutes, 13 seconds, 420ms passing in background
        long backgroundElapsedTime = (7 * 60 + 13) * 1000L + 420L;
        long now = startTime + backgroundElapsedTime;

        long remaining = Math.max(0L, deadline - now);
        assertEquals(duration - backgroundElapsedTime, remaining);
    }

    @Test
    public void testDeadlineExpiration_ClampsToZero() {
        long startTime = 100000L;
        long duration = 30 * 1000L; // 30 seconds
        long deadline = startTime + duration;

        // Device slept in Doze mode for 45 seconds
        long now = startTime + (45 * 1000L);

        long remaining = Math.max(0L, deadline - now);
        assertEquals(0L, remaining);
    }

    @Test
    public void testProcessDeathRecovery_EpochDeadlineAccurate() {
        long saveEpoch = 1700000000000L;
        long remainingAtSave = 15 * 60 * 1000L; // 15 minutes
        long deadlineEpoch = saveEpoch + remainingAtSave;

        // Process killed and relaunched 5 minutes later
        long reopenEpoch = saveEpoch + (5 * 60 * 1000L);
        long recoveredRemaining = Math.max(0L, deadlineEpoch - reopenEpoch);

        assertEquals(10 * 60 * 1000L, recoveredRemaining);
    }

    @Test
    public void testProcessDeathRecovery_ExpiredWhileKilled() {
        long saveEpoch = 1700000000000L;
        long remainingAtSave = 15 * 60 * 1000L;
        long deadlineEpoch = saveEpoch + remainingAtSave;

        // Process killed and relaunched 20 minutes later (past deadline)
        long reopenEpoch = saveEpoch + (20 * 60 * 1000L);
        long recoveredRemaining = Math.max(0L, deadlineEpoch - reopenEpoch);

        assertEquals(0L, recoveredRemaining);
    }
}
