# ZikrTime 0.8.19 — saved ayahs and clear reading statistics

User request: 2 November 2026. Android version 0.8.19 (31), branch `codex/quran-reader-0814`, draft PR #4. Continues the verified 0.8.18 reader. The 0.8.17 submission under RuStore moderation is unchanged.

## Saved reading

- Bookmarks and notes must be visible after reopening, without checking each ayah individually. Their indicators are independent of the transient selected ayah.
- The index shows bookmark and note totals, per-surah indicators, and note previews alongside saved bookmarks. Both existing index lists remain available.
- The reader exposes the saved ayahs in the current surah with exact references and note previews. A deliberate selection in this list navigates to the referenced ayah; opening the list itself does not move the reading position or add reading credit.
- Existing bookmark and note preference keys remain compatible. Deleting a note preserves its bookmark, and removing a bookmark preserves its note.

## Statistics

- Separate today's reading/goal, selected-period totals, the last seven days, and all-time coverage. A collapsed goal editor replaces the persistent long slider.
- Period controls affect period totals. The weekly section explicitly remains the last seven days; all-time values remain all-time. Unique ayahs are deduplicated within each scope.
- Empty and disabled states explain the current state. The shared personal-progress toggle remains available, with an explicit enable action when disabled.
- Automatic tracking, time recording, opt-out behavior and source text are unchanged. Statistics remain local estimates from viewed ayahs, not proof of reading or religious assessment.

## Verification

Delivered as a signed **test preview**, with the following verified scope. Do not call all six UI groups complete.

- Android source: `b7703f66aceb9a123992bc4266f1809cb8caed01`.
- [Build run 41511213221](https://github.com/ibragimvisaevv-cloud/ZikrTime/actions/runs/41511213221): 29 focused Quran unit tests passed; lint has no errors; debug and signed release builds passed; the original certificate and exact Quran assets inside the release APK passed.
- Three UI groups passed in that run: persistent bookmark/note/combined marks in both modes after restart; exact saved-list navigation and immediate deletion updates preserving bookmarks; no reading credit while saved-list/note menus are open. The run stopped because the test's numeric-field input did not replace the value.
- [Recheck 41514721125](https://github.com/ibragimvisaevv-cloud/ZikrTime/actions/runs/41514721125), verification commit `30686032b720ce186a777004e935fea681de395c`, verified unchanged Android inputs, both APK hashes and the prior test reports. It reused the exact APK and carried over those three groups.
- The fourth UI group then passed: distinct 7/30/90-day totals, unique ayahs, weekly/all-time values, invalid goal rejection and goal persistence after restart.
- The recheck stopped during the disabled-state journey when upward automation swipes dismissed the statistics sheet before the enable button could be located. The disabled notice and zero totals had been reached. This is an incomplete automation journey, not a verified enable-state outcome. Crash logs are empty.
- **Still pending:** complete disabled-to-enabled/empty-state journey, and dark/320 dp/150% text layout journey. To continue, reopen statistics at its top before tapping `quran_stats_enable`, retain the four verified groups using the same APK/source/hash guard, and run only the remaining groups. Do not rebuild unchanged Android inputs.
- Visual review completed for light reading marks, saved-ayah list, personal-note preview, Today/period/overall statistics and the goal editor. Physical-phone testing and the remaining dark/narrow review are not claimed.
- Evidence branches: `ci-reader-0819-41511213221` and `ci-reader-0819-41514721125`. Original build artifact: `13174791180`; recheck artifact: `13175207772`.

| Candidate | Value |
| --- | --- |
| File | `ZikrTime-0.8.19-preview.apk` |
| Size | 61,210,087 bytes |
| SHA-256 | `8a3c8f2addbca9a7a726ec4eaf3a269089e300d1d021feecd2460ae5e18998ef` |
| Certificate SHA-256 | `7ae126783c9417f9feeb15e05498eec466f97705f8b1831b4393754108502ccf` |
| Debug APK | 102,117,540 bytes; `b2320efce7f39322066575b2e47d2458e4b70cdbab4073d68408c4b05f1d69d5` |

The signed APK and three original light-theme screenshots (statistics, saved notes list, saved ayahs) were provided as downloadable files when the user requested the result. APK bytes were checked against the build delivery record.

The `[quran-paper]` marker runs the focused Android workflow with `QURAN_QA_MODE=saved`, checking all reader locales, Quran unit tests, lint, signed candidates and changed saved-ayah/statistics journeys. Preserve original signing and Quran source bytes as described in `SIGNING_CURRENT.md` and `QURAN_READING_0818.md`.

No store publication, merge to main, bot deployment, iOS change, new payments or audio is included.
