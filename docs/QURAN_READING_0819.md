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

Implementation and focused verification are in progress. Do not describe this version as tested or ready until the matching build and emulator evidence are recorded below.

The `[quran-paper]` marker runs the focused Android workflow with `QURAN_QA_MODE=saved`, checking all reader locales, Quran unit tests, lint, signed candidates and changed saved-ayah/statistics journeys. Preserve original signing and Quran source bytes as described in `SIGNING_CURRENT.md` and `QURAN_READING_0818.md`.

No store publication, merge to main, bot deployment, iOS change, new payments or audio is included.
