# LookAway for Android

An Android port of the macOS [LookAway](../LookAway) app. It enforces the
[20-20-20 rule](https://www.aao.org/eye-health/tips-prevention/computer-usage): every 20 minutes
it covers the screen for 20 seconds and asks you to look at something 20 feet away.

You start it once. After that it keeps running in the background, survives the app being swiped
away, process kills, reboots and app updates, and only stops when you tap **Stop**.

| | |
|---|---|
| Flutter | 3.47 / Dart 3.13 |
| Android | minSdk 26 (8.0), targetSdk 36 |
| State | flutter_riverpod 3 |
| Native | Kotlin, no extra Android libraries |

---

## How it maps to the macOS app

| macOS | Android |
|---|---|
| Menu bar item `👁️ 14 min` | Persistent notification with a live countdown (native chronometer) |
| Menu: Reset / Skip For 10m 30m 1h 2h / Quit | In app: Reset, Break now, Pause for 10m 30m 1h 2h, Stop. Notification actions: Pause 30 min, Reset, Stop |
| "20 seconds left" notification | "Break in 20 seconds" heads-up notification |
| Borderless window at `.screenSaver` level, 90% black, one per display | Full-screen `TYPE_APPLICATION_OVERLAY` window, same 90% black |
| "Look Away" text + Skip button | Same layout, plus a live seconds countdown |
| "Purr" sound when the break ends | "Break over" notification using the channel's sound (respects silent mode and Do Not Disturb) |
| Hidden dock icon | Not applicable |
| User adds it to Login Items by hand | Starts automatically after reboot and after app updates |

### What's better than the Mac version

- **Only screen time counts.** The timer pauses while the screen is off or locked. No reminder ever
  fires into an empty room.
- **You choose what happens when the screen comes back on.** *Continue timer* (the default) picks
  up exactly where the countdown stopped. *Restart timer* starts a fresh period if the screen was
  off for at least one break length, since that already counts as a break.
- **Configurable.** Break every 10, 20, 30, 45 or 60 minutes; breaks of 20, 30 or 60 seconds.

### Behavior changes from the Mac version

- **After a pause ends, a fresh work period starts.** The Mac version resumed from wherever the
  countdown had stopped.
- **The macOS timer only ticked every 20 seconds.** The menu bar label could lag by a tick, and the
  break could start up to 20 seconds late. Android schedules the exact moment instead.

---

## Architecture

```
┌──────────── Flutter (UI only) ─────────────┐          ┌──────────────── Kotlin (always on) ─────────────────┐
│                                            │          │                                                     │
│  HomeScreen ─┐                             │ Method   │  MainActivity ──► BreakService  (foreground service,│
│  Permissions ┼─► BreakService.dart ────────┼─Channel─►│                    type specialUse, START_STICKY)   │
│              │                             │          │                    ├─ BreakTimer   pure state machine│
│  breakStateProvider ◄──────────────────────┼─Event───◄│                    ├─ BreakStore   SharedPreferences │
│  permissionsProvider                       │ Channel  │                    ├─ BreakNotifications             │
│                                            │          │                    ├─ BreakOverlay (WindowManager)   │
└────────────────────────────────────────────┘          │                    └─ screen on/off receiver         │
                                                        │  BootReceiver ──► BreakService (boot, app update)    │
                                                        └─────────────────────────────────────────────────────┘
```

**Flutter only renders the UI and sends commands. All timing lives in native code.** That choice
drives everything else:

- Killing the UI, or never opening it, has no effect on breaks.
- No Flutter engine runs in the background. Background engines cost about 30–50 MB each, and
  Android's low-memory killer removes the biggest background processes first. A lean service
  process is much less likely to be killed.
- The overlay is a plain Android `View`. It is only a logo, two text labels and a button, so a
  second Flutter engine would cost a lot and add nothing.

### The timer: `BreakTimer.kt`

The timer is a pure, immutable state machine with no Android imports, and it is unit-tested.
Every function takes `now` from `SystemClock.elapsedRealtime()`.

```
          start                 work time is up               break time is up
STOPPED ─────────► WORKING ──────────────────────► BREAK ──────────────────────► WORKING (fresh)
                     │  ▲                            │
              pause  │  │ pause ends / resume        │ Skip (no sound)
                     ▼  │                            ▼
                    PAUSED                        WORKING (fresh)
```

- **WORKING** counts down only while the screen is on and unlocked. When the screen turns off, the
  remaining time is frozen.
- When the screen comes back on, the countdown continues where it stopped. With
  `resetAfterScreenOff`, a screen-off of at least one break length starts a fresh period instead.
- `evaluate(now)` applies at most one transition and returns an `Event`: `WARN`,
  `BREAK_STARTED`, `BREAK_FINISHED` or `PAUSE_ENDED`.
- `nextWakeAt()` returns the next moment anything can change, which is the one time the service
  needs to wake up.

### Why there's no AlarmManager or wake lock

Work time only counts while the screen is on, and while the screen is on the CPU is awake. So a
plain `Handler.postDelayed` fires on time. While the screen is off nothing needs to fire, and Doze
can delay handlers as much as it likes.

The `SCREEN_ON`, `SCREEN_OFF` and `USER_PRESENT` broadcasts don't carry state themselves. Each one
only triggers `syncScreen()`, which reads the real state from `PowerManager.isInteractive` and
`KeyguardManager.isKeyguardLocked`. While the lock screen is showing, `syncScreen()` re-checks
once per second until the phone is unlocked or the screen turns off. Opening the app also re-syncs.
So a missed or late broadcast can't leave the timer frozen.

> The receiver must be registered `RECEIVER_EXPORTED`. `USER_PRESENT` comes from SystemUI, not the
> system uid, and a `RECEIVER_NOT_EXPORTED` receiver silently never receives it. That bug froze the
> timer after unlocking. Exporting is safe because all three are protected broadcasts, which only
> the platform can send.

As a result the app needs no `SCHEDULE_EXACT_ALARM` permission (which Android 14+ denies by
default, and Google Play restricts), and it costs essentially no battery.

### The service: `BreakService.kt`

- `onStartCommand` always calls `startForeground` first, which the `startForegroundService()`
  contract requires. Then it handles the command: `RUN`, `STOP`, `RESET`, `SKIP`, `BREAK_NOW`,
  `PAUSE`, `RESUME` or `CONFIG`.
- `update(next, event)` is the **only** place where state changes become side effects: show or hide
  the overlay, notify, save the state, and push the state to Flutter.
- `tick()` applies every transition that is due, then schedules the next wake-up.

### Staying alive

| Threat | Defense |
|---|---|
| App swiped from recents | Foreground service; the UI isn't needed |
| Low-memory kill | Small native-only process; `START_STICKY` restarts it, and `BreakStore` restores the exact state |
| Reboot | `BootReceiver` (`BOOT_COMPLETED`) |
| App update | `BootReceiver` (`MY_PACKAGE_REPLACED`) |
| Aggressive OEM battery managers (Xiaomi, Samsung, Oppo, Vivo, OnePlus) | Setup screen sends the user to App info to set Battery to **Unrestricted** and turn on **Autostart** |
| All of the above failed | Opening the app restarts the service, or re-syncs it if it's running (`MainActivity.onResume`) |

Timer state is stored as `elapsedRealtime` timestamps together with the device boot count.
State saved during a previous boot is discarded, because those timestamps are meaningless after a
reboot.

No app can survive the user tapping **Stop** in Android 14+'s "Active apps" panel, and it
shouldn't. That's the user's call.

---

## Folder structure

The layout follows the team's Flutter reference project (`ReferanceDa`).

```
lib/
├── main.dart                         # Checks permissions → picks the first screen → runApp
├── core/
│   ├── constants/                    # app_colors, app_assets, constants (options, paddings)
│   ├── extensions/duration_ex.dart   # 19:59 / 1:02:03 formatting
│   └── utils/                        # enums (BreakPhase), theme_utils
├── models/                           # break_state, app_permissions (parsed from channel maps)
├── services/break_service.dart       # Platform channel wrapper: the only file that talks to native code
├── providers/                        # breakStateProvider (stream), permissionsProvider (refreshes on resume)
├── routes/routes.dart                # Named routes + RouteGenerator
└── views/
    ├── screens/
    │   ├── home/                     # Countdown ring, controls, schedule, setup banner
    │   └── permissions/              # First-run setup and reliability settings
    └── widgets/                      # SectionCard, OptionChips

android/app/src/main/kotlin/com/gadgetvala/lookaway/
├── MainActivity.kt                   # Channels: commands, state stream, permission intents
├── service/
│   ├── BreakTimer.kt                 # Pure state machine (unit-tested)
│   ├── BreakService.kt               # Foreground service: the always-on part
│   ├── BreakStore.kt                 # Persistence across process death
│   └── BreakNotifications.kt         # Channels, status/warning/done notifications
├── overlay/BreakOverlay.kt           # Full-screen break window
└── receiver/BootReceiver.kt          # Reboot and app-update restart

android/app/src/main/res/
├── layout/overlay_break.xml          # Break overlay layout
├── values/strings.xml                # All native user-facing text
└── drawable/, mipmap-anydpi/         # Adaptive launcher icon (vector redraw of the Mac logo), status icon
```

### Where this differs from the reference project

- **Riverpod `Notifier` / `StreamProvider`** instead of `ChangeNotifierProvider` from
  `flutter_riverpod/legacy.dart`. The legacy API is deprecated in Riverpod 3.
- **No `ScreenUtil`, `Sizes` or `SizedBoxH*` helpers.** Material 3 layout with `spacing:` on
  Row/Column does the same job without scaling every value by hand.
- **No `shared_preferences`.** Native code owns all persisted state, so the UI and the service can
  never disagree.

---

## Permissions

| Permission | Why | How it's granted |
|---|---|---|
| `SYSTEM_ALERT_WINDOW` | The break overlay | Settings → Display over other apps (required) |
| `POST_NOTIFICATIONS` | Countdown, warning, end-of-break sound | Runtime dialog on Android 13+, otherwise app notification settings |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` | The always-on timer | Granted at install |
| `RECEIVE_BOOT_COMPLETED` | Restart after reboot | Granted at install |
| Battery "Unrestricted" | Stops OEM killers | App info → Battery (the app links straight there) |

If the overlay permission is revoked while the app is running, breaks fall back to a heads-up
notification, so a break is never lost silently.

Each notification channel can be adjusted separately in system settings: **Timer**, **Break
warning** and **Break finished**. For example, you can mute the end-of-break sound or turn off the
warning without touching the others.

---

## Build and run

```bash
flutter pub get
flutter run                      # debug build; adds a "1 min" interval for quick testing
flutter build apk --release      # build/app/outputs/flutter-apk/app-release.apk
```

Release builds are currently signed with the debug key. Add a real `signingConfig` in
`android/app/build.gradle.kts` before publishing.

### Tests

```bash
flutter test                                       # Dart: state parsing, countdown extrapolation, formatting
cd android && ./gradlew :app:testDebugUnitTest     # Kotlin: BreakTimer state machine
```

### Manual test checklist (real device)

1. On first launch the setup screen appears. Grant the overlay permission, then continue.
2. Tap **Start**. A notification appears with a live countdown.
3. Tap **Break now**. The overlay covers everything, counts down, ignores Back, and **Skip**
   closes it.
4. In a debug build, pick **1 min**. The warning arrives at 0:20, the overlay at 0:00, and a sound
   plays when the break ends.
5. Note the countdown, lock the phone for a minute, then unlock. With *Continue timer* it carries
   on from the same value. With *Restart timer* it starts a full period again. Either way it must be
   **running** again; check both the app and the notification.
6. Same as step 5, but stay on the lock screen for about 10 seconds before unlocking. The timer
   should stay frozen until you're past the lock screen.
7. Swipe the app away from recents. The timer keeps running and the overlay still appears.
8. Reboot. The notification comes back without opening the app.
9. Try the notification actions: Pause 30 min, Reset, Stop.

---

## Publishing to Google Play

- **`specialUse` foreground service.** Play Console asks for a written justification and a short
  video. The justification text is already in `AndroidManifest.xml`
  (`PROPERTY_SPECIAL_USE_FGS_SUBTYPE`).
- **The app does not use `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.** Play often rejects apps that
  use it. The app opens App info instead, which is allowed, and on Xiaomi phones that page also has
  Autostart.
- **Don't switch the overlay to an Accessibility service.** That would cover the status bar and be
  harder to kill, but Play bans Accessibility use for non-accessibility purposes.

## Known limitations

- On some Android versions and phone brands, the status bar and notification shade can still draw
  above the overlay.
- The Home gesture still works during a break. The overlay stays on top of the launcher, just as
  Cmd+Tab works on macOS.
- Phones with very aggressive battery management may still kill the timer until the user changes
  the setting on the setup screen. See [dontkillmyapp.com](https://dontkillmyapp.com).
