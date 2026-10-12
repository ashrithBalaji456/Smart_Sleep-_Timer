# Smart Sleep Timer — Phase 2: Android Media Playback Control

A modern, calming night-themed Android application that lets users set a sleep timer and inspect/control active media sessions across external media applications.

This repository contains **Phase 1 (Timer Foundation)** and **Phase 2 (Android Media Playback Control)**. The overall project consists of exactly 6 phases.

---

## 🌙 Overview of Implemented Phases

### Phase 1: App Foundation and Functional Timer (Preserved)
- Night-sky visual identity with custom vector branding (`ic_moon_headphones.xml`).
- Timer presets: 15m, 30m, 45m, 1h, 2h, and custom duration picker (Hours & Minutes).
- Large countdown display and custom animated circular arc indicator (`CircularTimerView`).
- Start, Pause, Resume, and Cancel state machine with 300ms click debounce.
- Monotonic wall-clock calculations (`SystemClock.elapsedRealtime()`) and configuration change survival.
- **⚡ 30-Second Quick Verification Timer** button for rapid testing.

### Phase 2: Android Media Playback Control (New)
- **Media Session Discovery**: Evaluates Android's `MediaSessionManager` and `MediaController` APIs.
- **Notification Access Integration**: Declares and implements `MediaNotificationListenerService` as required by Android security to inspect active external sessions.
- **Application Identification**: Resolves package names and displays friendly names (Spotify, YouTube, Amazon Music, etc.).
- **Transport Controls**: Tests pause commands via `MediaController.getTransportControls().pause()` and media key event fallbacks.
- **Media Control Test Section**: Dedicated UI card showing permission status, detected player, session state, `Refresh Sessions`, and `Test Pause Audio`.

---

## 🏗️ Architecture & Component Overview

```
app/src/main/java/com/smartsleeptimer/app/
├── MainActivity.java                       # Main Activity coordinating Timer UI & Media Test Card
├── media/                                  # [PHASE 2] Media Control Package
│   ├── MediaAccessHelper.java              # Permission checks & Settings Intent creator
│   ├── MediaNotificationListenerService.java # Required system service for active session access
│   ├── MediaSessionInfo.java               # Model for detected app, state, & track info
│   ├── MediaControlResult.java             # Result model with readable status messages
│   └── MediaControlManager.java            # Manages MediaSessionManager, controllers, & pause logic
├── viewmodel/
│   ├── SleepTimerViewModel.java            # Phase 1: Timer state & tick LiveData
│   └── MediaControlViewModel.java          # [PHASE 2] Media session & permission LiveData
├── engine/
│   ├── SleepTimerEngine.java               # Phase 1: Monotonic timer engine
│   └── TimerListener.java                  # Phase 1: Listener contract
├── ui/
│   ├── CircularTimerView.java              # Phase 1: Gradient arc custom view
│   └── CustomDurationDialog.java           # Phase 1: Validated duration picker dialog
├── model/
│   └── TimerState.java                     # READY, RUNNING, PAUSED, COMPLETED
└── util/
    └── TimeFormatter.java                  # Time and duration formatting utility
```

---

## 🔐 Android Permissions & Setup Explanation

### Why Notification Access is Required
Android restricts applications from inspecting or controlling media sessions owned by other applications. Starting in Android 5.0 (API 21) and enforced in subsequent versions:
1. `MediaSessionManager.getActiveSessions(ComponentName)` **strictly requires** a registered and enabled `NotificationListenerService`.
2. Attempting to call `getActiveSessions` without this special access throws a `SecurityException`.
3. To enable this, the user must navigate to **Android Settings > Notification Access (Special App Access)** and toggle on **Smart Sleep Timer**.
4. The application includes a one-click button in the Media Control section (**"Enable Notification Access"**) that directly opens the appropriate system settings screen.

---

## 🧪 Detailed Manual Testing Procedure (Phase 2)

Follow these steps on an Android device or emulator:

1. **Initial State (No Permission)**:
   - Launch Smart Sleep Timer.
   - Scroll down to the **Media Playback Control (Phase 2 Test)** card.
   - Verify the badge shows `Access Required` (Amber) and an explanation box appears.
2. **Settings Navigation**:
   - Tap **Enable Notification Access**.
   - Verify that Android opens the **Device & app notifications** (or Notification Access) settings page.
3. **Grant Permission**:
   - Locate and select **Smart Sleep Timer** (or "Smart Sleep Timer Media Controller").
   - Toggle **Allow notification access** ON and confirm the system warning.
4. **Return and Verify State Update**:
   - Return to Smart Sleep Timer.
   - In `onResume()`, the app automatically detects the granted permission: the badge changes to `Access Granted` (Green) and the explanation box disappears.
5. **Test with Spotify**:
   - Open Spotify and start playing any song or podcast.
   - Return to Smart Sleep Timer and tap **Refresh Sessions**.
   - Verify that **Detected Player** displays `Spotify` and the session state shows `Playing`.
   - Tap **Test Pause Audio**.
   - Verify that the result displays `Pause command sent to Spotify` and playback pauses.
6. **Test with YouTube / YouTube Music**:
   - Start video or audio playback in YouTube or YouTube Music.
   - Return to Smart Sleep Timer and tap **Refresh Sessions**.
   - Verify `YouTube` / `YouTube Music` is detected.
   - Tap **Test Pause Audio** and verify the pause behavior.
7. **Test with Amazon Music / Other Media Players**:
   - Repeat the test with Amazon Music, Apple Music, or SoundCloud.
8. **Test with No Audio Playing**:
   - Ensure all media applications are closed or paused.
   - Tap **Refresh Sessions**.
   - Verify the UI displays `No active media session found`.
9. **Test Revoking Permission**:
   - Go to Android Settings and revoke notification access for Smart Sleep Timer.
   - Return to the app.
   - Tap **Test Pause Audio** or **Refresh Sessions**.
   - Verify the app handles the revocation gracefully without crashing, displaying `Media access required`.
10. **Verify Phase 1 Regression Safety**:
    - Tap **30-Second Quick Verification Timer** and tap **Start**.
    - Verify countdown, Pause, Resume, and Cancel all continue to function flawlessly alongside media controls.

---

## ⚠️ Media Application Behavior & Known Limitations

1. **Third-Party App Implementation Differences**:
   - **Spotify**: Fully exposes standard `MediaSession` and responds immediately to `TransportControls.pause()`.
   - **YouTube**: In standard (free) YouTube, playback may pause automatically when backgrounded unless YouTube Premium / PiP is active. When background playback is active, it responds to transport pause commands.
   - **Amazon Music / Apple Music**: Expose active media sessions when actively playing audio and accept standard pause commands.
2. **Command Dispatch vs. Playback Confirmation**:
   - Android's `MediaController.getTransportControls().pause()` is a one-way command dispatched to the target session. The actual pausing of audio output depends on whether the third-party application honors the command.
3. **Emulator vs. Physical Device**:
   - Emulators without Google Play Store or third-party streaming apps installed cannot verify real-world audio session control. Testing on a physical Android phone with genuine music apps is strongly recommended.

---

## 🔒 Strict Phase Boundaries (Phase 2 Scope)

- **Implemented**: MediaSession discovery, NotificationListenerService, app resolution, transport pause commands, and manual test interface.
- **Deferred to Later Phases**:
  - Timer-triggered automatic media pausing (Phase 3+).
  - Continuous foreground service for long background countdowns.
  - Final notification media controls & lock screen experience.
  - Automatic device locking.
