# ZikrTime 0.8.13 — azkar and motion

## Scope

Preserves the approved Living Horizon design, prayer calculations, notifications, focus rules and existing user settings. No Quran reader or audio download is introduced.

- Nine source-linked cards per morning/evening collection; two opening variants and a distinct final prayer. Six common cards correctly belong to both periods. See [SOURCES.md](SOURCES.md).
- Arabic originals, original Russian/English meaning translations and transliteration. Other interface languages explicitly identify English content fallback. These translations have not received independent scholarly review.
- Source details available on demand. Counts are attached to specific narrations; a single-read button is used where the narration does not specify a numerical target. Completion is optional and never represented as proof of worship.
- Separate per-period reading position and per-item counts survive navigation/process restart. A new `azkar_reading_v2` namespace avoids mapping old generic tasbih indices onto different prayers. The old namespace and overall user progress are retained; the new collection starts at its beginning.
- A manual theme change reveals the new palette in a 520ms wave from the clicked control. One live composition; a temporary old-pixel snapshot only. Reduced motion switches immediately. Offscreen/resize/cancellation releases snapshot references.
- Tasbih uses the existing mosque/tasbih scene in the counter itself, with theme-aware contrast and a short light/scale response. Existing beads, immediate persisted count, goals, haptics and widget updates remain.
- Existing navigation transitions and button press feedback remain; no unnecessary continuous animation is added.

## Verification

Application snapshot: `07747b02238e87a31502174c3c0e8368d1560ea4` (0.8.13, versionCode 25).

- Build/unit/lint run: https://github.com/ibragimvisaevv-cloud/ZikrTime/actions/runs/41075095882
- Final successful emulator run: https://github.com/ibragimvisaevv-cloud/ZikrTime/actions/runs/41076670782
- The emulator run reused the exact APK after verifying no application inputs changed. Only test-harness fixes were made after the application snapshot.
- Generated interface translations checked in eight languages; 63 unit tests passed with no failures/errors/skips. `lintDebug` and `assembleDebug` passed. Lint: 0 errors, 132 warnings, 3 informational findings; this task does not claim to clear existing project warnings.
- Android 15 / API 35 emulator: morning/evening counts and position stay independent, reading resumes after process restart, all nine cards can be visited, completion/restart works, and seeded legacy progress is preserved.
- Both theme directions, five tasbih taps in each theme, reduced-motion behavior, English fallback, and knowledge/focus/shop/more smoke routes passed. Crash log is empty.
- Default 1080x1920 at 420dpi and small 720x1280 at 360dpi (320dp wide), with font scale 1.5, were exercised. Screenshots of reading, source sheet and tasbih were visually reviewed in light/dark and enlarged-text states.
- Evidence artifact `12988765402` includes 18 screenshots, UI hierarchies, `passed.json`, crash log and a 33.6-second actual screen recording. Recording frames confirm the circular theme transition; screenshots alone are not animation evidence.
- No physical-device frame-time benchmark, independent scholarly review, or real installed-user APK upgrade was performed. Legacy-data coverage uses a disposable emulator fixture.

APK SHA-256: `dc59a37cf49c27137fb6c6c5b25489db4cac42f5bf28a6d44f6d814e1894b4c7`.

## Installation limitation

This debug APK is a review build. Its signing certificate differs from the available prior 0.8.12 debug artifact, so it cannot update that APK in place. The certificate of the user's installed version is unknown. Do not remove an existing app or user data to install this build. A compatible update requires the signing key used for that installation; use the recording/screenshots or a separate test device in the meantime.
