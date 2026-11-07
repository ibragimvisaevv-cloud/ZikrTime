# ZikrTime 0.8.17 — direct ayah actions and automatic reading progress

User request: 2026-10-31. Android version code 29; work continues on `codex/quran-reader-0814`, draft PR #4. Previous verified release and live bot: [0.8.16](QURAN_READING_0816.md).

Status: verified Android test APK. Focused tests, lint, assembly and the remaining Android 15 journeys passed in [run 41209564777](https://github.com/ibragimvisaevv-cloud/ZikrTime/actions/runs/41209564777). The large-font index hint now scrolls with the list so it cannot consume the list's viewport.

- A tap selects the exact ayah, including in continuous whole-surah text. Holding its Arabic text opens contextual note, bookmark and copy actions; translation cards support the same selection. Notes show the already selected surah/ayah and only ask for the note text.
- The top bookmark button saves the selected/current visible ayah in one tap, including in full-screen mode. It also updates the explicit reading stop. Removing a bookmark preserves its note. Old note/bookmark preference keys remain compatible.
- Full-screen controls occupy a compact top row so they do not cover the scrolling Arabic text. Source characters and the existing Arabic line spacing are unchanged.
- Reading progress is a local automatic estimate from fully viewed ayahs. The tracker uses original text offsets and actual visible lines, requires consecutive quiet observations and a shared reading-time budget, and never credits unseen gaps. Jumps, fast scrolling, menus, background time, timer pause and statistics opt-out do not create reading credit. Repeated observations remain deduplicated.
- The earlier rule requiring manual verse marking is superseded by the user's request. The manual marking form is removed from the reader. Eight languages describe the new automatic measurement and gestures.

Reference reviewed: [Quran Majeed / Pakdata](https://pakdata.com/products/quran-majeed) (bookmarks, notes and direct reader controls).

Verification is scoped to Quran text/offsets, passive-tracker edge cases, actual gesture/persistence/timer journeys, narrow-screen large text and Android compilation/lint. Bot, shop, stores and iOS are outside this change.

## Verified delivery

| Item | Value |
| --- | --- |
| File | `ZikrTime-0.8.17-test.apk` |
| Android source | `22bed67f6863addaa1eed71f4bd563f1b6d00ad4` |
| Size | `101938800` bytes |
| SHA-256 | `d1048c731153662808682401cfe48dc9e4638f5216bf0656ff7d4efacaefdb44` |
| Raw APK artifact | `13045308679` in run `41209564777` |
| Diagnostic ZIP artifact | `13045383585` in the same run |
| Evidence branch | `ci-reader-0817-41209564777` |

The raw artifact contains APK bytes directly (`archive: false`). Do not unzip it as if it contained another APK. If the raw artifact is unavailable, extract `app/build/outputs/apk/debug/app-debug.apk` from the diagnostic ZIP, allowing for a common directory prefix, and verify size and SHA-256 before handing it over.

## Verification and reuse

- 29 focused Quran unit tests passed. They cover every original Arabic character/ayah marker mapping across all 114 surahs, exact source preservation, statistics/timer behavior and eight passive-tracker edge cases. Android lint and APK assembly passed. New/changed reader resources have matching keys and format arguments in all eight languages. Crash log is empty.
- Five UI journeys passed on the first 0.8.17 APK in [run 41207824100](https://github.com/ibragimvisaevv-cloud/ZikrTime/actions/runs/41207824100): exact long-press notes without numeric fields; one-tap bookmark, rotation, restart and preservation of old notes; automatic short-surah tracking/deduplication/menu/background/opt-out; complete coverage of a long ayah without crediting jumped-over ayahs; exact note selection in verse mode.
- That first run then found the narrow-screen index issue. Its source was `617c6a3a324bfd3a302882fc1d462ceb1c8c1091`, APK SHA-256 `2094067e6625cf9ef71e56406ba9b7d100ccd5ae521356e00804e2015c3b99fa`, evidence commit `e29488951426b588d0cc9c0c591c7a424494accc`. The five passes are inherited evidence from that earlier APK, not repeated runs on the final APK.
- `verify_touch_continuation.py` verified that the final production diff is exactly the move of the index hint into its scrolling list; reader, tracker, resources and build inputs remain unchanged. The evidence file `inherited-touch-verification.json` records both sources and the five prior journeys. The final APK passed the remaining three journeys: full-screen gestures/note controls at 320 dp and 150% font in dark mode, timer completion/continuation, and an English contextual note.
- Screenshots were visually reviewed for normal note/full-screen/statistics views and narrow-screen dark full-screen/note controls. These are emulator checks with synthetic local data, not a physical-device test or proof of compatibility with every existing APK signing key. No application ID change, data deletion, store publication or PR merge was performed.

For a future reader change use `[reader-touch]` to run the focused workflow. `[touch-finish]` is only for this exact guarded index correction. Do not reuse it after other Android changes. A file handoff or documentation-only update does not need another build. Keep the previous bot deployment and broader 0.8.16 verification unchanged; follow the economy rules in `AGENTS.md`.
