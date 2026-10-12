# Smart Sleep Timer — Phase 1

A modern, calming night-themed Android application that lets users set a sleep timer for listening to music, podcasts, or other audio. 

This repository contains **Phase 1: App Foundation and Functional Timer**. Media control and background services will be added in subsequent phases.

---

## 🌙 Visual Identity & UI Design
- **Theme**: Calming dark night-sky aesthetics with deep slate/navy surfaces (`#0B0F19`, `#161F33`), soft luminescent cyan accents (`#00E5FF`), and warm lunar amber/emerald indicators.
- **Branding**: Custom vector identity featuring a glowing crescent moon resting with over-ear headphones and night stars.
- **Display**: High-contrast, large countdown display (`HH:MM:SS` or `MM:SS`) centered inside a custom animated circular arc indicator.
- **Presets**: 15m, 30m, 45m, 1h, 2h, a custom duration picker, plus a **30-second quick-test button** for rapid verification.
- **States**: Dynamic pill badge reflecting `READY`, `RUNNING`, `PAUSED`, or `COMPLETED`.

---

## 🏗️ Architecture & Component Overview

```
app/src/main/java/com/smartsleeptimer/app/
├── MainActivity.java                // Primary Activity; observes LiveData & binds UI
├── model/
│   └── TimerState.java              // Enum: READY, RUNNING, PAUSED, COMPLETED
├── engine/
│   ├── SleepTimerEngine.java        // Drift-free wall-clock timer engine (SystemClock.elapsedRealtime)
│   └── TimerListener.java           // Callback contract for ticks, state transitions, & completion
├── viewmodel/
│   └── SleepTimerViewModel.java     // Android ViewModel surviving rotation & debounce logic
├── ui/
│   ├── CircularTimerView.java       // Custom Canvas view drawing the dual-color gradient ring
│   └── CustomDurationDialog.java    // Input dialog for hours & minutes with strict validation
└── util/
    └── TimeFormatter.java           // Formatting utilities (HH:MM:SS, label generator, validation)
```

### Key Architectural Decisions:
1. **Drift-Free Time Calculation**: Rather than subtracting 1 second on each tick (which drifts when the thread lags or the screen turns off), `SleepTimerEngine` computes `endTimeRealtime = SystemClock.elapsedRealtime() + remainingMillis`. Every tick recalculates remaining time against `SystemClock.elapsedRealtime()`.
2. **Configuration Change Survival**: `SleepTimerViewModel` retains the timer instance and LiveData streams across screen rotations or Activity recreations. Returning to the app triggers `viewModel.refreshTimer()` in `onResume()`.
3. **Debounced User Actions**: Rapid taps on "Start", "Pause", or "Resume" are debounced and checked against the state machine to prevent duplicate timers or concurrent countdown loops.
4. **Input Validation**: The custom duration dialog validates both hours and minutes, disallowing empty values, negative values, 0-minute durations, or durations over 24 hours.

---

## 🚀 How to Open and Run in Android Studio

1. **Launch Android Studio** (Hedgehog, Iguana, Jellyfish, or newer).
2. Click **Open** (or **File > Open**) and select this project directory:
   ```
   C:\Users\ashri\SpringNew\AutoStopMedia
   ```
3. Allow Android Studio to sync the Gradle project automatically.
4. Select a virtual device (Emulator) or your physical Android phone from the device dropdown.
5. Click the green **Run (Shift + F10)** button.

---

## 📱 How to Install and Test on a Physical Android Phone

1. **Enable Developer Options on your phone**:
   - Go to **Settings > About Phone**.
   - Tap **Build Number** 7 times until you see "You are now a developer!".
2. **Enable USB Debugging**:
   - Go to **Settings > System > Developer Options**.
   - Toggle **USB Debugging** ON.
3. **Connect your phone to your PC via USB cable**:
   - Accept the "Allow USB debugging?" authorization prompt on your phone screen.
4. In Android Studio, your phone will appear in the target device selector.
5. Click **Run** to compile and automatically install the app on your phone.

*(Alternatively, to generate a standalone APK: select **Build > Build Bundle(s) / APK(s) > Build APK(s)** in Android Studio. The output `.apk` file will be generated in `app/build/outputs/apk/debug/` and can be transferred to your phone).*

---

## 🧪 Verification & Acceptance Test Checklist

You can test all scenarios using the built-in **⚡ 30-Second Quick Verification Timer**:

| Test Scenario | Action | Expected Result |
|---|---|---|
| **1. Countdown** | Select a preset (e.g. 30s Quick Test) and tap **Start** | Countdown begins, ring arc decreases smoothly, status changes to `RUNNING`. |
| **2. Pause** | While running, tap **Pause** | Remaining time stops decreasing, status badge changes to `PAUSED`, **Resume** button appears. |
| **3. Resume** | Tap **Resume** | Countdown resumes exactly where it paused, status changes back to `RUNNING`. |
| **4. Cancel** | Tap **Cancel** | Timer immediately stops, status changes to `READY`, countdown resets to the selected duration. |
| **5. Expiration** | Let 30s timer reach zero | Displays `COMPLETED`, remaining time shows `00:00`, toast appears, and Start button resets. |
| **6. Custom Duration Validation** | Tap **Custom**, enter `0` hours and `0` mins, or leave blank | Error message displayed; dialog rejects invalid input. |
| **7. Orientation / Lifecycle** | Start countdown and rotate phone | Timer continues accurately without resetting or skipping ticks. |
| **8. Double-Tap Prevention** | Rapidly tap **Start** multiple times | Only one timer runs; duplicate timers are prevented. |

---

## 🔒 Phase Boundaries (Out of Scope for Phase 1)
- **Spotify / YouTube API integration**: Deferred to Phase 2.
- **MediaSessionManager & Audio Focus pause**: Deferred to Phase 2.
- **Foreground Service & Persistent Lock Screen Notification**: Deferred to Phase 2.
