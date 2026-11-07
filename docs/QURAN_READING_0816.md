# ZikrTime 0.8.16 — reader, local reading statistics and private feedback

Continuation of [0.8.15](QURAN_READING_0815.md). Android version code 28; branch `codex/quran-reader-0814`, draft PR #4. Android source: `98eb829ad3c8a3350b1e9b698c779fbf207501a3`.

## Delivered behavior

- A full-screen Quran reader with manual portrait/landscape rotation, text sizing, optional keep-awake, whole-surah reading and the existing verses/translation mode.
- An explicit stop at an exact ayah, a personal note, a resume card and a notes list. Existing bookmarks are preserved. On short surahs the saved exact stop, rather than the first visible backfilled block, is used when reopening the note editor.
- A 1–180 minute reading timer with pause/resume and an end dialog. It counts only while the reader is active and visible; menus, background time and screen-off time are excluded. State survives process restart.
- Local reading statistics: time, daily goal, 7/30/90-day periods, manually marked unique ayahs, active days and completed surahs. Scrolling alone does not mark reading; overlapping marks are deduplicated. Statistics obey the existing personal-progress switch. Clearing personal progress preserves the reading stop, notes and bookmarks.
- Suggestions, application errors and text corrections in the existing Telegram bot: description, up to 5 photos, preview, private history, owner statuses and replies. Only the author and configured owner can access a ticket; other shop administrators cannot. Photo IDs and tickets remain in private storage, outside the public catalog.
- The existing support card is nearer the top of More, with a neutral explanation and the existing support destination. No additional paid features. App feedback opens the existing configured bot with app/version/reader context.
- New Android strings cover the same eight languages as the application. Quran text, source checksums and 2.25em Arabic line spacing remain unchanged. Quran audio and transliteration were not added.

## Verification and file

The Android build and source/resource checks ran in [run 41126993813](https://github.com/ibragimvisaevv-cloud/ZikrTime/actions/runs/41126993813): **86 unit tests passed**, zero failures/errors/skips; lint has **zero errors**. This build's initial reader/rotation/restart journeys passed; its workflow later stopped on a UI-harness gesture, without an app crash.

[Final UI run 41129691398](https://github.com/ibragimvisaevv-cloud/ZikrTime/actions/runs/41129691398) completed successfully on Android 15 with the same checksum-pinned APK and unchanged Android build inputs. All **10 journey checkpoints** passed. Five completed checkpoints were reused from [run 41128524222](https://github.com/ibragimvisaevv-cloud/ZikrTime/actions/runs/41128524222), which also pins the original build. The remaining checks cover timer completion/continuation, clearing statistics while preserving notes/bookmarks, Al-Baqarah's last ayah and invalid bounds at 320dp/150% font, support/feedback navigation entries, and the English last surah. Crash log is empty. The earlier checkpoints cover exact notes, both rotation directions, process restart, deduplicated marking, excluding menu time, background/screen-off/pause handling and statistics opt-out.

Actual screenshots of light full-screen reading, landscape, statistics, timer completion and dark large-text reading were visually inspected. Support/feedback entries were checked by UI semantics; the captured support screenshot did not show the complete card. Evidence is in branches `ci-reader-0816-41126993813`, `ci-reader-0816-41128524222` and `ci-reader-0816-41129691398`. No physical-phone or iOS test is implied.

Ready file: `ZikrTime-0.8.16-test.apk` (debug), **101913630 bytes**.

SHA-256: `c8dc1028f6bbbf4d520ec06ab0cf5d077a9587c44ae3d5328c3082151e8dd3d2`.

Raw APK artifact: [13012532970](https://github.com/ibragimvisaevv-cloud/ZikrTime/actions/runs/41129691398/artifacts/13012532970), named `ZikrTime-0.8.16-test.apk`. It was uploaded with `archive: false`; its artifact digest and size equal the APK digest and size above. The REST download endpoint was independently checked by [run 41130973660](https://github.com/ibragimvisaevv-cloud/ZikrTime/actions/runs/41130973660) for the same SHA-256/size and APK entries `AndroidManifest.xml` and `classes.dex`, without rebuilding.

Full reports/diagnostic archive: artifact `13011748902`, `ZikrTime-0.8.16-check`; APK inside `app/build/outputs/apk/debug/app-debug.apk`. Artifact expiry is 2027-01-28. Reuse the verified file for transfers.

Physical-phone verification and debug-signing compatibility with an installed copy remain unverified. Do not instruct the user to uninstall or erase data as a workaround for a signing mismatch.

## Live feedback bot

[@ZeekrTime_Bot](https://t.me/ZeekrTime_Bot?start=feedback) is deployed by [run 41125275087](https://github.com/ibragimvisaevv-cloud/ZikrTime/actions/runs/41125275087), source `c5f454a3af557cb622bd43685933a1dfadf88cd3` on `codex/feedback-0816`.

Eight new feedback tests and eight place-correction tests passed, along with the existing bot/API checks. The live authenticated probe confirmed `feedback_v1`, configured owner and private database; an unauthenticated probe returned HTTP 403. No synthetic messages were sent to Telegram users. Moderation routing and attachments were checked with test doubles; the live read-only probe verified configuration and access.

Stable webhook: `https://shop-bot-rho.vercel.app/api/webhook`. Existing private storage and cloud credentials were reused. No key rotation, public catalog migration, mini-app redeployment or webhook queue reset. Scoped deployment workflow: `.github/workflows/feedback-bot.yml`, branch `codex/feedback-0816`, explicit `[feedback-deploy]` commit marker.

## Continuity and limits-saving rules

The concise continuation instructions are in the repository root [AGENTS.md](../AGENTS.md).

The user wants development, builds and the bot to work with the PC switched off. Source and checkpoints live in GitHub; builds/emulator checks run in GitHub Actions; the bot runs on its existing cloud host. A chat appearing on a phone does not by itself move a local execution environment. This record preserves the verified project state, not an invented verbatim import of another chat.

Read this note, the 0.8.15 note and only the files needed for the next change. Batch independent reads, keep updates concise in Russian, and do not repeatedly request confirmation for work already authorized. Use targeted tests; do not repeat previously successful broad checks without a code change or unresolved failure. Reuse a checksum-verified APK for delivery and UI-harness-only fixes. Do not rebuild solely to transfer a file. Do not add paid functions; existing paid shop promotion and voluntary support remain.

For this release, the UI recheck pins the original build run/artifact and checksum, verifies unchanged Android build inputs and all unit/lint reports, and imports only already completed journeys from that same APK. This keeps verification honest without rebuilding or replaying the passed initial scenarios.

Future changes should remain in the working branch/draft PR until publication is requested. Store publishing and iOS were not performed.
