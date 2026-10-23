"""Проверяет переводы: в каждом values-*/strings.xml те же ключи, что и в основном, и те же подстановки (%1$s, %d...)."""
import re, sys, pathlib
import xml.etree.ElementTree as ET

res = pathlib.Path("app/src/main/res")
base_file = res / "values" / "strings.xml"
if not base_file.exists():
    print("strings.xml не найден — пропускаю"); sys.exit(0)

PH = re.compile(r"%(\d+\$)?[sdf]")

def load(path):
    root = ET.parse(path).getroot()
    out = {}
    for el in root:
        name = el.get("name")
        if el.get("translatable") == "false":
            continue
        if el.tag == "string":
            out[name] = ("string", "".join(el.itertext()))
        elif el.tag == "plurals":
            out[name] = ("plurals", {i.get("quantity"): "".join(i.itertext()) for i in el})
        elif el.tag == "string-array":
            out[name] = ("array", ["".join(i.itertext()) for i in el])
    return out

base = load(base_file)
errors = []
for d in sorted(res.glob("values-*")):
    f = d / "strings.xml"
    if not f.exists():
        continue
    tr = load(f)
    for key, (kind, val) in base.items():
        if key not in tr:
            errors.append(f"{d.name}: нет ключа {key}")
            continue
        tkind, tval = tr[key]
        if tkind != kind:
            errors.append(f"{d.name}: {key} другого типа")
            continue
        if kind == "string" and sorted(PH.findall(val)) != sorted(PH.findall(tval)):
            errors.append(f"{d.name}: {key} — подстановки не совпадают")
        if kind == "array" and len(val) != len(tval):
            errors.append(f"{d.name}: {key} — другое число элементов")
        if kind == "plurals":
            base_ph = sorted(set(m for v in val.values() for m in PH.findall(v)))
            for q, v in tval.items():
                if sorted(set(PH.findall(v))) != base_ph:
                    errors.append(f"{d.name}: {key}/{q} — подстановки не совпадают")
    for key in tr:
        if key not in base:
            errors.append(f"{d.name}: лишний ключ {key}")
for e in errors:
    print("ОШИБКА:", e)
print(f"Проверено языков: {len(list(res.glob('values-*/strings.xml')))}, ключей: {len(base)}, ошибок: {len(errors)}")
sys.exit(1 if errors else 0)
