"""Собирает строки интерфейса из tools/i18n/<язык>.py в Android-ресурсы (и позже — в каталог строк iOS).

Каждый файл языка определяет:
  STRINGS  — dict ключ → текст (подстановки в стиле Android: %1$s, %1$d)
  PLURALS  — dict ключ → dict(quantity → текст)
  ARRAYS   — dict ключ → list[str]
Английский — язык по умолчанию (values/), остальные — values-<код>/.
Запуск: python3 tools/i18n/build_strings.py
"""
import importlib.util
import pathlib
import re
import sys
from xml.sax.saxutils import escape

ROOT = pathlib.Path(__file__).resolve().parents[2]
RES = ROOT / "app/src/main/res"
LANGS = ["en", "ru", "uz", "tg", "kk", "ky", "az", "tr"]
DEFAULT = "en"


def load(lang):
    path = pathlib.Path(__file__).parent / f"{lang}.py"
    if not path.exists():
        return None
    spec = importlib.util.spec_from_file_location(f"strings_{lang}", path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def android_escape(text):
    s = escape(text)  # & < >
    s = s.replace("\\", "\\\\").replace("'", "\\'").replace('"', '\\"')
    if s.startswith("@") or s.startswith("?"):
        s = "\\" + s
    s = s.replace("\n", "\\n")
    return s


PH = re.compile(r"%(\d+\$)?[sdf]")


def duplicate_keys(lang):
    """Python молча перезаписывает повторный ключ в словаре — ищем такие ошибки явно."""
    import ast
    tree = ast.parse((pathlib.Path(__file__).parent / f"{lang}.py").read_text(encoding="utf-8"))
    for node in tree.body:
        if isinstance(node, ast.Assign) and getattr(node.targets[0], "id", "") == "STRINGS":
            keys = [k.value for k in node.value.keys]
            return sorted({k for k in keys if keys.count(k) > 1})
    return []


def check(lang, mod, base):
    errors = [f"{lang}: ключ {k} повторяется" for k in duplicate_keys(lang)]
    for key, text in base.STRINGS.items():
        if key not in mod.STRINGS:
            errors.append(f"{lang}: нет {key}")
        elif sorted(PH.findall(text)) != sorted(PH.findall(mod.STRINGS[key])):
            errors.append(f"{lang}: {key} — подстановки не совпадают")
    for key in mod.STRINGS:
        if key not in base.STRINGS:
            errors.append(f"{lang}: лишний ключ {key}")
    for key in base.PLURALS:
        if key not in mod.PLURALS:
            errors.append(f"{lang}: нет plurals {key}")
    for key, arr in base.ARRAYS.items():
        if len(mod.ARRAYS.get(key, [])) != len(arr):
            errors.append(f"{lang}: массив {key} другой длины")
    return errors


def write(lang, mod):
    folder = RES / ("values" if lang == DEFAULT else f"values-{lang}")
    folder.mkdir(parents=True, exist_ok=True)
    lines = ['<?xml version="1.0" encoding="utf-8"?>',
             "<!-- Сгенерировано tools/i18n/build_strings.py — правьте tools/i18n/%s.py -->" % lang,
             "<resources>"]
    for key, text in mod.STRINGS.items():
        fmt = ' formatted="false"' if ("%" in text and not PH.search(text)) else ""
        lines.append(f'    <string name="{key}"{fmt}>{android_escape(text)}</string>')
    for key, forms in mod.PLURALS.items():
        lines.append(f'    <plurals name="{key}">')
        for q, text in forms.items():
            lines.append(f'        <item quantity="{q}">{android_escape(text)}</item>')
        lines.append("    </plurals>")
    for key, items in mod.ARRAYS.items():
        lines.append(f'    <string-array name="{key}">')
        for item in items:
            lines.append(f"        <item>{android_escape(item)}</item>")
        lines.append("    </string-array>")
    lines.append("</resources>")
    (folder / "strings.xml").write_text("\n".join(lines) + "\n", encoding="utf-8")


def main():
    base = load(DEFAULT)
    all_errors = []
    built = []
    for lang in LANGS:
        mod = load(lang)
        if mod is None:
            continue
        if lang != DEFAULT:
            all_errors += check(lang, mod, base)
        write(lang, mod)
        built.append(lang)
    for e in all_errors:
        print("ОШИБКА:", e)
    print("Языки:", ", ".join(built), "| строк:", len(base.STRINGS), "| ошибок:", len(all_errors))
    sys.exit(1 if all_errors else 0)


if __name__ == "__main__":
    main()
