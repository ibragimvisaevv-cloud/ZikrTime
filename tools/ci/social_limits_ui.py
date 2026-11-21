"""Capture and verify the real Android daily-limit flow on a disposable emulator."""
import json
from pathlib import Path
import xml.etree.ElementTree as ET

helpers = Path("tools/ci/guide_journeys.py")
exec(compile(helpers.read_text().split("\ndef fresh_skip_and_upgrade():")[0], str(helpers), "exec"))


def capture_text(name, text):
    locate_control(text, "text")
    capture(name)


def main():
    OUT.mkdir(exist_ok=True)
    (OUT / "screens").mkdir(exist_ok=True)
    shell("wm", "size", "1080x2160")
    shell("wm", "density", "420")
    shell("settings", "put", "system", "font_scale", "1.0")
    shell("cmd", "locale", "set-app-locales", PKG, "--user", "0", "--locales", "ru")
    # Start as an onboarded person with no selected apps or permissions.
    seed("dark", onboarded=True, dismissed=True)
    initial = prefs_tree()
    for entry in list(initial):
        if entry.attrib.get("name") in {"packages", "apps_init", "limit"}:
            initial.remove(entry)
    ET.SubElement(initial, "string", name="learning_level_v1").text = "1"
    write_tree(initial)
    start()
    home = observe("home_set_limits")
    card = find_control(home, "home_set_limits")
    left, top, right, bottom = visible_bounds(home, card)
    assert bottom - top > 60 and right - left > 150, "Home card is outside the initial viewport"
    capture("01-home-social-limits", home)
    shell("input", "tap", (left + right) // 2, (top + bottom) // 2)
    capture("02-focus-setup", observe("focus_choose_apps"))

    labels = {n.get("name"): n.text for n in ET.parse("app/src/main/res/values-ru/strings.xml").getroot()}
    start("block_limit")
    title = labels["block_limit_title"]
    start_quiz = labels["block_quiz_button"].replace("%1$d", "3").replace("%2$d", "10")
    capture_text("03-daily-limit-block", title)
    tap_control(start_quiz, "text")
    locate_control("learning_answer_0")
    capture("04-first-blocking-question")
    bank = {q["id"]: q for q in json.loads(Path("app/src/main/assets/quiz/ru.json").read_text())["questions"]}
    for index in range(3):
        session = learning()["sessions"]["blocking"]
        question = bank[session["ids"][session["index"]]]
        tap_control(question["options"][question["answer"]], "text")
        tap_control("learning_finish" if index == 2 else "learning_next")
    capture_text("05-earned-ten-minutes", labels["block_earned"].replace("%1$d", "10"))


if __name__ == "__main__":
    main()
