"""Tap displayed Quran words and verify real audio position and both highlight layers."""
import io
import json
import re
import time
import subprocess
import traceback
import uuid
import xml.etree.ElementTree as ET
from pathlib import Path

from PIL import Image
import listening_offers_ui as suite
from quran_reading_pairs_ui import line_metrics, png, seek


def geometry(ayah, word, surah=5):
    token = uuid.uuid4().hex
    suite.shell("am", "start", "-W", "-n", suite.PKG + "/.MainActivity", "--ez", "qa_audio_probe", "true",
                "--es", "qa_audio_probe_id", token, "--ei", "qa_point_surah", surah,
                "--ei", "qa_point_ayah", ayah, "--ei", "qa_point_word", word)

    def read():
        latest = json.loads(suite.shell("run-as", suite.PKG, "cat", "files/qa-audio-state.json"))
        return latest if latest.get("probeId") == token else None

    return suite.wait_until(read, bool, "Point probe did not respond", 6).get("point")


def tap_word(ayah, word):
    point = suite.wait_until(lambda: geometry(ayah, word), bool, f"5:{ayah}/{word} outside viewport", 12)
    suite.shell("input", "tap", round(point[0]), round(point[1]))


def current(tree, prefix):
    pattern = re.compile(prefix + r"_5_(\d+)_(\d+)_(\d+)")
    matches = [tuple(map(int, m.groups())) for n in tree.iter("node")
               if (m := pattern.fullmatch(n.get("resource-id", "").split("/")[-1]))]
    assert len(matches) <= 1, f"Competing targets: {matches}"
    return matches[0] if matches else None


def near_word(ayah, clip_ms, label):
    first_audible = None
    def reached(p):
        nonlocal first_audible
        if not (p.get("isPlaying") and p.get("playWhenReady") and p.get("surahId") == 5 and p.get("ayah") == ayah):
            return False
        if first_audible is None:
            first_audible = time.monotonic()
        # Selecting the basmala extends the same clip backward. Always compare
        # absolute track positions against the independent numbered-verse clock.
        origin = {1: 14390, 2: 45050}[ayah]
        relative = p["positionMs"] + p["clipStartMs"] - origin
        if clip_ms - 250 <= relative < clip_ms + 2600:
            return True
        # Media3 can publish one stale snapshot immediately after the touch.
        # Do not let playback from the beginning naturally reach the target.
        if time.monotonic() - first_audible > 1.4:
            raise AssertionError(f"{label}: wrong first audible position {p['positionMs']} for {clip_ms}")
        return False
    state = suite.wait_until(suite.player, reached, label, 35)
    suite.REPORT[label] = {"ayah": ayah, "expected_clip_ms": clip_ms, "actual_absolute_ms": state["clipStartMs"] + state["positionMs"]}
    return state


def locate_panels(tree, whole, current_ayah):
    if whole:
        # Root source block can cover both adjacent verses in continuous mode.
        return [suite.find_control(tree, "quran_whole_1")]
    return [suite.find_control(tree, f"quran_arabic_{current_ayah}")]


def samples_at(image, point, color):
    x, y = (round(n) for n in point)
    pixels = image.crop((x - 9, y - 8, x + 10, y + 9)).convert("RGB").getdata()
    return sum(pixel == color for pixel in pixels)


def evidence(label, whole, ayah, selected=False, old=None):
    def inspect():
        tree = suite.ui()
        cursor = current(tree, "quran_selected_pair" if selected else "quran_playing_pair")
        if not cursor or cursor[0] != ayah:
            return False
        nodes = [n for n in locate_panels(tree, whole, ayah) if n is not None]
        if not nodes:
            return False
        old_point = geometry(*old) if old else None
        if old and old_point is None:
            return False
        raw = png()
        with Image.open(io.BytesIO(raw)) as image:
            soft, bright = (sum(group) for group in zip(*(line_metrics(image, n) for n in nodes)))
            if soft < 4 or bright < 4:
                return False
            old_soft = samples_at(image, old_point, (76, 69, 52)) if old_point else 0
            old_bright = samples_at(image, old_point, (216, 184, 118)) if old_point else 0
        assert old_soft <= 2 and old_bright <= 2, f"Previous verse remained colored ({old_soft}, {old_bright})"
        (suite.MEDIA / (label + ".png")).write_bytes(raw)
        suite.REPORT[label] = {"pair": cursor, "soft_rows": soft, "bright_rows": bright,
                               "old_verse_soft_pixels": old_soft, "old_verse_bright_pixels": old_bright}
        return True
    suite.wait_until(inspect, bool, "Missing expected two-layer selection: " + label, 25)


def open_reader(whole):
    prefs = ET.Element("map")
    ET.SubElement(prefs, "string", name="last").text = "5:1"
    ET.SubElement(prefs, "boolean", name="whole_surah", value=str(whole).lower())
    suite.write_tree(prefs, "quran_reading")
    suite.route("quran")
    suite.tap_control("quran_read_entry")
    suite.wait_tag("quran_whole_1" if whole else "quran_arabic_1")
    if not whole:
        translation = suite.find_control(suite.wait_tag("quran_translation_1"), "quran_translation_1")
        assert re.search(r"[А-Яа-яЁё]", translation.get("text", "")), "Missing Russian translation"


def verify(whole):
    mode = "whole" if whole else "translation"
    open_reader(whole)
    # 5:1 includes an unrecited written basmala. The packed source uses the
    # original token indices. Token 11 starts 11,995 ms into this verse.
    tap_word(1, 11)
    suite.wait_tag("quran_inline_player")
    assert not suite.player()["playWhenReady"], "Selecting a word started playback"
    evidence(mode + "-silent-selection", whole, 1, selected=True)
    suite.tap_control("quran_inline_options")
    if whole:
        suite.tap_control("quran_reciter_select")
        options = suite.wait_tag("quran_reciter_yasser-dosari")
        for reciter in ("mishary-alafasy", "yasser-dosari", "mahmoud-husary", "muhammad-minshawi"):
            assert suite.find_control(options, "quran_reciter_" + reciter) is not None, reciter
        suite.tap_control("quran_reciter_mishary-alafasy")
    suite.tap_control("ayah_speed_50")
    suite.tap_control("ayah_close")
    suite.tap_control("quran_inline_play_pause")
    near_word(1, 11995, mode + "-start-at-selected-word")
    suite.tap_control("quran_inline_play_pause")
    held = suite.wait_until(suite.player, lambda p: not p["playWhenReady"] and p["ayah"] == 1, "Pause failed")
    suite.tap_control("quran_inline_play_pause")
    near_word(1, held["positionMs"], mode + "-ordinary-resume")

    if whole:
        # 5:1 includes a separately recorded basmala, with no published word
        # subdivisions. Tap its printed second token and verify the intro itself.
        tap_word(1, 2)
        intro = suite.wait_until(suite.player, lambda p: p["isPlaying"] and p["ayah"] == 1 and
                                 p["clipStartMs"] == 7265 and p["positionMs"] < 2000 and p["word"] is None,
                                 "Printed basmala did not begin at the recorded introduction", 25)
        seek(1, 14390 - intro["clipStartMs"] - 750)
        first_word = suite.wait_until(suite.player, lambda p: p["isPlaying"] and p["clipStartMs"] == 7265 and
                                     p["ayah"] == 1 and p["word"] == 5 and p["positionMs"] >= 14390 - 7265,
                                     "Basmala did not lead into the first numbered ayah", 8)
        suite.REPORT["printed_basmala"] = {"intro_ms": intro["clipStartMs"],
                                          "first_spoken_token": first_word["word"]}
    # Audible, immediate backwards jump in the same long verse, with no Play.
    tap_word(1, 7)
    near_word(1, 4275, mode + "-same-verse-word")
    # Select another displayed ayah while recitation is still running.
    tap_word(2, 5)
    near_word(2, 5695, mode + "-cross-verse-word")
    evidence(mode + "-cross-verse", whole, 2, old=(1, 7))
    # Original token 22 has a newly verified start at 88,385 ms; its
    # unambiguous display covers 88,781..89,705 ms after an overlapping tail.
    tap_word(2, 22)
    near_word(2, 43335, mode + "-supplemental-word-start")
    extra = suite.wait_until(suite.player, lambda p: p["isPlaying"] and p["ayah"] == 2 and p["word"] == 22,
                             "Supplemental measured interval did not reach the UI cursor", 6)
    suite.REPORT[mode + "-supplemental-highlight"] = extra["word"]
    # Seek back via another real touch, then let the audio advance naturally.
    tap_word(1, 11)
    active = near_word(1, 11995, mode + "-return-to-verse")
    seek(1, active["clipEndMs"] - active["clipStartMs"] - 1300)
    suite.wait_until(suite.player, lambda p: p["isPlaying"] and p["ayah"] == 2,
                     "Playing ayah did not advance naturally from 5:1 to 5:2", 20)
    evidence(mode + "-automatic-next", whole, 2, old=(1, 11))
    # Pause a running clip, choose another position, then explicitly resume.
    suite.tap_control("quran_inline_play_pause")
    suite.wait_until(suite.player, lambda p: not p["playWhenReady"], "Pause failed")
    tap_word(1, 11)
    tree = suite.wait_tag("quran_inline_play_pause")
    focus = current(tree, "quran_selected_pair")
    assert focus and focus[0] == 1 and focus[1] <= 11 <= focus[2], f"Target word not selected: {focus}"
    assert not suite.player()["playWhenReady"], "Changing the selected word resumed paused audio"
    suite.tap_control("quran_inline_play_pause")
    near_word(1, 11995, mode + "-resume-selected")
    suite.tap_control("quran_inline_close")


def main():
    suite.MEDIA = Path("ci-out/word-taps")
    suite.MEDIA.mkdir(parents=True, exist_ok=True)
    try:
        # A tablet-sized logical viewport keeps both long ayahs visible so
        # the test can sample the prior one after a jump in either layout.
        suite.shell("wm", "size", "1080x2200")
        suite.shell("wm", "density", "180")
        suite.shell("cmd", "uimode", "night", "yes")
        suite.shell("input", "keyevent", "KEYCODE_WAKEUP")
        suite.shell("wm", "dismiss-keyguard")
        suite.adb("install", "-r", "app/build/outputs/apk/debug/app-debug.apk")
        suite.shell("pm", "clear", suite.PKG)
        suite.route("quran")
        index = suite.wait_tag("quran_audio_entry")
        assert suite.find_control(index, "quran_read_entry") is not None
        assert suite.find_control(index, "quran_transcription_entry") is None
        assert not suite.text_visible(index, "транскрипц")
        suite.shot("reader-index")
        suite.start_shop()
        suite.put_pref("appearance", "string", "dark")
        verify(True)
        verify(False)
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


if __name__ == "__main__":
    main()
