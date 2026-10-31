# Quran sources and import audit

This reader contains Arabic text and a Russian translation of meanings. Runtime
source verification must succeed before any verse is shown. The UI preserves
Arabic strings exactly, including all combining marks. Rendering still requires
visual verification with the shipped Arabic font; byte equality is not a claim
that a religious editor has independently reviewed the application.

## Arabic

- Publisher: Tanzil Project, Uthmani text, version 1.1 (Hafs).
- Official download: <https://tanzil.net/download/>.
- Downloaded bytes: `app/src/main/assets/quran/sources/quran-uthmani.txt`.
- Direct URL: <https://tanzil.net/pub/download/index.php?quranType=uthmani&outType=txt-2&agree=true>.
- SHA-256: `bf4f57b968d03f4131c070b1e285da9be0e0a108a21c910e872801ca273312c8`.
- Size: 1,370,878 bytes; 114 surahs; 6,236 indexed verses.
- Source license: <https://tanzil.net/docs/Text_License>. The original complete
  copyright/terms notice is retained verbatim in the source file. CC BY 3.0 with
  the publisher's explicit requirement not to change the text, source attribution,
  and link to <https://tanzil.net/>.
- The source includes the basmala in the first-verse field for surahs other than
  surah 9. The reader must not strip this prefix, add a second copy, or count it
  as an additional numbered verse. Surah 1 already numbers its basmala as 1:1.
- No NFC/NFKC normalization, character substitution, whitespace trimming or
  punctuation repair is allowed on the displayed Arabic field.

## Russian translation of meanings

- Publisher: QuranEnc, Rowwad Translation Center, edition key `russian_rwwad`.
- Version: **1.0.1**, updated **29 March 2026**, retrieved **30 October 2026**.
- Edition page: <https://quranenc.com/en/browse/russian_rwwad/1>.
- Attribution from the publisher: Translated by the team of the Rowwad Translation
  Center, in cooperation with the Rabwah Dawah Association, the Islamic Content
  Service Association in Languages, and the IslamHouse.com website.
- Official API documentation: <https://quranenc.com/en/home/api>.
- Version metadata: <https://quranenc.com/en/home>. The index API did not list this
  edition at retrieval time; the official website's edition card does identify
  the version and update date. A verbatim edition-card excerpt is retained in
  `tools/quran/source-evidence/quranenc-russian-rwwad.html`; `provenance.json`
  records the URL and SHA-256 of both the full downloaded page and the excerpt.
  The full HTML audit response stays outside the APK under ignored `.work/`.
- Chapter API: `https://quranenc.com/api/v1/translation/sura/russian_rwwad/{1..114}`.
- Every raw JSON response is retained; translations and footnotes are exposed
  without rewriting. All **114 surahs / 6,236 verses** are present. **73 verses**
  have footnotes. The downloaded translation and footnotes contain no HTML tags
  or entities, so the reader displays them as plain text without conversion.
  Edition version, complete attribution and checksums are in `manifest.json`.
- Publisher terms: <https://quranenc.com/en/home>. Reproduction requires unchanged
  contents, publisher/source credit, version and transcript information retained,
  reporting translation notes to the publisher, keeping up with source updates,
  and no inappropriate advertisements alongside the translation.

## Navigation labels

Arabic chapter names, Latin names and verse counts come from Tanzil's
<https://tanzil.net/res/text/metadata/quran-data.xml>. Russian chapter names in
`tools/quran/surah_names_ru.json` are editorial navigation labels; they are not
part of the verse text or translation. They let readers search familiar names
such as «Аль-Фатиха» or «Я Син». No verse transcription or Quran audio is bundled.

## Reproducibility

`python tools/quran/import_quran.py --download` downloads Arabic, metadata and
translation source files without modifying existing snapshots. `--build` validates every surah/ayah key,
generates a SHA-256 manifest and pins that manifest in `QuranContent.kt`.
The application checks the manifest and every consumed source file. Missing,
modified, duplicate, malformed and incomplete content raises an error. Failed
loads are not cached. The importer does not certify religious correctness or
grant any rights that were not provided by the respective publishers.

Before publishing a source update, re-download into a clean staging directory,
compare the official edition version and raw source changes, review the diff,
then rebuild the manifest and update the attribution notice/UI. Do not silently
replace a snapshot in an installed application. The importer pins the reviewed
Arabic edition independently and rejects any changed Arabic bytes.

Verification: deterministic importer validation covers all keys and nonempty
Russian fields; JVM tests verify every displayed Arabic, translation and footnote
against the corresponding raw snapshot and reject missing or changed files.
The runtime performs the same source-hash and coverage checks offline before
returning a book. JVM execution is part of the Android CI workflow.
