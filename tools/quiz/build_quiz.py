"""Собирает вопросы викторины из tools/quiz/<язык>.py в app/src/main/assets/quiz/<язык>.json.

Эталон — ru.py: из него берутся порядок, id, темы и арабский текст.
В других языках вопрос записывается как (id, тема, вопрос, варианты или None, справка):
None вместо вариантов — взять варианты из эталона (для арабских аятов).
Запуск: python3 tools/quiz/build_quiz.py
"""
import argparse
from collections import Counter
import importlib.util
import json
import pathlib
import sys
from urllib.parse import urlparse

HERE = pathlib.Path(__file__).parent
OUT = HERE.parents[1] / "app/src/main/assets/quiz"
LANGS = ["ru", "en", "uz", "tg", "kk", "ky", "az", "tr"]
MASTER = "ru"


def load(lang):
    path = HERE / f"{lang}.py"
    if not path.exists():
        return None
    spec = importlib.util.spec_from_file_location(f"quiz_{lang}", path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def level_questions(categories):
    """Validate complete authored banks before touching any generated asset."""
    result = []
    seen = set()
    for level, name in enumerate(["beginner", "intermediate", "advanced"], 1):
        path = HERE / "levels" / f"{name}.json"
        bank = json.loads(path.read_text(encoding="utf-8"))
        if len(bank) < 40:
            raise ValueError(f"{name}: each level needs at least 40 new questions")
        for item in bank:
            qid = item["id"]
            if qid in seen or not qid.startswith(f"l{level}_"):
                raise ValueError(f"Duplicate or invalid new question ID: {qid}")
            seen.add(qid)
            if item["difficulty"] != level or item["cat"] not in categories:
                raise ValueError(f"{qid}: invalid difficulty/category")
            if set(item["locales"]) != set(LANGS):
                raise ValueError(f"{qid}: all eight authored translations are required")
            url = urlparse(item["url"])
            if url.scheme != "https" or url.hostname not in {"quranenc.com", "kuran.diyanet.gov.tr"} or url.username:
                raise ValueError(f"{qid}: expected a verified institutional source URL")
            if not item.get("reference") or not item.get("sourceVerses"):
                raise ValueError(f"{qid}: missing source references")
            for reference in item["sourceVerses"]:
                surah, ayah = map(int, reference.split(":"))
                source = HERE.parents[1] / f"app/src/main/assets/quran/sources/russian_rwwad/{surah}.json"
                verses = json.loads(source.read_text())["result"]
                if not any(int(v["aya"]) == ayah for v in verses):
                    raise ValueError(f"{qid}: verse not found: {reference}")
            for lang, localized in item["locales"].items():
                options = localized["options"]
                if len(options) != 4 or len({o.strip().casefold() for o in options}) != 4 or any(not o.strip() for o in options):
                    raise ValueError(f"{lang}/{qid}: expected four distinct non-empty options")
                if not localized["q"].strip() or len(localized["info"].strip()) < 20:
                    raise ValueError(f"{lang}/{qid}: question and explanation are required")
            result.append(item)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="verify generated assets without writing them")
    args = parser.parse_args()
    master = load(MASTER)
    m_by_id = {q[0]: q for q in master.QUESTIONS}
    metadata = json.loads((HERE / "source_metadata.json").read_text(encoding="utf-8"))
    if set(metadata) != set(m_by_id):
        raise ValueError("Source metadata IDs must match the bank; never reuse another question ID")
    levels = level_questions(master.CATEGORIES)
    difficulty = json.loads((HERE / "legacy_difficulty.json").read_text())
    if set(difficulty) != set(m_by_id) or any(type(x) is not int or x not in (1, 2, 3) for x in difficulty.values()):
        raise ValueError("Legacy difficulty must classify every existing stable question ID")
    if set(m_by_id) & {q["id"] for q in levels}:
        raise ValueError("New questions cannot reuse a legacy learning identity")
    errors = []
    outputs = {}
    OUT.mkdir(parents=True, exist_ok=True)
    built = []
    for lang in LANGS:
        mod = load(lang)
        if mod is None:
            errors.append(f"{lang}: missing locale module")
            continue
        if set(mod.CATEGORIES) != set(master.CATEGORIES):
            errors.append(f"{lang}: другие темы")
        ids = [q[0] for q in mod.QUESTIONS]
        if ids != [q[0] for q in master.QUESTIONS]:
            missing = set(m_by_id) - set(ids)
            extra = set(ids) - set(m_by_id)
            errors.append(f"{lang}: порядок/набор вопросов не совпадает (нет: {sorted(missing)}, лишние: {sorted(extra)})")
        questions = []
        for q in mod.QUESTIONS:
            qid, cat, text, options, info = q[0], q[1], q[2], q[3], q[4]
            m = m_by_id.get(qid)
            if m is None:
                continue
            if cat != m[1]:
                errors.append(f"{lang}/{qid}: тема {cat} вместо {m[1]}")
            if options is None:
                options = m[3]
            if len(options) != 4 or len(set(options)) != 4:
                errors.append(f"{lang}/{qid}: нужно 4 разных варианта")
            if not info or len(info) < 20:
                errors.append(f"{lang}/{qid}: нет справки")
            item = {"id": qid, "cat": cat, "q": text, "options": options, "answer": 0, "info": info}
            meta = metadata[qid]
            item["contentVersion"] = meta["contentVersion"]
            item["difficulty"] = difficulty[qid]
            source = {"translationVersion": meta.get("translationVersion", {}).get(lang, 1)}
            for key in ("reference", "translation", "url", "originalReview", "translationReview"):
                if key not in meta:
                    continue
                value = meta[key]
                source[key] = value.get(lang) if isinstance(value, dict) and lang in value else value
            item["source"] = source
            if m[5]:
                item["arabic"] = m[5]
            questions.append(item)
        for item in levels:
            local = item["locales"][lang]
            questions.append({
                "id": item["id"], "cat": item["cat"], "difficulty": item["difficulty"],
                "q": local["q"], "options": local["options"], "answer": 0, "info": local["info"],
                "contentVersion": item.get("contentVersion", 1),
                "source": {"reference": item["reference"], "url": item["url"],
                           "translationVersion": 1},
            })
        texts = [q["q"].strip().casefold() for q in questions]
        if len(set(texts)) != len(texts):
            # Existing ayah prompts intentionally repeat their title; the Arabic prompt differs.
            identities = [(q["q"].strip().casefold(), q.get("arabic")) for q in questions]
            if len(set(identities)) != len(identities):
                errors.append(f"{lang}: duplicate question text")
        data = {
            "lang": lang,
            "categories": [{"id": cid, "name": mod.CATEGORIES[cid]} for cid in master.CATEGORIES],
            "questions": questions,
        }
        outputs[OUT / f"{lang}.json"] = json.dumps(data, ensure_ascii=False, indent=1)
        built.append(f"{lang}:{len(questions)}")
    if not errors:
        for path, rendered in outputs.items():
            if args.check:
                if not path.exists() or path.read_text(encoding="utf-8") != rendered:
                    errors.append(f"{path.name}: generated bank is stale; run tools/quiz/build_quiz.py")
            else:
                path.write_text(rendered, encoding="utf-8")
    for e in errors:
        print("ОШИБКА:", e)
    print("Собрано:", ", ".join(built), "| ошибок:", len(errors))
    print("Levels:", dict(Counter([*difficulty.values(), *(q["difficulty"] for q in levels)])))
    sys.exit(1 if errors else 0)


if __name__ == "__main__":
    main()
