# Smart Sleep Timer — Phase 4: Automatic Media Pausing & Timer Completion

A modern, calming night-themed Android application that lets users set a sleep timer that runs reliably in the background and automatically pauses active media playback (Spotify, YouTube, Amazon Music, etc.) upon timer expiration.

This repository contains **Phase 1 (Timer UI)**, **Phase 2 (Media Session Access)**, **Phase 3 (Reliable Background Timer)**, and **Phase 4 (Automatic Media Pausing & Timer Completion)**. The overall project consists of exactly 6 phases.

---

## 🌙 Overview of Implemented Phases

### Phase 1: App Foundation and Functional Timer (Preserved)
- Night-sky visual identity with custom vector branding (`ic_moon_headphones.xml`).
- Timer presets: 15m, 30m, 45m, 1h, 2h, and custom duration picker (Hours & Minutes).
- Large countdown display and custom animated circular arc indicator (`CircularTimerView`).
- Start, Pause, Resume, and Cancel controls with 300ms click debounce.
- **⚡ 30-Second Quick Verification Timer** for rapid testing.

### Phase 2: Android Media Playback Control (Preserved)
- Native `MediaSessionManager` and `MediaController` integration.
- `MediaNotificationListenerService` enables cross-app session access.
- Player identification (Spotify, YouTube, Amazon Music, etc.).
- Dedicated **Media Playback Control (Phase 2 Test)** card with manual *Test Pause Audio*.

### Phase 3: Reliable Background Timer (Preserved)
- `SleepTimerService` runs as an official Android Foreground Service.
- Native Chronometer countdown on the notification shade and lock screen without continuous CPU waking.
- Doze-mode exact wakeups via `AlarmManager.setExactAndAllowWhileIdle()` and `TimerCompletionReceiver`.
- `TimerPreferences` persists target deadlines to recover state across process termination.

### Phase 4: Automatic Media Pausing & Timer Completion (New)
- **Automatic Playback Pause**: When the timer deadline is reached, `SleepTimerService` automatically queries active media controllers and dispatches the pause command.
- **Session Selection Strategy**: If multiple media apps exist, prioritizes an actively playing session (`STATE_PLAYING`) that explicitly supports `ACTION_PAUSE`.
- **Playback State Verification**: Uses a timed post-check (600ms) to verify whether the target app actually transitioned to `STATE_PAUSED` or `STATE_STOPPED` vs. an unconfirmed state.
- **Dedicated Completion Notification**: Issues a separate notification (`ID: 1002`) on a dedicated channel (`sleep_timer_completion_channel`) indicating the exact outcome.
- **In-App Completion Outcome Banner**: When the user reopens the app, displays the result banner with actionable options (*Start New Timer* or *Enable Media Access*).
- **Single Execution Guarantee**: Uses an atomic guard (`AtomicBoolean isCompleting`) to ensure the completion event, media pause, and notification execute strictly once per session.

---

## 🏗️ Architecture & Component Overview

```
app/src/main/java/com/smartsleeptimer/app/
├── MainActivity.java                       # Coordinates Timer UI, Media Test Card, and Phase 4 Outcome Banner
├── service/
│   ├── SleepTimerService.java              # [PHASE 4] Triggers auto-pause, posts completion notification, & shuts down
│   └── TimerCompletionReceiver.java        # Receives AlarmManager Doze wake-up and routes to service
├── media/
│   ├── MediaControlManager.java            # [PHASE 4] Dispatches auto-pause with post-check verification & selection strategy
│   ├── MediaControlResult.java             # [PHASE 4] Outcome models: Paused Confirmed, Unconfirmed, No Session, Missing Access
│   ├── MediaAccessHelper.java              # Checks NotificationListener access & Settings intent
│   ├── MediaNotificationListenerService.java # Android-bound service enabling session access
│   └── MediaSessionInfo.java               # Active session metadata and state
├── util/
│   ├── TimerPreferences.java               # [PHASE 4] Persists completion outcome & timestamp across process death
│   └── TimeFormatter.java                  # Time and duration formatting utility
├── viewmodel/
│   ├── SleepTimerViewModel.java            # [PHASE 4] Exposes completion result LiveData to UI
│   └── MediaControlViewModel.java          # Exposes Phase 2 manual testing LiveData
├── engine/
│   ├── SleepTimerEngine.java               # Timer state machine & monotonic calculations
│   └── TimerListener.java                  # Tick and state listener contract
├── ui/
│   ├── CircularTimerView.java              # Gradient arc custom view
│   └── CustomDurationDialog.java           # Validated hours & minutes picker dialog
└── model/
    └── TimerState.java                     # READY, RUNNING, PAUSED, COMPLETED
```

---

## 🎯 Documented Media Session Selection Strategy

When the sleep timer expires:
1. `MediaControlManager.getActiveControllers()` queries active sessions via `MediaSessionManager`.
2. **Step 1 — Actively Playing**: Prioritizes sessions currently in `PlaybackState.STATE_PLAYING`, `STATE_BUFFERING`, or `STATE_CONNECTING`.
3. **Step 2 — Action Support**: Among active sessions, selects one that explicitly advertises `PlaybackState.ACTION_PAUSE` or `ACTION_PLAY_PAUSE`.
4. **Step 3 — Fallback Candidate**: If no session is actively reporting playing (e.g. paused shortly before expiration), selects the session with `ACTION_PAUSE` that was most recently active.
5. **Step 4 — No Unrelated Pauses**: If no session exposes controllable media controls, reports `NO_CONTROLLABLE_SESSION_FOUND` rather than blindly sending key events to random background tasks.

---

## 🔍 Playback Verification vs. Unconfirmed States

Android's `MediaController.getTransportControls().pause()` dispatches an asynchronous one-way IPC command:
- **Verification Routine**: `MediaControlManager` dispatches the command and schedules a 600ms post-check.
- **Confirmed (`TIMER_COMPLETED_PAUSED_CONFIRMED`)**: If `getPlaybackState().getState()` reflects `STATE_PAUSED` or `STATE_STOPPED`, reports *"Sleep timer finished. Playback paused in [AppName]."*
- **Unconfirmed (`TIMER_COMPLETED_PAUSE_UNCONFIRMED`)**: If the player does not expose an updated state or remains in `STATE_PLAYING`, reports *"Sleep timer finished. Pause command sent to [AppName] (unconfirmed state)."*

---

## 🧪 Detailed Manual Testing Procedure (Phase 4)

Follow these steps on an Android device or emulator:

1. **Timer Expires While Spotify is Playing**:
   - Start playback in Spotify.
   - Open Smart Sleep Timer, select the **⚡ 30-Second Quick Verification Timer**, and tap **Start**.
   - Lock the phone or switch to another app.
   - When 30 seconds elapse, observe:
     - Spotify audio pauses automatically.
     - Ongoing countdown notification dismisses.
     - A new completion notification appears: *"Sleep timer finished. Playback paused in Spotify."*
2. **Timer Expires While YouTube is Playing**:
   - Start video or music playback in YouTube (or YouTube Music).
   - Start a 30-second timer and lock the screen.
   - When timer expires, verify the pause command is sent and reported.
3. **Timer Expires While Amazon Music is Playing**:
   - Repeat test with Amazon Music or another streaming app.
4. **Timer Expires With No Media Playing**:
   - Close all media players.
   - Start a 30-second timer.
   - Upon completion, verify the completion notification states: *"Sleep timer finished. No active media player found."*
5. **Media Access Revoked Before Completion**:
   - Start a timer, then revoke notification access in Android Settings.
   - Allow timer to expire.
   - Verify graceful handling: timer completes and reports *"Media access permission is missing"* without crashing.
6. **Phone Locked at Timer Expiration**:
   - Start a timer and lock the phone.
   - Allow timer to expire while locked.
   - Wake the screen: verify completion notification is present on the lock screen and audio is paused.
7. **Reopening the App After Completion**:
   - Unlock phone and open Smart Sleep Timer after timer completion.
   - Verify the in-app **Timer Completion Outcome** card is visible with the exact result.
   - Tap **Start New Timer** and verify the outcome banner hides and timer resets to `READY`.
8. **Service Reconnection Does Not Duplicate Pause**:
   - Start a timer, rotate the screen or switch apps repeatedly as the timer reaches zero.
   - Verify that the auto-pause and completion notification fire **strictly once**.
9. **Timer Completes Even If Media Control Fails**:
   - If an unsupported media app is open, verify the timer still reaches `COMPLETED` state and does not hang in `RUNNING`.
10. **Phase 1, 2, and 3 Regression Check**:
    - Confirm presets (15m, 30m, 45m, 1h, 2h, custom), manual *Test Pause Audio*, and persistent notification controls continue working.

---

## ⚠️ Known Media Application Behavior & Limitations

- **Spotify**: Consistently exposes active media sessions and updates state to `STATE_PAUSED` within ~200ms.
- **YouTube (Standard vs. Premium)**: Free YouTube pauses video output when backgrounded unless PiP is enabled. When active in background or PiP, it receives and executes the pause command.
- **Amazon Music / Apple Music**: Honor transport controls when actively streaming.
- **One-Way Dispatch**: As with all native Android media controllers, the actual halting of sound output is executed by the target player.

---

## 🔒 Strict Phase Boundaries

- **Phase 4 Scope (Implemented)**: Automatic media pause at timer expiration, session selection strategy, post-check state verification, distinct completion notifications, and in-app outcome cards.
- **Deferred to Phase 5**: Comprehensive UI polish, animation fine-tuning, and multi-device OEM testing.
- **Deferred to Phase 6**: Final production packaging, release build, and final APK delivery.
