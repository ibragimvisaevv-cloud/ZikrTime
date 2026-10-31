# ZikrTime 0.8.17 — direct ayah actions and automatic reading progress

User request: 2026-10-31. Android version code 29; work continues on `codex/quran-reader-0814`, draft PR #4. Previous verified release and live bot: [0.8.16](QURAN_READING_0816.md).

Status: implementation checkpoint; the new APK and gesture journeys are awaiting the focused cloud check. Do not describe 0.8.17 as tested until the final evidence is recorded here.

- A tap selects the exact ayah, including in continuous whole-surah text. Holding its Arabic text opens contextual note, bookmark and copy actions; translation cards support the same selection. Notes show the already selected surah/ayah and only ask for the note text.
- The top bookmark button saves the selected/current visible ayah in one tap, including in full-screen mode. It also updates the explicit reading stop. Removing a bookmark preserves its note. Old note/bookmark preference keys remain compatible.
- Full-screen controls occupy a compact top row so they do not cover the scrolling Arabic text. Source characters and the existing Arabic line spacing are unchanged.
- Reading progress is a local automatic estimate from fully viewed ayahs. The tracker uses original text offsets and actual visible lines, requires consecutive quiet observations and a shared reading-time budget, and never credits unseen gaps. Jumps, fast scrolling, menus, background time, timer pause and statistics opt-out do not create reading credit. Repeated observations remain deduplicated.
- The earlier rule requiring manual verse marking is superseded by the user's request. The manual marking form is removed from the reader. Eight languages describe the new automatic measurement and gestures.

Reference reviewed: [Quran Majeed / Pakdata](https://pakdata.com/products/quran-majeed) (bookmarks, notes and direct reader controls).

Verification is scoped to Quran text/offsets, passive-tracker edge cases, actual gesture/persistence/timer journeys, narrow-screen large text and Android compilation/lint. Bot, shop, stores and iOS are outside this change.
