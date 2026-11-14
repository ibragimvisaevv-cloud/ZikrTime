"""Reproduce Al-Maida pair highlighting on a real media timeline and capture the reader."""
import io
import json
import re
import subprocess
import time
import traceback
import uuid
import xml.etree.ElementTree as ET
from pathlib import Path

from PIL import Image
import listening_offers_ui as suite
from reading_store_promos_ui import tap_arabic_text


PAIR = re.compile(r"^quran_playing_pair_5_(\d+)_(\d+)_(\d+)$")
SELECTION = (76, 69, 52)
GOLD = (216, 184, 118)


def pair_in(tree):
    pairs = [tuple(map(int, match.groups())) for node in tree.iter("node")
             if (match := PAIR.fullmatch(node.get("resource-id", "").split("/")[-1]))]
    assert len(pairs) <= 1, f"Several simultaneous reading pairs: {pairs}"
    return pairs[0] if pairs else None


def seek(ayah, position):
    # The debug hook validates the surah, current ayah and active playback.
    suite.shell("am", "start", "-W", "-n", suite.PKG + "/.MainActivity", "--ez", "qa_audio_probe", "true",
                "--es", "qa_audio_probe_id", uuid.uuid4().hex, "--ei", "qa_seek_surah", "5",
                "--ei", "qa_seek_ayah", ayah, "--el", "qa_seek_clip_ms", position)


def png():
    return subprocess.check_output(["adb", "exec-out", "screencap", "-p"], timeout=25)


def line_metrics(image, node):
    left, top, right, bottom = map(int, re.findall(r"-?\d+", node.get("bounds", "")))
    bounds = (max(0, left), max(0, top), min(right, image.width), min(bottom, image.height))
    if bounds[0] >= bounds[2] or bounds[1] >= bounds[3]:
        return 0, 0
    area = image.crop(bounds).convert("RGB")
    rgb = area.tobytes()
    # A filled source-text span has broad runs of the exact background color.
    # Arabic letter contours and verse number glyphs have no such wide strips.
    def band_rows(color):
        sequence = bytes(color) * 16
        stride = area.width * 3
        return sum(sequence in rgb[start:start + stride] for start in range(0, len(rgb), stride))
    return band_rows(SELECTION), band_rows(GOLD)


def photo(first, ayah, whole, label, expected=None):
    def capture():
        tree = suite.ui()
        pair = pair_in(tree)
        if not pair or pair[0] != ayah or (expected and pair != expected):
            return None
        nodes = [n for n in tree.iter("node")
                 if re.fullmatch("quran_" + ("whole" if whole else "arabic") + r"_\d+",
                                 n.get("resource-id", "").split("/")[-1])]
        main = suite.find_control(tree, f"quran_whole_{first}" if whole else f"quran_arabic_{ayah}")
        if main is None:
            return None
        screen = png()
        with Image.open(io.BytesIO(screen)) as image:
            old_rows = sum(line_metrics(image, node)[0] for node in nodes)
            gold_rows = line_metrics(image, main)[1]
        assert old_rows < 3, f"Old whole-ayah selection is visible at 5:{ayah} ({old_rows} rows)"
        if gold_rows < 4:
            return None
        (suite.MEDIA / (label + ".png")).write_bytes(screen)
        suite.REPORT[label] = {"pair": pair, "old_selection_rows": old_rows, "pair_fill_rows": gold_rows}
        return pair
    return suite.wait_until(capture, bool, "No visible clean pair for " + label, 30)


def open_reader(ayah, whole):
    suite.shell("am", "force-stop", suite.PKG)
    prefs = ET.Element("map")
    ET.SubElement(prefs, "string", name="last").text = f"5:{ayah}"
    ET.SubElement(prefs, "boolean", name="whole_surah", value=str(whole).lower())
    suite.write_tree(prefs, "quran_reading")
    suite.route("quran")
    suite.tap_control("quran_read_entry")
    tag = f"quran_whole_{ayah}" if whole else f"quran_arabic_{ayah}"
    suite.wait_tag(tag)
    tap_arabic_text(tag)
    suite.wait_tag("quran_inline_player")
    # Deliberately slow real playback to allow asynchronous UIAutomator snapshots.
    suite.tap_control("quran_inline_options")
    suite.tap_control("ayah_speed_50")
    suite.tap_control("ayah_close")
    suite.tap_control("quran_inline_play_pause")
    return suite.wait_until(suite.player, lambda p: p["isPlaying"] and p["surahId"] == 5 and
                            p["ayah"] == ayah and p["speed"] == .5 and p["continueAfter"],
                            f"Expected real playback from 5:{ayah}", 90)


def verify_boundaries(ayah, whole):
    label = ("whole" if whole else "translation") + f"-5-{ayah}"
    state = open_reader(ayah, whole)
    if ayah == 1:
        # Publisher words 5 and 6 (after the written unrecited basmala) form a
        # single group. Their clip positions are 0..3195 ms and 3195..4275 ms.
        # Seek just inside each interval; normal playback stays running.
        seek(ayah, 0)
        first = photo(1, 1, whole, label + "-first", (1, 5, 6))
        seek(ayah, 2800)
        observed = suite.wait_until(suite.player, lambda p: p["isPlaying"] and p["word"] == 6,
                                    "Second spoken word in first pair did not play", 12)
        tree = suite.ui()
        # If UIAutomator missed the 1.08s word, rewind a second time before
        # the transition, then sample the same pair while that word sounds.
        if pair_in(tree) != first:
            seek(ayah, 2700)
            tree = suite.ui()
        assert pair_in(tree) == first, "The pair changed at its internal word boundary"
        suite.REPORT[label + "-same-pair"] = {"pair": first, "second_source_word": observed["word"]}
        seek(ayah, 4275)
        photo(1, 1, whole, label + "-second", (1, 7, 8))
    duration = state["clipEndMs"] - state["clipStartMs"]
    seek(ayah, duration - 1200)
    following = ayah + 1
    suite.wait_until(suite.player, lambda p: p["isPlaying"] and p["surahId"] == 5 and p["ayah"] == following,
                     f"Natural clip transition from 5:{ayah} to 5:{following} failed", 25)
    photo(ayah, following, whole, label + f"-to-{following}")
    # Real pause clears the marker and the attached word background in the visible page.
    suite.tap_control("quran_inline_play_pause")
    suite.wait_until(suite.player, lambda p: not p["playWhenReady"], "Reader did not pause")
    pause_tree = suite.wait_until(lambda: suite.ui(), lambda t: pair_in(t) is None, "Pair survived pause")
    prefix = "quran_whole_" if whole else "quran_arabic_"
    nodes = [n for n in pause_tree.iter("node") if re.fullmatch(prefix + r"\d+", n.get("resource-id", "").split("/")[-1])]
    assert nodes, "No text visible after pausing"
    paused = png()
    with Image.open(io.BytesIO(paused)) as image:
        assert all(line_metrics(image, n)[0] < 3 for n in nodes), "Paused page still shows stale manual selection"
    (suite.MEDIA / (label + "-paused.png")).write_bytes(paused)
    suite.tap_control("quran_inline_close")
    suite.check(True, label + " no stale filled ayah after natural playback transition or pause")


def main():
    suite.MEDIA = Path("ci-out/reading-pairs")
    suite.MEDIA.mkdir(parents=True, exist_ok=True)
    try:
        suite.shell("wm", "size", "720x1600")
        suite.shell("wm", "density", "320")
        suite.shell("cmd", "uimode", "night", "yes")
        suite.shell("input", "keyevent", "KEYCODE_WAKEUP")
        suite.shell("wm", "dismiss-keyguard")
        suite.adb("install", "-r", "app/build/outputs/apk/debug/app-debug.apk")
        suite.start_shop()
        suite.put_pref("appearance", "string", "dark")
        for whole in (True, False):
            for ayah in (1, 6):
                verify_boundaries(ayah, whole)
        suite.REPORT["verified"] = True
    except Exception:
        (suite.MEDIA / "failure.txt").write_text(traceback.format_exc())
        suite.shot("failure")
        raise
    finally:
        (suite.MEDIA / "verification.json").write_text(json.dumps(suite.REPORT, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
