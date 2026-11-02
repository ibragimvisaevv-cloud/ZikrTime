# ZikrTime 0.8.18 — book reading and visible ayah actions

User request: 2 November 2026. Android version 0.8.18 (30); branch `codex/quran-reader-0814`.
The user reports that 0.8.17 is under RuStore moderation. This work does not replace that submission.

## Reader design

- The Quran section has its own ivory/brass day palette and ink/ivory night palette. Ornament is drawn in the margins and the surah heading; it never becomes part of the Quran text.
- The index is a book contents list. Search, sections and resume controls scroll with the list on narrow screens. Saved reading place is not duplicated when it matches the latest position.
- Continuous reading and translation are explicit modes. The verified Amiri font is retained, including its diacritic-safe leading. No new Arabic font or synthetic calligraphy is introduced.
- Ayah actions have visible entry points. A normal tap selects an ayah; long press remains an optional shortcut. Selection alone does not save a new reading position.
- The action sheet identifies its exact ayah. Notes, bookmarks and copying are explicit actions. Copy offers Arabic, translation, or both and confirms completion.
- Unsaved note edits require a deliberate discard; deleting a note preserves bookmarks. Existing preference keys remain compatible.

Patterns reviewed: [Quran.com Study Mode](https://quran.com/en/product-updates/new-study-mode-on-quran-com), [Greentech bookmarks](https://gtaf.org/blog/bookmark-redesigned-al-quran/), and [Quran for Android](https://android.quran.com/). This is an original responsive reading layout, not a claim of printed mushaf pagination.

## Verification

Pending first 0.8.18 CI run. Do not treat this document as a passed test report until the evidence below is updated.

Use the `[quran-paper]` commit marker to run `.github/workflows/quran-paper.yml`. It checks the original source snapshots, all eight reader locales, focused Quran unit tests, Android lint, debug and signed release builds, the original signing certificate, and source bytes inside the release APK. Emulator journeys exercise changed reader actions, persistence, note dismissal, selection, copying, themes, rotation, large text and automatic progress exclusions.

Do not run the old `[touch-finish]` shortcut for this change. If only test automation is corrected after a successful build, reuse the verified APK rather than rebuilding unchanged Android inputs.

No source update, Quran audio, payment change, bot deployment, iOS change, store submission or merge to main is part of this version.
