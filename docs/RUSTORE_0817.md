# RuStore 0.8.17 preparation — 2026-10-31

The user supplied RuStore Console screenshots confirming **0.8.2 (14)** is published to 100% of the audience, and requested preparation of the next upload. Do not confuse this with the earlier assumption that 0.8.3 had been submitted. Application source remains the already verified [0.8.17](QURAN_READING_0817.md), Android package `com.zikrtime.app`, versionCode 29.

## Current result: release prepared, original signing blocked

[Preparation run 41240887366](https://github.com/ibragimvisaevv-cloud/ZikrTime/actions/runs/41240887366) completed its signing probe, release build and store screenshots. Source: `31560a5f285d9983df6cf3db852526f58cd59898`.

- The original key cannot be decrypted. The repository Actions secret `RELEASE_KEY_PASSWORD` has the wrong secret type (GitHub token). No secret value or decrypted key was exposed or uploaded. Expected original certificate SHA-256: `7ae126783c9417f9feeb15e05498eec466f97705f8b1831b4393754108502ccf`.
- Release assembly and **release** lint passed (zero errors), with resource/code shrinking enabled. Package, 0.8.17/29, minSdk 26, targetSdk 35, `debuggable=false`, ARM64/ARMv7 and exact Quran asset bytes were verified. The Android inputs match verified reader source `22bed67f6863addaa1eed71f4bd563f1b6d00ad4`; previously passed unit/UI tests were not intentionally rerun. A legacy general workflow triggered by the first preparation commit was cancelled, and its condition now excludes store packaging.
- Unsigned APK: **61,121,219 bytes**, SHA-256 `7b18fd180b4af31eb2270bc6e60555872cd26d38d0dd7f2d79022effea294599`. This is not a store-uploadable or installable delivery. Keep it as the immutable signing input.
- Build artifact `13057178682`, name `ZikrTime-0.8.17-release-build`; ZIP SHA-256 `59e19abccb6dcccceaea5d3e00517c54516ceb3ab3a6315e0237e0d2d2b82b2f`. APK path: `ci-store/ZikrTime-0.8.17-UNSIGNED.apk`. The same artifact includes release reports and R8 mappings.
- Signing probe artifact `13057825151`, `RuStore-signing-check`, records `ready=false`, `reason=wrong-secret-type`.

## Store materials

Copy-ready Russian texts are in `store/rustore/0.8.17`: changes from published 0.8.2, short/full descriptions, reviewer notes, data/privacy additions and upload instructions. A full privacy HTML candidate is produced from the existing policy template with the contact shown in the user's published listing. It is a prepared revision, not a deployed privacy page.

Media artifact `13057328446` (`ZikrTime-0.8.17-store-media`), ZIP SHA-256 `32715092901d162bb76a6ba6a1f32819b7f2a10154d6eb91ddb80c583eca42b1`, contains seven unaltered 1080×1920 Android 15 captures from the checksum-pinned checked debug APK (no new debug build). Crash log is empty. All were visually reviewed. The delivery selects six: prayer/home, Quran translation, full-screen Quran, reading statistics, tasbih and dark Quran. The Focus capture shows permission not granted and is excluded from the marketing selection. Each selected image is below 3 MB. Existing 512×512 RGB store icon is retained.

The APK signature remains the blocking step. A physical upgrade from the actual RuStore-installed APK has not been performed. No new app signing key, store submission/publication, bot redeployment or main-branch merge was performed.

## Resume without rebuilding

1. User restores the **original Android keystore password** in repository Actions secret `RELEASE_KEY_PASSWORD`, via GitHub Settings → Secrets and variables → Actions. Do not ask them to put the password into chat or replace the original certificate.
2. Trigger `.github/workflows/rustore-sign-0817.yml` on `codex/quran-reader-0814` with a `[rustore-sign]` commit. This reuses artifact `13057178682`; it verifies the original build provenance, unchanged Android inputs, unsigned APK checksum, original certificate and unchanged ZIP payload after signing. It never calls Gradle.
3. Verify the successful signed APK artifact/report, save the actual file for the user, and record its new SHA-256/size. Do not hand out the debug or unsigned APK as a store update.
4. Publish the prepared privacy revision at the existing policy URL, test a normal update from the RuStore version and its retained settings, then proceed with the actual store upload/moderation as authorized. The current request only resulted in preparation; do not claim the console was updated.

Official requirements checked: [version update](https://www.rustore.ru/help/developers/publishing-and-verifying-apps/app-publication/new-version-app) and [APK, descriptions and media](https://www.rustore.ru/help/developers/publishing-and-verifying-apps/app-publication). An update needs the same package/signature and a higher version code. The prepared short/full descriptions fit 80/4000 characters; the six portrait screenshots satisfy the documented count, dimensions and file-size limits.
