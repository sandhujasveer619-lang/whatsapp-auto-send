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

## Current state

Release apk-5 (commit 59270b8), build green, APK HTTP 200. User testing real
send. If a send fails, the app shows the failing stage ("Chat not found",
"Message box not found", "Send button not found", "Could not open WhatsApp
chats list").
