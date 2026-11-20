# GOAL: Build AutoSend — an Android app that sends a customizable WhatsApp message to a chosen chat/group at a selected AM/PM time daily, with an enable/disable switch.

Plan:
- [x] Collect app idea, design pick (Dark Neon), GitHub credentials
- [x] Design 3 UI directions + preview in Chrome
- [x] Scaffold Gradle project + GitHub Actions build workflow
- [x] Build WebView UI (Dark Neon) with toggle, time picker, message, recipient, permissions
- [x] Build automation engine (AccessibilityService + AlarmManager + boot receiver)
- [x] Push to GitHub and watch build to green (run 44560868119 success, commit 1fab5f1)
- [x] Deliver APK link + setup instructions (release apk-2, APK HTTP 200 verified)
- [x] Add recipient picker: Browse button reads real WhatsApp chat/group list via AccessibilityService, searchable sheet to pick a chat
- [x] Rebuild to green + deliver new APK (run 44664910693 success, release apk-3 HTTP 200)

Key decisions:
- Design 3 = Dark Neon (#121214 bg, #00C951 accent).
- Sending method: AccessibilityService drives WhatsApp UI (search chat by exact name → type message → tap Send). No root required.
- Scheduling: AlarmManager setExactAndAllowWhileIdle, re-armed after each fire and on reboot.
- Sources in $HOME/whatsapp-auto-send (not shared storage) so git works.

- [x] Fix: WhatsApp opens but message does not send — harden send chain (force chats list via BACK, flexible chat-name match, wider element ids, longer waits, on-screen result reporting)
- [x] Fix CI compile error: findByText(String,String) overload ambiguity; build green, release apk-5 verified HTTP 200

- [x] Fix "Chat not found" when tapping search result (e.g. "jot Bro"): click walks 6 ancestors, clickable-row subtree fallback, first-word query retry, skip editable field (run 44669299465 success, apk-6 HTTP 200)

- [x] Add "Send while screen is off": persistent toggle (default ON); on a
  screen-off alarm, acquire a FULL wake lock, launch invisible SendBridgeActivity
  to dismiss the keyguard, then run the normal send chain; on a terminal result
  re-lock the screen (GLOBAL_ACTION_LOCK_SCREEN, goToSleep fallback pre-28) and
  release the wake lock. Known limit: a secure PIN/pattern lock must be unlocked
  once; the system cannot dismiss a secure keyguard unattended.

- [x] Build green (run 44671435403) + APK verified HTTP 200 (release apk-7).

NOW: DONE — screen-off sending shipped. Test it: lock the phone with a Swipe/
no lock screen, wait for the scheduled time, and check "Last send" in the app.
