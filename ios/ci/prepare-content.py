"""Prepare localized text, artwork and daily remembrances from the Android release sources."""
import json
import re
import shutil
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
ANDROID = ROOT / "app/src/main"
IOS = ROOT / "ios/ZikrTime/Resources"


def ios_text(text):
    text = text.replace("\\'", "'").replace('\\"', '"').replace("\\n", "\n")
    text = re.sub(r"%(\d+\$)?s", lambda m: "%" + (m[1] or "") + "@", text)
    return re.sub(r"%(\d+\$)?d", lambda m: "%" + (m[1] or "") + "ld", text)


def supplement_strings():
    for lang in ("en", "ru", "uz", "tg", "kk", "ky", "az", "tr"):
        values = ANDROID / "res" / ("values" if lang == "en" else f"values-{lang}")
        entries = {}
        for xml in sorted(values.glob("*.xml")):
            for node in ET.parse(xml).getroot().findall("string"):
                entries[node.attrib["name"]] = ios_text("".join(node.itertext()))
        with (IOS / f"{lang}.lproj" / "Android.strings").open("w", encoding="utf8") as dest:
            for key, value in sorted(entries.items()):
                quoted = json.dumps(value, ensure_ascii=False)
                dest.write(f'"{key}" = {quoted};\n')


def azkar():
    source = (ANDROID / "java/com/zikrtime/app/data/AzkarContent.kt").read_text("utf8")
    entries = {}
    for name, kind, body in re.findall(r"private val (\w+) = (AzkarEntry|\w+\.copy)\(\s*(.*?)\n    \)", source, re.S):
        fields = {} if kind == "AzkarEntry" else entries[kind.split(".")[0]].copy()
        for key, literal in re.findall(r'^\s*(\w+) = ("(?:\\.|[^"\\])*"|\d+|true|false),\s*$', body, re.M):
            fields[key] = json.loads(literal)
        assert all(fields.get(k) for k in ("id","arabic","titleRu","meaningRu","sourceUrl","repetitions")), name
        entries[name] = fields
    shared = re.search(r"private val shared = listOf\(([^)]+)\)", source)[1].split(", ")
    result = {}
    for period in ("morning", "evening"):
        group, last = re.search(rf"private val {period} = listOf\(([^)]+)\) \+ shared \+ (\w+)", source).groups()
        result[period] = [entries[k] for k in [*group.split(", "), *shared, last]]
    (IOS / "daily-azkar.json").write_text(json.dumps(result, ensure_ascii=False), "utf8")


if __name__ == "__main__":
    supplement_strings()
    azkar()
    for image in ("horizon_portrait_day", "horizon_portrait_night"):
        shutil.copyfile(ANDROID / f"res/drawable-nodpi/{image}.webp", IOS / f"{image}.webp")
