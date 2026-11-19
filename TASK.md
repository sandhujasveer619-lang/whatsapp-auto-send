# GOAL: Build AutoSend — an Android app that sends a customizable WhatsApp message to a chosen chat/group at a selected AM/PM time daily, with an enable/disable switch.

Plan:
- [x] Collect app idea, design pick (Dark Neon), GitHub credentials
- [x] Design 3 UI directions + preview in Chrome
- [x] Scaffold Gradle project + GitHub Actions build workflow
- [x] Build WebView UI (Dark Neon) with toggle, time picker, message, recipient, permissions
- [x] Build automation engine (AccessibilityService + AlarmManager + boot receiver)
- [ ] Push to GitHub and watch build to green
- [ ] Deliver APK link + setup instructions

Key decisions:
- Design 3 = Dark Neon (#121214 bg, #00C951 accent).
- Sending method: AccessibilityService drives WhatsApp UI (search chat by exact name → type message → tap Send). No root required.
- Scheduling: AlarmManager setExactAndAllowWhileIdle, re-armed after each fire and on reboot.
- Sources in $HOME/whatsapp-auto-send (not shared storage) so git works.

NOW: pushing to GitHub and watching the build.
