"""Собирает строки для iPhone-версии из общих файлов tools/i18n/<язык>.py и tools/i18n/ios_extra.py.

Результат: ios/ZikrTime/Resources/<язык>.lproj/Localizable.strings и InfoPlist.strings.
Подстановки Android (%1$s, %1$d, %d) переводятся в формат iOS (%1$@, %1$ld, %ld).
Множественное число: ключ_one / ключ_few / ключ_many / ключ_other.
Массивы: ключ_0, ключ_1, …
Запуск: python3 tools/ios/build_strings.py
"""
import importlib.util
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
I18N = ROOT / "tools/i18n"
OUT = ROOT / "ios/ZikrTime/Resources"
LANGS = ["en", "ru", "uz", "tg", "kk", "ky", "az", "tr"]


def load(name):
    spec = importlib.util.spec_from_file_location(f"ios_{name}", I18N / f"{name}.py")
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def to_ios(text):
    text = re.sub(r"%(\d+)\$s", r"%\1$@", text)
    text = re.sub(r"%(\d+)\$d", r"%\1$ld", text)
    text = re.sub(r"%d", "%ld", text)
    return text


def esc(text):
    return text.replace("\\", "\\\\").replace('"', '\\"').replace("\n", "\\n")


def line(key, value):
    return f'"{key}" = "{esc(value)}";'


def main():
    extra = load("ios_extra").STRINGS
    errors = []
    for lang in LANGS:
        mod = load(lang)
        lines = [f"/* Сгенерировано tools/ios/build_strings.py из tools/i18n/{lang}.py — не править вручную */"]
        for key, text in mod.STRINGS.items():
            lines.append(line(key, to_ios(text)))
        for key, values in extra.items():
            if lang not in values:
                errors.append(f"{lang}: нет {key} в ios_extra")
                continue
            lines.append(line(key, to_ios(values[lang])))
        for key, forms in mod.PLURALS.items():
            other = forms.get("other") or list(forms.values())[-1]
            for q in ["one", "few", "many", "other"]:
                lines.append(line(f"{key}_{q}", to_ios(forms.get(q, other))))
        for key, items in mod.ARRAYS.items():
            for i, item in enumerate(items):
                lines.append(line(f"{key}_{i}", item))
        folder = OUT / f"{lang}.lproj"
        folder.mkdir(parents=True, exist_ok=True)
        (folder / "Localizable.strings").write_text("\n".join(lines) + "\n", encoding="utf-8")
        info = [
            "/* Сгенерировано tools/ios/build_strings.py */",
            line("CFBundleDisplayName", mod.STRINGS["app_name"]),
            line("NSAlarmKitUsageDescription", extra["info_alarm_usage"][lang]),
            line("NSLocationWhenInUseUsageDescription", extra["info_location_usage"][lang]),
        ]
        (folder / "InfoPlist.strings").write_text("\n".join(info) + "\n", encoding="utf-8")
    for e in errors:
        print("ОШИБКА:", e)
    print("iOS-строки:", ", ".join(LANGS), "| ошибок:", len(errors))
    sys.exit(1 if errors else 0)


if __name__ == "__main__":
    main()
