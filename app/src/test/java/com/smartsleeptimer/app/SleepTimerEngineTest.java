package com.smartsleeptimer.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.smartsleeptimer.app.engine.SleepTimerEngine;
import com.smartsleeptimer.app.model.TimerState;

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Unit tests verifying state machine transitions, drift-free calculations,
 * and duplicate timer prevention in SleepTimerEngine.
 */
public class SleepTimerEngineTest {

    private AtomicLong simulatedTime;
    private SleepTimerEngine engine;

    @Before
    public void setUp() {
        simulatedTime = new AtomicLong(100000L);
        SleepTimerEngine.TimeProvider testTimeProvider = () -> simulatedTime.get();
        engine = new SleepTimerEngine(null, testTimeProvider);
        engine.setDuration(30 * 1000L); // 30 seconds
    }

    @Test
    public void testInitialState_IsReady() {
        assertEquals(TimerState.READY, engine.getState());
        assertEquals(30 * 1000L, engine.getRemainingMillis());
    }

    @Test
    public void testStart_TransitionsToRunning() {
        boolean started = engine.start();
        assertTrue(started);
        assertEquals(TimerState.RUNNING, engine.getState());
    }

    @Test
    public void testDuplicateStart_IsPrevented() {
        engine.start();
        boolean secondStart = engine.start();
        assertFalse("Duplicate start should be prevented while running", secondStart);
        assertEquals(TimerState.RUNNING, engine.getState());
    }

    @Test
    public void testPause_FreezesRemainingTime() {
        engine.start();
        // Advance 10 seconds
        simulatedTime.addAndGet(10 * 1000L);

        boolean paused = engine.pause();
        assertTrue(paused);
        assertEquals(TimerState.PAUSED, engine.getState());
        assertEquals(20 * 1000L, engine.getRemainingMillis());

        // Advance 5 seconds while paused -> remaining time must not decrease!
        simulatedTime.addAndGet(5 * 1000L);
        assertEquals(20 * 1000L, engine.getRemainingMillis());
    }

    @Test
    public void testResume_ContinuesCountdown() {
        engine.start();
        simulatedTime.addAndGet(10 * 1000L);
        engine.pause();

        // Advance while paused
        simulatedTime.addAndGet(5 * 1000L);

        boolean resumed = engine.resume();
        assertTrue(resumed);
        assertEquals(TimerState.RUNNING, engine.getState());

        // Advance another 5 seconds after resume
        simulatedTime.addAndGet(5 * 1000L);
        engine.refreshTick();
        assertEquals(15 * 1000L, engine.getRemainingMillis());
    }

    @Test
    public void testCancel_ResetsToConfiguredDuration() {
        engine.start();
        simulatedTime.addAndGet(15 * 1000L);

        engine.cancel();
        assertEquals(TimerState.READY, engine.getState());
        assertEquals(30 * 1000L, engine.getRemainingMillis());
    }

    @Test
    public void testCountdownExpiration_TransitionsToCompleted() {
        engine.start();
        // Advance beyond total duration (35 seconds)
        simulatedTime.addAndGet(35 * 1000L);

        engine.refreshTick();
        assertEquals(TimerState.COMPLETED, engine.getState());
        assertEquals(0L, engine.getRemainingMillis());
    }

    @Test
    public void testInvalidDuration_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> engine.setDuration(0L));
        assertThrows(IllegalArgumentException.class, () -> engine.setDuration(-1000L));
    }
}
