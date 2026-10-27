"""Собирает вопросы викторины из tools/quiz/<язык>.py в app/src/main/assets/quiz/<язык>.json.

Эталон — ru.py: из него берутся порядок, id, темы и арабский текст.
В других языках вопрос записывается как (id, тема, вопрос, варианты или None, справка):
None вместо вариантов — взять варианты из эталона (для арабских аятов).
Запуск: python3 tools/quiz/build_quiz.py
"""
import importlib.util
import json
import pathlib
import sys

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


def main():
    master = load(MASTER)
    m_by_id = {q[0]: q for q in master.QUESTIONS}
    metadata = json.loads((HERE / "source_metadata.json").read_text(encoding="utf-8"))
    if set(metadata) != set(m_by_id):
        raise ValueError("Source metadata IDs must match the bank; never reuse another question ID")
    errors = []
    OUT.mkdir(parents=True, exist_ok=True)
    built = []
    for lang in LANGS:
        mod = load(lang)
        if mod is None:
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
        data = {
            "lang": lang,
            "categories": [{"id": cid, "name": mod.CATEGORIES[cid]} for cid in master.CATEGORIES],
            "questions": questions,
        }
        (OUT / f"{lang}.json").write_text(json.dumps(data, ensure_ascii=False, indent=1), encoding="utf-8")
        built.append(f"{lang}:{len(questions)}")
    for e in errors:
        print("ОШИБКА:", e)
    print("Собрано:", ", ".join(built), "| ошибок:", len(errors))
    sys.exit(1 if errors else 0)


if __name__ == "__main__":
    main()
