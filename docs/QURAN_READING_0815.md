# ZikrTime 0.8.15 — Arabic spacing and whole-surah reading

Built on 2026-10-30 from `9dd8738c6381bd235c9f64cea8de6fa4ad8f2947` on `codex/quran-reader-0814` (draft PR #4). Version code 27.

Arabic typography uses a relative 2.25em line height, untrimmed line boxes and font padding. Quran, azkar and the other shared-Arabic-style screens inherit it. Original Quran source strings and their checksums remain unchanged.

The Quran reader now offers **Read entire surah** / **Verses and translation**. Whole-surah mode is one continuous scroll with verse markers and no interleaved translation or verse cards. Eight-verse internal layout blocks are invisible and do not require paging. The selected mode persists. Jumps and reopening split a block at the requested verse so the requested verse remains an exact anchor; scrolling then saves the first verse in the visible layout block.

Verification: [GitHub Actions run 41114183120](https://github.com/ibragimvisaevv-cloud/ZikrTime/actions/runs/41114183120). Source import and localized resources verified; 79 unit tests passed with no failures/errors/skips; lint has zero errors. Android 15 layout scenarios passed: offline whole-surah reading, mode/text across process restart, long-surah end and resume, translation return, invalid verse bounds, dark theme, 320dp/150% font, azkar and shared-Arabic-screen smoke. Crash log is empty. Actual screenshots were visually inspected for verse reading, whole-surah reading, Al-Baqarah, An-Nas, narrow-screen large fonts and azkar. Full audio journeys were previously checked on 0.8.14 and were not repeated for this typography change. Physical-phone verification remains with the user.

Ready file: `ZikrTime-0.8.15-test.apk` (debug), 101804710 bytes.

SHA-256: `2c446e196aae1302bdd4833c6a8980875c9c2e4718d881c88a4039dd28f1e633`.

GitHub artifact: `13005756968`, `ZikrTime-0.8.15-check`; APK inside `app/build/outputs/apk/debug/app-debug.apk`. Native ChatGPT file: `libfile_9960ef7c2d4c8191b7413c1851f0e217`. Use this verified APK for transfers; do not rebuild just to deliver it. Debug signing compatibility with an existing phone installation was not established.

The build ran in GitHub Actions and is independent of the user's PC. This does not change the execution host of the current chat. Main, stores, backend and iOS were not changed.
