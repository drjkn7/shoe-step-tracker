# Manual checks on the phone (before each release)

Things only a person with the real phone can check. Tick them in the release PR.
Test IDs refer to [TEST_PLAN.md](TEST_PLAN.md).

| ✓ | Check | How | Expected |
|---|---|---|---|
| ☐ | Screen reader (T25) | Settings → Accessibility → TalkBack on. Swipe through Home, a pair, Settings. | Every button and row is read out sensibly; nothing says just "button". |
| ☐ | Right-to-left (T25) | Settings → System → Developer options → "Force RTL layout direction". | Screens mirror (back arrow on the right); no text is cut off. Turn it off afterwards. |
| ☐ | Restart mid-session (T17) | Start a session, restart the phone, open the app. | The session is still running. |
| ☐ | Session notification (T40–T44) | Android 13+: start a session, answer Continue, allow. Lock the phone, then unlock; pause and resume from the notification with the app closed; tap End session; restart the phone mid-session and open the app. | Asked once in the app's words before Android's prompt. Lock screen shows only "Session running". Pause/Resume act at once and the timer leaves out paused time; End session opens "End this session?". After the restart the notification is back once the app is opened. |
| ☐ | Airplane mode (T18) | Airplane mode on; add a pair, start/end a session, export. | Everything works (the app never needs the internet). |
| ☐ | Time zone change (T20) | During a session, change the phone's time zone; end it. | Times show in the zone they were recorded in; duration is right. |
| ☐ | ~200 steps (T09) | Start, walk about 200 steps, end, open the app a minute later. | Steps close to Health Connect's own count. |
| ☐ | Pause excludes steps (T10) | Start, walk, pause, walk more, resume, walk, end. | Only the walking while running is counted. |
| ☐ | Watch + phone (T21) | Wear a watch that writes steps to Health Connect; do a session. | Not counted twice (Health Connect's app priority decides). |
| ☐ | Count becomes final (T19) | Open the app a day after a session. | "About" disappears; the number may have moved up or down until then. |
| ☐ | Lock a tag (T05) | On a **spare** tag: Link NFC tag → tick "Lock the tag" → tap. Then tap it with the app closed. | "Tag linked"; the tag still starts sessions. A tag-writer app (e.g. NFC Tools) shows it as read-only. |
| ☐ | Export opens (Phase 7) | Export pairs and sessions; open both in Sheets / another app. | First heading reads `Name` / `Pair`; rows look right. |
| ☐ | Release APK | Install the signed release APK (see [RELEASE.md](RELEASE.md)), tap a linked tag with the app closed. | Opens the pair with a session running. |
