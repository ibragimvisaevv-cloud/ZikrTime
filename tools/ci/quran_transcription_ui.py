"""Play real recordings through the Cyrillic reader and inspect its rendered text and cursor."""
import json
from pathlib import Path
import re
import subprocess
import traceback

import listening_offers_ui as suite
from quran_tap_word_ui import geometry


def tap_word(ayah, word):
    point = suite.wait_until(lambda: geometry(ayah, word, surah=1), bool,
                             f"No Cyrillic hit target for 1:{ayah}/{word}", 15)
    suite.shell("input", "tap", round(point[0]), round(point[1]))


def visible_text(node):
    return " ".join(child.get("text", "") for child in node.iter("node"))


def choose_reciter(reciter):
    suite.tap_control("quran_inline_options")
    suite.tap_control("quran_reciter_select")
    options = suite.wait_tag("quran_reciter_yasser-dosari")
    for name in ("mishary-alafasy", "yasser-dosari", "mahmoud-husary", "muhammad-minshawi"):
        assert suite.find_control(options, "quran_reciter_" + name) is not None, name
    suite.tap_control("quran_reciter_" + reciter)
    suite.tap_control("ayah_speed_50")
    suite.tap_control("ayah_close")


def verify():
    # The preceding tap regression uses an independent debug installation.
    suite.adb("install", "-r", "app/build/outputs/apk/debug/app-debug.apk")
    suite.shell("pm", "clear", suite.PKG)
    suite.shell("wm", "size", "1080x2200")
    suite.shell("wm", "density", "210")
    suite.route("quran")
    suite.tap_control("quran_transcription_entry")
    suite.wait_tag("quran_transcription_index")
    suite.tap_control("quran_surah_1")
    tree = suite.wait_tag("quran_transcription_1")
    verse = visible_text(suite.find_control(tree, "quran_transcription_1")).lower()
    assert "бисми" in verse and "рахиим" in verse, verse
    assert not re.search(r"[\u0600-\u06ff]", visible_text(tree)), "Arabic text in Cyrillic-only reader"
    suite.shot("reader")

    tap_word(2, 4)
    suite.wait_tag("quran_selected_ayah_1_2")
    assert not suite.player()["playWhenReady"]
    choose_reciter("yasser-dosari")
    # Tapping after the voice change specifies the same publisher-timed word.
    tap_word(2, 4)
    selected = suite.wait_tag("quran_selected_pair_1_2_3_4")
    assert suite.find_control(selected, "quran_transcription_2") is not None
    suite.shot("selected-word")
    suite.tap_control("quran_inline_play_pause")
    state = suite.wait_until(suite.player, lambda p: p["isPlaying"] and p.get("reciterId") == "yasser-dosari"
                             and p["surahId"] == 1 and p["ayah"] == 2, "Yasir recording did not play", 75)
    assert state["sourceUrl"] == "https://cdn.mp3quran.net/audio/yasser-dosari/r1/001.mp3", state
    assert (state["clipStartMs"], state["clipEndMs"]) == (3369, 9320), state
    assert 1550 <= state["positionMs"] <= 4200 and state["word"] == 4, state
    suite.REPORT["yasser_selected_word"] = state
    suite.wait_tag("quran_playing_pair_1_2_3_4")
    suite.shot("playing-selected-word")
    next_ayah = suite.wait_until(suite.player, lambda p: p["isPlaying"] and p["ayah"] == 3,
                                 "No natural advance to 1:3", 25)
    suite.wait_tag("quran_playing_ayah_3")
    suite.REPORT["natural_next_ayah"] = next_ayah
    suite.shot("next-ayah")
    # A real touch outside the current verse should switch it without another Play.
    tap_word(2, 4)
    again = suite.wait_until(suite.player, lambda p: p["isPlaying"] and p["ayah"] == 2 and
                             p.get("word") == 4, "Tap in another Cyrillic verse failed", 12)
    assert again["reciterId"] == "yasser-dosari" and again["clipStartMs"] == 3369
    suite.tap_control("quran_inline_play_pause")
    suite.wait_until(suite.player, lambda p: not p["playWhenReady"], "Pause failed")


if __name__ == "__main__":
    suite.MEDIA = Path("ci-out/transcription")
    suite.MEDIA.mkdir(parents=True, exist_ok=True)
    try:
        verify()
        suite.REPORT["verified"] = True
    except Exception:
        for filename, command in [
            ("android-crash.txt", ["logcat", "-d", "-b", "crash"]),
            ("android-activities.txt", ["logcat", "-d", "-s", "ActivityTaskManager", "ActivityManager", "AndroidRuntime"]),
        ]:
            with (suite.MEDIA / filename).open("wb") as output:
                subprocess.run(["adb", *command], stdout=output, timeout=15)
        (suite.MEDIA / "failure.txt").write_text(traceback.format_exc())
        suite.shot("failure")
        raise
    finally:
        (suite.MEDIA / "verification.json").write_text(json.dumps(suite.REPORT, ensure_ascii=False, indent=2))
