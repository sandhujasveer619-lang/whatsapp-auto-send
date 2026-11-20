# Project: whatsapp-auto-send (AutoSend)

Android app that sends a customizable WhatsApp message to a chosen chat/group
at a selected daily time. WebView UI (assets/index.html) + Java bridge in
MainActivity + AccessibilityService that drives WhatsApp's UI. No root.

## Key files

- `app/src/main/assets/index.html` — Dark Neon WebView UI (#121214 / #00C951).
- `app/src/main/java/com/zyvo/autosend/MainActivity.java` — WebView + JS bridge
  (`getSettings`, `save`, `getChats`, `getLastResult`, `pickChats`, `testSend`,
  permission openers) + broadcast receivers for chats list and send result.
- `app/src/main/java/com/zyvo/autosend/WhatsappAccessibilityService.java` —
  send chain + chat-list scan. Broadcast actions: TRIGGER, SCAN_CHATS,
  SEND_RESULT.
- `app/src/main/java/com/zyvo/autosend/ChatPicker.java` — scan request helper.
- `app/src/main/java/com/zyvo/autosend/Settings.java` — SharedPreferences
  (settings, chats_json, chats_scan, last_result, result_ts).
- `app/src/main/java/com/zyvo/autosend/Scheduler.java`, `ScheduleReceiver.java`,
  `BootReceiver.java`, `Trigger.java`.
- `.github/workflows/build.yml` — GitHub Actions `gradle assembleDebug`,
  uploads artifact + attaches APK to release `apk-<run_number>`.

## Commands

```bash
cd $HOME/whatsapp-auto-send
git add -A && git commit -m "msg" && git push origin main
# watch build:
curl -s -H "Authorization: token $TOKEN" \
  https://api.github.com/repos/sandhujasveer619-lang/whatsapp-auto-send/actions/runs?per_page=1
# latest APK:
https://github.com/sandhujasveer619-lang/whatsapp-auto-send/releases/latest
```

No local gradle/java on Termux — all builds run on GitHub Actions.

## Decisions (dated)

- 2026-11-19: Dark Neon design (option 3); AccessibilityService drives WhatsApp
  UI; AlarmManager setExactAndAllowWhileIdle, re-arm on reboot.
- 2026-11-20: recipient picker added (Browse reads real WhatsApp chat list).
- 2026-11-20: send chain hardened; send result shown on-screen + Android Toast.
- 2026-11-20: "Send while screen off" toggle (pref `screen_off`, default true).
  Trigger detects `!PowerManager.isInteractive()`, takes a FULL wake lock, and
  marks a prelude; service launches `SendBridgeActivity` (translucent, keyguard
  dismiss) then waits 2.2s before the normal chain. Terminal `report(msg,true)`
  re-locks via `GLOBAL_ACTION_LOCK_SCREEN` and releases the lock. Secure (PIN)
  keyguard cannot be dismissed unattended — send will report a failure.
  versionCode 2 / versionName 1.1. Shipped as apk-7 (build 44671435403 green).
- 2026-11-20: user asked to auto-dismiss a PASSWORD keyguard at trigger time
  (two taps wake -> swipe -> password -> Enter -> send -> re-lock). Investigation:
  no root (`su` denied), `/system/bin/input keyevent` -> SecurityException
  INJECT_EVENTS, `run-as com.zyvo.autosend` -> Permission denied (Termux cannot
  write app prefs), adb/wireless-debugging not enabled. `requestDismissKeyguard`
  only works on non-secure (Swipe/none) locks.   Remaining lead: AccessibilityService
  `dispatchGesture` (needs `canPerformGestures="true"`) + ACTION_SET_TEXT on the
  password field; device-specific and unverified. Password must NEVER be committed
  to this public repo — it has to be entered once in-app into private prefs.
- 2026-11-20: user chose the best-effort in-app route. Implemented: `Settings`
  prefs `auto_unlock` + `unlock_password` (private, entered via UI only);
  `saveUnlock` JS bridge; UI card under screen-off toggle; `canPerformGestures=true`
  plus SystemUI/keyguard packages in accessibility config; service `beginUnlock`
  does swipe-up gesture -> ACTION_SET_TEXT (fallback: tap keyboard key nodes) ->
  Enter -> poll `KeyguardManager.isKeyguardLocked()/isDeviceLocked()` -> runSend;
  failure path is terminal so it re-locks. versionCode 3 / versionName 1.2.
  NOT yet verified on device — needs a scheduled-send test with a password lock.

## Current state

Latest shipped: apk-8 (commit ce7cda5), build green, APK HTTP 200 — screen-off
sending works on Swipe/no-lock devices. Working tree now has the unverified
auto-unlock feature (not yet committed/built). Send failures show the failing
stage ("Chat not found", "Message box not found", "Send button not found",
"Could not open WhatsApp chats list", "Could not unlock screen"). Search-result
selection walks up to 6 ancestors and falls back to the first word.
