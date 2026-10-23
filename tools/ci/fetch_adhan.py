"""Скачивает записи азана с Wikimedia Commons (проверка SHA-1) и строит спектрограммы для сравнения качества."""
import hashlib, json, pathlib, subprocess, sys, urllib.request

UA = {"User-Agent": "ZikrTimeCI/1.0 (https://github.com/ibragimvisaevv-cloud/ZikrTime)"}
CANDIDATES = [
    ("beautiful_adhan.ogg", "https://upload.wikimedia.org/wikipedia/commons/b/b0/Beautiful_adhan.ogg", "a1fa4fd942401922c5c3301816384aae86956522", "CC0"),
    ("adhan_short.ogg", "https://upload.wikimedia.org/wikipedia/commons/e/e7/Adhan.ogg", "b86432f1581e11f898de1dc3402af0d541af110e", "CC0"),
    ("aaqib_azeez.mp3", "https://upload.wikimedia.org/wikipedia/commons/7/7d/The_Adhan_-_Muslim_Call_to_Prayer_-_Aaqib_Azeez.mp3", "971bced4b0bf6067358afc78cac6e9d219226c19", "CC BY-SA 4.0"),
    ("azan_andrewler.ogg", "https://upload.wikimedia.org/wikipedia/commons/8/86/Azan.ogg", "ada13592b01c12b0128af40035ed7a803e08b563", "CC BY-SA 4.0"),
]

out = pathlib.Path(sys.argv[1]); out.mkdir(parents=True, exist_ok=True)
report = []
for name, url, sha1, lic in CANDIDATES:
    dest = out / name
    try:
        with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60) as r:
            data = r.read()
        ok = hashlib.sha1(data).hexdigest() == sha1
        if ok:
            dest.write_bytes(data)
            subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(dest), "-lavfi",
                            "showspectrumpic=s=1200x400:legend=1", str(out / (name + ".png"))], check=False)
            stats = subprocess.run(["ffmpeg", "-hide_banner", "-i", str(dest), "-af", "astats=metadata=0,ebur128=framelog=quiet",
                                    "-f", "null", "-"], capture_output=True, text=True).stderr[-2500:]
            (out / (name + ".stats.txt")).write_text(stats)
        report.append({"file": name, "sha1_ok": ok, "license": lic, "bytes": len(data)})
    except Exception as e:  # noqa: BLE001
        report.append({"file": name, "error": str(e)})
(out / "report.json").write_text(json.dumps(report, indent=1))
print(json.dumps(report, indent=1))
