# Morning / evening azkar: editorial source map

Checked 2026-10-30. This is a **selected collection of nine cards per period**, not a claim to contain every transmitted morning/evening remembrance. It replaces the former reuse of six generic tasbih phrases. Six entries legitimately belong to both periods; the opening supplication has day/night wording, and the final entry is period-specific.

The Arabic prayers and Qur'anic verses are classical public-domain texts. Russian and English explanatory translations and transliteration aids were written for this implementation rather than copied wholesale from a modern published translation. They are not an independent scholarly certification or a replacement for the Arabic. Arabic remains the authoritative reading text. No audio or new Qur'an-reader database is introduced.

## Sources and counts

| Card | Period | Readings | Text and basis |
|---|---|---:|---|
| Asking for the good of the day/night | Morning / evening variants | 1 | [Sahih Muslim 2723b](https://sunnah.com/muslim:2723b); explicit expanded daytime wording and night substitution in [Hisn al-Muslim 77](https://sunnah.com/hisn:77). The evening form uses `رَبِّ`, `خَيْرَ مَا فِي`, `وَخَيْرَ مَا بَعْدَهَا` from **2723b**, not a mixture with 2723a. |
| Allahumma bika… | Morning / evening variants | 1 | [Abu Dawud 5068](https://sunnah.com/abudawud:5068), sahih (al-Albani). Both variants end in `وَإِلَيْكَ النُّشُورُ` in this specific narration; its evening version does not include `وبك أصبحنا`. |
| Al-Ikhlas | Both | 3 | [Qur'an 112:1–4](https://quran.com/112); count/period in [Abu Dawud 5082](https://sunnah.com/abudawud:5082), graded hasan by al-Albani on the cited page. |
| Al-Falaq | Both | 3 | [Qur'an 113:1–5](https://quran.com/113); same [5082](https://sunnah.com/abudawud:5082). |
| An-Nas | Both | 3 | [Qur'an 114:1–6](https://quran.com/114); same [5082](https://sunnah.com/abudawud:5082). |
| Sayyid al-Istighfar | Both | 1 | [Sahih al-Bukhari 6306](https://sunnah.com/bukhari:6306), prayer text and day/night recitation. |
| Bismillah protection | Both | 3 | [Abu Dawud 5088](https://sunnah.com/abudawud:5088), graded sahih by al-Albani on the cited page, explicitly three times morning/evening. |
| Subhanallahi wa bihamdih | Both | 100 | [Sahih Muslim 2692](https://sunnah.com/muslim:2692), explicitly morning/evening and one hundred repetitions. |
| Glorification taught to Juwairiya | Morning only | 3 | [Sahih Muslim 2726a](https://sunnah.com/muslim:2726a), morning context and three readings of the four-part formula. |
| Refuge in Allah's perfect words | Evening only | 3 | [At-Tirmidhi 3604b](https://sunnah.com/tirmidhi:3604b), explicitly evening and three readings; sahih (Darussalam), with al-Tirmidhi's hasan evaluation in the Arabic narration. Also compare [Sahih Muslim 2709a](https://sunnah.com/muslim:2709a), whose evening narration does **not** independently specify three. |

The target `1` for the opening, Allahumma bika and istighfar is a single reading in the guided interface; their cited narrations do not set a numbered repetition requirement. `countIsPrescribed = false` marks these cases for a “Read” action rather than a numerical prescription. The interface must not present this as an obligation or claim that reading beyond it is prohibited. Each count for a surah refers to a complete recitation of that surah, not its individual verses. Basmalah is shown separately above the verses and is not assigned an extra repetition.

## Deliberate exclusions / wording decisions

- No weak narration is included just to enlarge the list. For example, [Abu Dawud 5084](https://sunnah.com/abudawud:5084) is graded da'if by al-Albani on the page and was not selected.
- No invented exact minute boundaries are attached to “morning” and “evening”; the user can choose the collection. Religious time-window interpretations are not inferred from device time.
- Reports of rewards/protection are not turned into product guarantees, health promises, or points. The app counts user taps, not worship quality.
- The morning/evening “Allahumma bika…” prayer has different transmitted wordings/attributions. This collection follows the Arabic of Abu Dawud 5068 exactly. In particular, it does not splice the endings from [Tirmidhi 3391](https://sunnah.com/tirmidhi:3391) or [Hisn 78](https://sunnah.com/hisn:78), whose morning/evening presentation differs. This source choice is intentional, not a missing substitution.
- Ordering is editorial and does not claim a mandatory sequence.

## Display / locale contract

`AzkarEntry` supplies Arabic plus original Russian and English explanatory text. `AzkarContent.items("morning" | "evening")` has stable IDs. Other locale interfaces must explicitly identify the English content fallback rather than silently displaying English as if translated into that locale. Transliteration should be labelled a reading aid; it cannot reproduce every Arabic sound exactly. A compact “Source” panel is sufficient: do not restore noisy warning badges beneath every phrase.

`VERSION = 1` identifies the first real corpus. Old pending indices referred to the generic tasbih list and must not be assumed to identify the same text after migration. UI/store integration owns migration and per-card repetition persistence.

## Validation scope

Focused tests protect period-specific content, source/count mapping, stable unique IDs and the presence of full Arabic / meaning / reading aids. They do not constitute theological approval. Native UI, font rendering, large text and persistence are checked by the integrating change.
