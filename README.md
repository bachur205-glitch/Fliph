# Flipdeck: a launcher for the TCL Flip 2 (4058G)

D-pad launcher with big icons. Written for the Android-based OS some retailers list for the
4058G. It will not work if your phone turns out to run KaiOS (check Settings > About).

## Controls

| Key | Action |
|---|---|
| OK | Open the menu (all apps, as a grid or a list) |
| Hold OK | Launcher settings |
| D-pad up | Quick settings (Wi-Fi, Bluetooth, mobile data, hotspot, airplane mode) |
| D-pad down / left / right | Anything you choose (defaults: Phone / Messages / Camera) |
| Left softkey | Notices |
| Right softkey | Contacts by default (hold OK > "Right softkey action" to change it) |
| Back | Close the current screen |

In the app menu, pressing right at the end of a row continues on the next row (left goes back).
In Notices, the right softkey (or the clear key, or holding OK) erases just the selected notice.

In the app menu, "Launcher settings" is listed alphabetically with your apps and opens the launcher settings.
The menu can be a grid or a list: hold OK > "Menu look".
The package `com.moshe.flipmdm` is never shown anywhere in the launcher (menu, app picker, notices).

## Build

You can't build this without the Android SDK, so use a computer:

1. Install Android Studio and open this folder.
2. Let it sync. If it offers to update the Android Gradle Plugin, accept.
3. Build > Build APK(s). The file is in `app/build/outputs/apk/debug/`.

## Install on the phone

- Settings > About: tap the build number 7 times to enable Developer options, turn on USB debugging,
  then run `adb install app-debug.apk`.
- Or copy the APK to a microSD card, open it from a file manager, and allow "unknown sources".

Then press Home and choose Flipdeck as the home app (or Settings > Apps > Default apps > Home app).

## First-run setup

1. **Softkeys:** hold OK > "Left softkey" / "Right softkey" > press the physical key. Do this if the
   softkeys don't open Notices and Contacts. The phone may report them as SOFT_LEFT/SOFT_RIGHT or MENU.
2. **Notices:** open Notices once and press OK to grant notice access.
3. **D-pad keys:** hold OK > Down/Left/Right key > pick an action or "Launch an app...".

## Limits

Android doesn't let normal apps switch Wi-Fi, mobile data, hotspot or airplane mode directly.
Those tiles show live state (where the phone reports it) and open the one-tap system screen for
that setting. Bluetooth toggles directly. Fully direct toggles would need a system-signed app.
