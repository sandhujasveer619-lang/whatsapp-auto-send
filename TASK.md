# GOAL: Build AutoSend — an Android app that sends a customizable WhatsApp message to a chosen chat/group at a selected AM/PM time daily, with an enable/disable switch.

Plan:
- [x] Collect app idea, design pick (Dark Neon), GitHub credentials
- [x] Design 3 UI directions + preview in Chrome
- [x] Scaffold Gradle project + GitHub Actions build workflow
- [x] Build WebView UI (Dark Neon) with toggle, time picker, message, recipient, permissions
- [x] Build automation engine (AccessibilityService + AlarmManager + boot receiver)
- [x] Push to GitHub and watch build to green (run 44560868119 success, commit 1fab5f1)
- [x] Deliver APK link + setup instructions (release apk-2, APK HTTP 200 verified)
- [ ] Add recipient picker: Browse button reads real WhatsApp chat/group list via AccessibilityService, searchable sheet to pick a chat
- [ ] Rebuild to green + deliver new APK

Key decisions:
- Design 3 = Dark Neon (#121214 bg, #00C951 accent).
- Sending method: AccessibilityService drives WhatsApp UI (search chat by exact name → type message → tap Send). No root required.
- Scheduling: AlarmManager setExactAndAllowWhileIdle, re-armed after each fire and on reboot.
- Sources in $HOME/whatsapp-auto-send (not shared storage) so git works.

NOW: adding the recipient picker (ChatPicker.java + AccessibilityService scan + WebView sheet),
then rebuild + deliver new APK.
