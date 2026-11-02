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

Verified on 2 November 2026. Android source: `6275b1a485d65210ad44932a1a5e15be67d6db9d`.

- [Build run 41493288209](https://github.com/ibragimvisaevv-cloud/ZikrTime/actions/runs/41493288209): 29 focused Quran tests, lint with no errors, debug and signed release builds, original certificate and exact Quran assets inside the release APK passed. The first UI attempt stopped when Android's clipboard preview intercepted an automation tap; the crash log was empty.
- [UI run 41495690961](https://github.com/ibragimvisaevv-cloud/ZikrTime/actions/runs/41495690961) passed all eight scenario groups on the same APK. Verification commit: `80a3d9d3d3f3bb129f44bc3ba34fed0e9450158f`. The guard verified unchanged Android inputs and both APK hashes; no second build was performed.
- Passed: exact-ayah actions and Back; three exact clipboard variants; note discard/save/confirmed deletion preserving bookmarks; last-ayah mode switch and restart; dark reading/translation/full-screen/rotation; 320 dp with 150% text; automatic progress exclusions for menus/background/jumps; English note controls. Crash log is empty.
- Visual review covered day index/actions, night reading/translation, landscape, 320 dp reading/note controls and unsaved-note confirmation. This is emulator evidence, not a physical-phone test or independent religious review.
- Evidence branch: `ci-reader-0818-41495690961`. Signed candidate artifact: `13168315791`; UI evidence artifact: `13167102952`.

| Candidate | Value |
| --- | --- |
| File | `ZikrTime-0.8.18-preview.apk` |
| Size | 61,170,479 bytes |
| SHA-256 | `ad17abce7a2d1d9a00299fd91f4463c9ff56ebd879427d977ac7b1dd79a91b3c` |
| Certificate SHA-256 | `7ae126783c9417f9feeb15e05498eec466f97705f8b1831b4393754108502ccf` |
| Debug APK SHA-256 used for emulator checks | `df4029e4068360986225ad04575fc8f31c20add71aec0edca81262dac30d8f02` |

The signed candidate was downloaded and independently checked by size and hash. APK and four original emulator screenshots were provided as separate downloadable files.

Use the `[quran-paper]` commit marker to run `.github/workflows/quran-paper.yml`. It checks the original source snapshots, all eight reader locales, focused Quran unit tests, Android lint, debug and signed release builds, the original signing certificate, and source bytes inside the release APK. Emulator journeys exercise changed reader actions, persistence, note dismissal, selection, copying, themes, rotation, large text and automatic progress exclusions.

Do not run the old `[touch-finish]` shortcut for this change. If only test automation is corrected after a successful build, reuse the verified APK rather than rebuilding unchanged Android inputs.

No source update, Quran audio, payment change, bot deployment, iOS change, store submission or merge to main is part of this version.
