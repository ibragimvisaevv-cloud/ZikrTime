"""Exercise real text taps and playback on the Quran reader in a disposable debug emulator."""
import io
import json
import re
import subprocess
import traceback
import xml.etree.ElementTree as ET

from PIL import Image
import listening_offers_ui as suite


def tap_arabic_text(tag, long_press=False):
    """Tap a visible source glyph, even when the final justified line is short."""
    node = suite.locate_control(tag)
    left, top, right, bottom = map(int, re.findall(r"-?\d+", node.get("bounds")))
    png = subprocess.check_output(["adb", "exec-out", "screencap", "-p"], timeout=25)
    with Image.open(io.BytesIO(png)) as image:
        pixels = image.convert("RGB")
        matches = []
        for y in range(max(top, 0), min(bottom, pixels.height)):
            for x in range(min(right - 1, pixels.width - 1), max(left, 0), -1):
                r, g, b = pixels.getpixel((x, y))
                if abs(r - 240) <= 6 and abs(g - 230) <= 6 and abs(b - 209) <= 6:
                    matches.append((x, y))
                    break
            if len(matches) >= 5:
                break
    assert matches, f"No readable Arabic glyph in {tag} bounds {node.get('bounds')}"
    x, y = matches[-1]
    if long_press:
        suite.shell("input", "swipe", x, y, x, y, 700)
    else:
        suite.shell("input", "tap", x, y)


def cursor():
    tree = suite.ui()
    ayahs = suite.active_numbers(tree, "quran_playing_ayah_")
    words = suite.active_numbers(tree, "quran_playing_word_")
    return (ayahs[0], words[0]) if len(ayahs) == len(words) == 1 else None


def verify_reader():
    # Model the previous reading location. All further navigation and input take
    # place through the same visible controls that the reader uses.
    prefs = ET.Element("map")
    ET.SubElement(prefs, "string", name="last").text = "98:1"
    ET.SubElement(prefs, "boolean", name="whole_surah", value="true")
    suite.write_tree(prefs, "quran_reading")
    suite.route("quran")
    suite.tap_control("quran_read_entry")
    suite.wait_tag("quran_whole_surah")
    tap_arabic_text("quran_whole_1")
    tree = suite.wait_tag("quran_inline_play_pause")
    suite.check(suite.find_control(tree, "quran_whole_surah") is not None and
                suite.find_control(tree, "quran_ayah_sheet") is None,
                "A short tap on Arabic keeps the full-surah reading page and shows its bottom play control")
    suite.check(not suite.player()["playWhenReady"], "Selecting text waits for Play")
    suite.shot("ZikrTime-reader-selection-51")
    suite.tap_control("quran_inline_play_pause")
    first = suite.wait_until(suite.player, lambda p: p["isPlaying"] and p["surahId"] == 98 and
                             p["ayah"] == 1 and p["continueAfter"],
                             "Reader did not start at the selected 98:1", 75)
    cursor_a = suite.wait_until(cursor, bool, "Reader did not display a timed word", 35)
    cursor_b = suite.wait_until(cursor, lambda c: c is not None and c != cursor_a,
                                "Reader word highlighting did not move", 35)
    suite.REPORT["reader_word_positions"] = [cursor_a, cursor_b]
    suite.verify_highlight_pixels("ZikrTime-reader-playing-51", whole=True)
    later = suite.wait_until(suite.player, lambda p: p["isPlaying"] and p["surahId"] == 98 and
                             isinstance(p["ayah"], int) and p["ayah"] > first["ayah"],
                             "Reader did not continue with the next ayah", 45)
    suite.check(suite.highlight("quran_playing_ayah_") >= later["ayah"],
                "Full-surah reader follows the advancing audio")
    # This existing system-level fixture observes real Android ducking state.
    suite.verify_duck()
    suite.tap_control("quran_inline_play_pause")
    suite.wait_until(suite.player, lambda p: not p["playWhenReady"], "Inline pause failed")
    suite.wait_until(lambda: suite.active_numbers(suite.ui(), "quran_playing_ayah_"),
                     lambda values: not values, "Pause did not clear the active text")
    suite.tap_control("quran_mode_translation")
    tree = suite.ui()
    ids = suite.active_numbers(tree, "quran_arabic_")
    assert ids, "No Arabic text visible in translation mode"
    tag = f"quran_arabic_{ids[0]}"
    tap_arabic_text(tag)
    suite.wait_tag("quran_inline_play_pause")
    suite.tap_control("quran_inline_options")
    suite.wait_tag("ayah_repeat_1")
    suite.locate_control("ayah_speed_100")
    suite.back()
    suite.wait_tag("quran_inline_player")
    suite.tap_control("quran_inline_close")
    suite.wait_until(lambda: suite.find_control(suite.ui(), "quran_inline_player"),
                     lambda node: node is None, "Inline player controls did not close")
    tree = suite.ui()
    visible = suite.active_numbers(tree, "quran_arabic_")
    assert visible
    tap_arabic_text(f"quran_arabic_{visible[0]}", long_press=True)
    suite.wait_tag("quran_personal_actions")
    suite.locate_control("quran_action_bookmark")
    suite.locate_control("quran_note_open")
    suite.check(True, "Long press keeps bookmark and note actions accessible")


def main():
    suite.MEDIA = suite.OUT / "reading-store-promos"
    suite.MEDIA.mkdir(parents=True, exist_ok=True)
    try:
        suite.shell("wm", "size", "720x1600")
        suite.shell("wm", "density", "320")
        suite.shell("cmd", "uimode", "night", "yes")
        suite.shell("input", "keyevent", "KEYCODE_WAKEUP")
        suite.shell("wm", "dismiss-keyguard")
        suite.adb("install", "-r", "app/build/outputs/apk/debug/app-debug.apk")
        suite.adb("install", "-r", "focus-test.apk")
        suite.start_shop()
        suite.put_pref("appearance", "string", "dark")
        verify_reader()
        suite.verify_store()
        suite.REPORT["verified"] = True
    except Exception:
        failure = traceback.format_exc()
        print(failure, flush=True)
        (suite.MEDIA / "failure.txt").write_text(failure)
        suite.shot("failure")
        raise
    finally:
        (suite.MEDIA / "verification.json").write_text(json.dumps(suite.REPORT, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
