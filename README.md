# Smart Sleep Timer — Phase 3: Reliable Background Timer

A modern, calming night-themed Android application that lets users set a sleep timer for audio apps (Spotify, YouTube, Amazon Music, etc.) with drift-free background execution, Doze mode resilience, and persistent process recovery.

This repository contains **Phase 1 (Timer UI Foundation)**, **Phase 2 (Android Media Session Access)**, and **Phase 3 (Reliable Background Timer)**. The overall project consists of exactly 6 phases.

---

## 🌙 Overview of Implemented Phases

### Phase 1: App Foundation and Functional Timer (Preserved)
- Night-sky visual identity with custom vector branding (`ic_moon_headphones.xml`).
- Timer presets: 15m, 30m, 45m, 1h, 2h, and custom duration picker (Hours & Minutes).
- Large countdown display and custom animated circular arc indicator (`CircularTimerView`).
- Start, Pause, Resume, and Cancel state machine with 300ms click debounce.
- **⚡ 30-Second Quick Verification Timer** for rapid testing.

### Phase 2: Android Media Playback Control (Preserved)
- `MediaSessionManager` and `MediaController` integration.
- `MediaNotificationListenerService` declared and implemented for cross-app session access.
- Application identification (Spotify, YouTube, Amazon Music, etc.) and transport pause testing.
- Dedicated **Media Playback Control (Phase 2 Test)** card on the main dashboard.

### Phase 3: Reliable Background Timer (New)
- **Foreground Service Architecture**: `SleepTimerService` keeps the timer running independently of the Activity lifecycle.
- **Battery-Conscious Native Chronometer**: Uses `NotificationCompat.Builder.setUsesChronometer(true)` and `setChronometerCountDown(true)` to display real-time second-by-second countdowns on the status bar and lock screen *without waking the CPU on every tick*.
- **Doze Mode Alarm Wake-up**: `AlarmManager.setExactAndAllowWhileIdle()` with `TimerCompletionReceiver` guarantees the timer-completion event triggers at the exact second even when the phone is locked and stationary in deep Doze mode.
- **Process Recovery Persistence**: `TimerPreferences` persists target deadlines in `SharedPreferences`. If Android terminates the process under memory pressure, reopening the app recalculates the remaining time against the stored deadline.

---

## 🏗️ Architecture & Component Overview

```
app/src/main/java/com/smartsleeptimer/app/
├── MainActivity.java                       # Main Activity coordinating Timer UI & Media Test Card
├── service/                                # [PHASE 3] Background Execution Package
│   ├── SleepTimerService.java              # Foreground Service managing notification & countdown
│   └── TimerCompletionReceiver.java        # BroadcastReceiver for exact AlarmManager Doze wakeups
├── util/
│   ├── TimerPreferences.java               # [PHASE 3] Deadline persistence for process kill recovery
│   └── TimeFormatter.java                  # Phase 1: Time and duration formatting utility
├── media/                                  # Phase 2: Media Control Package
│   ├── MediaAccessHelper.java              # Permission checks & Settings Intent creator
│   ├── MediaNotificationListenerService.java # NotificationListenerService for media session access
│   ├── MediaSessionInfo.java               # Model for detected app, state, & track info
│   ├── MediaControlResult.java             # Result model with readable status messages
│   └── MediaControlManager.java            # Manages MediaSessionManager, controllers, & pause logic
├── viewmodel/
│   ├── SleepTimerViewModel.java            # Phase 1+3: Bridges UI to SleepTimerService and LiveData
│   └── MediaControlViewModel.java          # Phase 2: Media session & permission LiveData
├── engine/
│   ├── SleepTimerEngine.java               # Unit-testable timer state engine
│   └── TimerListener.java                  # Listener contract
├── ui/
│   ├── CircularTimerView.java              # Phase 1: Gradient arc custom view
│   └── CustomDurationDialog.java           # Phase 1: Validated duration picker dialog
└── model/
    └── TimerState.java                     # READY, RUNNING, PAUSED, COMPLETED
```

---

## ⏱️ Reliable Timekeeping & Recovery Analysis

### 1. Monotonic Deadline Calculation
Rather than subtracting 1 second on each callback, `SleepTimerService` records the target monotonic deadline:
$$\text{endTimeElapsedRealtime} = \text{SystemClock.elapsedRealtime}() + \text{remainingMillis}$$
Whenever the Activity resumes, the screen unlocks, or a tick fires, remaining time is recalculated as:
$$\text{remainingMillis} = \max(0, \text{endTimeElapsedRealtime} - \text{SystemClock.elapsedRealtime}())$$
This makes timekeeping drift-free regardless of thread delays or background throttling.

### 2. Process Death & Device Reboot Recovery
- **Surviving Process Termination**: When the timer starts, `TimerPreferences` persists both `end_time_elapsed_ms` and `end_time_epoch_ms` (`System.currentTimeMillis() + remaining`). If Android terminates the app process, reopening the app immediately loads the stored epoch deadline:
  - If $\text{now} \ge \text{end\_time\_epoch}$, the timer transitions to `COMPLETED`.
  - If $\text{now} < \text{end\_time\_epoch}$, the exact remaining duration is restored and the timer resumes seamlessly.
- **Device Reboot Limitation**: `SystemClock.elapsedRealtime()` resets to zero on device reboot. Because wall-clock time is also stored, the app can detect whether the timer expired while the phone was turned off upon relaunch.

---

## 🔋 Battery Optimization & Android Power Restrictions

1. **Native Chronometer vs. CPU Polling**:
   - Instead of running a 1-second WakeLock and continuously waking the CPU to update the notification, we use Android's built-in `setUsesChronometer(true)` and `setChronometerCountDown(true)`. The Android SystemUI updates the notification view natively, saving significant battery.
2. **Doze Mode & AlarmManager**:
   - In Android 6.0+, Doze mode restricts network access, CPU wakelocks, and standard `Handler` timers when the device is unplugged, stationary, and with the screen off.
   - We schedule `AlarmManager.setExactAndAllowWhileIdle()`. When the deadline arrives, the OS wakes the device to deliver the completion event via `TimerCompletionReceiver`.
3. **OEM Background Killers (Manufacturer Restrictions)**:
   - Aggressive battery optimizations by manufacturers (such as Xiaomi MIUI/HyperOS, Samsung OneUI, Huawei, and OnePlus OxygenOS) can restrict background processes despite standard Foreground Services.
   - For standard operation, our Foreground Service with `FOREGROUND_SERVICE_SPECIAL_USE` complies with official Android 14 (API 34) guidelines.

---

## 🧪 Detailed Manual Testing Procedure (Phase 3)

Follow these steps on an Android device or emulator:

1. **Minimize the App While Running**:
   - Start a 15-minute or 30-second timer.
   - Press the **Home** button to minimize Smart Sleep Timer.
   - Observe that a persistent notification appears showing the real-time countdown.
2. **Lock Screen for > 1 Minute**:
   - Start a timer, press the power button to lock the device, and wait 1–2 minutes.
   - Check the lock screen: the notification continues counting down accurately.
3. **Unlock and Verify In-App Synchronization**:
   - Unlock the phone and return to Smart Sleep Timer.
   - Verify that the countdown and the circular arc match the elapsed time without jumping or restarting.
4. **App Switching**:
   - Start the timer and switch between Spotify, YouTube, and the browser.
   - Return to Smart Sleep Timer and confirm the countdown continued uninterrupted.
5. **Pause & Resume from Notification**:
   - Tap **Pause** directly on the persistent notification.
   - Notice the notification updates to *"Timer paused at MM:SS"* and the chronometer freezes.
   - Tap **Resume** on the notification; countdown continues accurately.
6. **Cancel from Notification**:
   - Tap **Cancel** on the notification.
   - Verify the notification dismisses and the in-app state resets to `READY`.
7. **Simulate Process Recreation / Rotation**:
   - Rotate the phone while the timer is running.
   - Verify that the timer does not reset or duplicate.
8. **Simulate Process Death**:
   - In developer options, test with "Don't keep activities" or force-kill the activity from terminal.
   - Reopen Smart Sleep Timer and verify that the remaining time is correctly restored from `TimerPreferences`.
9. **Notification Permission Denied (Android 13+)**:
   - Deny `POST_NOTIFICATIONS` permission.
   - Verify that the in-app timer still functions and recalculates deadlines without crashing.
10. **Phase 1 and Phase 2 Verification**:
    - Confirm all presets (15m, 30m, 45m, 1h, 2h, custom) work as expected.
    - Confirm the Phase 2 **Test Pause Audio** and **Refresh Sessions** buttons continue functioning.

---

## 🔒 Strict Phase Boundaries

- **Phase 3 Scope (Implemented)**: Foreground service execution, persistent notification with native countdown, Doze-mode AlarmManager scheduling, deadline persistence, and UI synchronization.
- **Deferred to Phase 4**:
  - Automatically pausing active media playback when the timer reaches zero.
  - Media session audio focus ducking/fading.
  - Final timer completion notification sound/vibration.
- **Deferred to Phase 5 & 6**:
  - UI polish and broad OEM compatibility testing (Phase 5).
  - Final production build and APK release (Phase 6).
