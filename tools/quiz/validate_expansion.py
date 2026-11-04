"""Verify the 0.8.21 expansion without transferring old learning history to changed facts."""
import json
import re
import subprocess
from collections import Counter
from pathlib import Path

BASE = "c592403a83b5455719b35dc1570399406e676efa"
ROOT = Path(__file__).resolve().parents[2]
LANGS = ("ru", "en", "uz", "tg", "kk", "ky", "az", "tr")

def read_baseline(lang):
    path = f"app/src/main/assets/quiz/{lang}.json"
    return json.loads(subprocess.check_output(
        ["git", "show", f"{BASE}:{path}"], cwd=ROOT).decode("utf-8"))

def main():
    canonical = None
    for lang in LANGS:
        data = json.loads((ROOT / f"app/src/main/assets/quiz/{lang}.json").read_text(encoding="utf-8"))
        questions = data["questions"]
        by_id = {q["id"]: q for q in questions}
        assert len(by_id) == len(questions) == 450, (lang, "duplicate IDs or wrong count")
        assert Counter(q["difficulty"] for q in questions) == {1: 150, 2: 150, 3: 150}, lang
        old = read_baseline(lang)
        assert data["categories"] == old["categories"], (lang, "category identity changed")
        for question in old["questions"]:
            assert by_id.get(question["id"]) == question, (lang, question["id"], "old learning identity or meaning changed")
        identity = [(q["id"], q["cat"], q["difficulty"], q["contentVersion"], q["answer"]) for q in questions]
        if canonical is None:
            canonical = identity
        assert identity == canonical, (lang, "localized learning identity mismatch")
        additions = [q for q in questions if re.match(r"l[123]x_", q["id"])]
        assert len(additions) == 247, (lang, "expected 247 authored additions")
        for q in additions:
            assert len(q["options"]) == 4 and len({o.strip().casefold() for o in q["options"]}) == 4, (lang, q["id"])
            assert q["answer"] == 0 and q["contentVersion"] == 1, (lang, q["id"], "canonical answer/version")
            assert q["source"].get("reference") and q["source"].get("url", "").startswith("https://"), (lang, q["id"], "source")
            assert not q["source"].get("originalReview") and not q["source"].get("translationReview"), (lang, q["id"], "undocumented review")
            for field in ("q", "info"):
                assert len(q[field].strip()) >= 20, (lang, q["id"], field)
                if lang in ("ru", "tg", "kk", "ky"):
                    assert re.search("[А-Яа-яЁё]", q[field]), (lang, q["id"], "untranslated text", field)
    print("450 questions in 8 languages; 247 additions; all 203 existing records preserved exactly.")

if __name__ == "__main__":
    main()
