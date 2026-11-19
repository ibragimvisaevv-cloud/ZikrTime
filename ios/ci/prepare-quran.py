"""Stage the same Quran text, audio catalogues and measured timings used by Android."""
import hashlib
import json
from pathlib import Path
import shutil
import tempfile
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[2]
GENERATED = ROOT / "ios/Generated"
ASSETS = ROOT / "app/src/main/assets"
VOICES = ("mishary-alafasy", "yasser-dosari", "mahmoud-husary", "muhammad-minshawi")


def prepare():
    GENERATED.mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(dir=GENERATED) as temp:
        stage = Path(temp) / "QuranData"
        shutil.copytree(ASSETS / "quran", stage / "quran")
        shutil.copytree(ASSETS / "quran_audio", stage / "quran_audio")
        text = stage / "quran"
        manifest = (text / "manifest.json").read_bytes()
        assert hashlib.sha256(manifest).hexdigest() == "0ad3cd6003fda2bcc8ea3dbde94925e67a1594c2b58c01ab62df98dfa17f6b84"
        metadata = json.loads(manifest)
        for filename, digest in metadata["files"].items():
            assert hashlib.sha256((text / filename).read_bytes()).hexdigest() == digest, filename
        for reciter in VOICES[1:]:
            evidence = json.loads((ROOT / f"tools/quran/source-evidence/55/{reciter}.json").read_bytes())
            for pack in evidence["packs"]:
                with ZipFile(ROOT / "app/recitation-packs" / pack) as archive:
                    for entry in archive.infolist():
                        if entry.is_dir():
                            continue
                        parts = Path(entry.filename).parts
                        assert parts[:2] == ("quran_audio", reciter) and ".." not in parts
                        target = stage.joinpath(*parts)
                        assert not target.exists(), entry.filename
                        target.parent.mkdir(exist_ok=True, parents=True)
                        target.write_bytes(archive.read(entry))
        for voice in VOICES:
            base = stage / "quran_audio"
            catalog = base / "alafasy.json" if voice == VOICES[0] else base / voice / "catalog.json"
            tracks = json.loads(catalog.read_bytes())["tracks"]
            assert [row["surah_id"] for row in tracks] == list(range(1, 115))
            for row in tracks:
                assert row["url"] == f"https://cdn.mp3quran.net/audio/{voice}/r1/{row['surah_id']:03d}.mp3"
        dest = GENERATED / "QuranData"
        if dest.exists():
            shutil.rmtree(dest)
        stage.rename(dest)
    print("Prepared 114 surahs, 6,236 ayahs and four reciter catalogues for iOS")


if __name__ == "__main__":
    prepare()
