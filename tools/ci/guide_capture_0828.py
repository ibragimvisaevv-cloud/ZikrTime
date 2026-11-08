"""Capture real Android screens for the in-app illustrated guide.

No mock UI is painted. Screens are read-only examples on a disposable debug
profile. Every hotspot is taken from the accessibility bounds of the visible
control in the same screenshot. User data, permission grants, payments and
production writes are never used.
"""
import hashlib
import os
import traceback
from pathlib import Path

from PIL import Image

helpers = Path("tools/ci/guide_journeys.py")
exec(compile(helpers.read_text().split("\ndef fresh_skip_and_upgrade():")[0], str(helpers), "exec"))

ASSETS = OUT / "guide-assets" / "ru"
MANIFEST = {}
EVIDENCE = []


def route(screen):
    start(screen)
    root = ui()
    assert any(n.get("package") == PKG for n in root.iter("node")), screen
    return root


def local_setup():
    OUT.mkdir(exist_ok=True)
    (OUT / "screens").mkdir(exist_ok=True)
    ASSETS.mkdir(parents=True, exist_ok=True)
    shell("wm", "density", "480")
    shell("wm", "size", "1080x2400")
    shell("settings", "put", "system", "font_scale", "1.0")
    shell("am", "force-stop", PKG)
    shell("cmd", "locale", "set-app-locales", PKG, "--user", "0", "--locales", "ru")
    locale = shell("cmd", "locale", "get-app-locales", PKG, "--user", "0").strip()
    assert locale == f"Locales for {PKG} for user 0 are [ru]", locale
    seed("dark", onboarded=True, dismissed=True)


def specimen(topic, step, identifier, attribute="resource-id"):
    locate_control(identifier, attribute)
    root = ui()
    node = find_control(root, identifier, attribute)
    raw, visible = bounds(node), visible_bounds(root, node)
    assert raw == visible, (topic, step, identifier, "hotspot clipped", raw, visible)
    assert visible[2] > visible[0] and visible[3] > visible[1], identifier
    name = f"guide-source-{topic}-{step}"
    capture(name, root)
    png = OUT / "screens" / (name + ".png")
    with Image.open(png) as image:
        original = image.size
        left, top, right, bottom = visible
        assert 0 <= left < right <= original[0] and 0 <= top < bottom <= original[1]
        # Preserve the complete screenshot; normalized XML coordinates remain
        # valid after proportional reduction. 720px keeps zoomed labels legible.
        width = min(720, original[0])
        height = round(original[1] * width / original[0])
        resized = image.convert("RGB").resize((width, height), Image.Resampling.LANCZOS)
        filename = f"{topic}-{step}.webp"
        target = ASSETS / filename
        resized.save(target, "WEBP", quality=88, method=6)
    spec = {"image": filename, "width": width, "height": height,
            "target": [round(left/original[0], 6), round(top/original[1], 6),
                       round(right/original[0], 6), round(bottom/original[1], 6)]}
    steps = MANIFEST.setdefault(topic, [None, None, None])
    assert 1 <= step <= 3 and steps[step-1] is None
    steps[step-1] = spec
    EVIDENCE.append({"topic": topic, "step": step, "control": identifier,
                     "attribute": attribute, "originalSize": original, "bounds": visible,
                     "pngSha256": hashlib.sha256(png.read_bytes()).hexdigest(),
                     "webpSha256": hashlib.sha256(target.read_bytes()).hexdigest()})
    persist()
    return root


def persist():
    (ASSETS / "manifest.json").write_text(json.dumps(MANIFEST, ensure_ascii=False, indent=2) + "\n")
    (OUT / "guide-capture-provenance.json").write_text(json.dumps({
        "sourceSha": os.environ.get("GUIDE_APK_SOURCE_SHA", ""),
        "sourceRun": os.environ.get("GUIDE_APK_SOURCE_RUN", ""),
        "debugApkSha256": hashlib.sha256(Path("app/build/outputs/apk/debug/app-debug.apk").read_bytes()).hexdigest(),
        "profile": "disposable_debug_emulator", "locale": "ru", "theme": "dark",
        "imagesAreActualScreenshots": True, "productionWrites": False,
        "captures": EVIDENCE,
    }, ensure_ascii=False, indent=2) + "\n")


def capture_common():
    route("home")
    for step, label in enumerate(("Намаз", "Лимиты", "Настройки"), 1):
        specimen("welcome", step, label, "text")
    route("home")
    specimen("prayer", 1, "home_change_city")
    route("more")
    specimen("prayer", 2, "Время намаза", "text")
    route("home")
    specimen("prayer", 3, "prayer_notification_settings")
    specimen("journal", 1, "Фаджр: Отметить намаз", "content-desc")
    route("home")
    specimen("journal", 2, "home_prayer_journal")
    route("prayer_journal")
    # Yesterday is always editable; do not alter a prayer mark merely for a
    # screenshot. The actual status chooser is enough to show 'Made up'.
    tap_control("journal_previous")
    tap_control("journal_FAJR")
    specimen("journal", 3, "journal_MADE_UP")
    route("focus")
    specimen("limits", 1, "focus_choose_apps")
    specimen("limits", 2, "focus_settings_open")
    tap_control("focus_settings_open")
    specimen("limits", 3, "Ограничивать время", "text")
    route("focus")
    tap_control("focus_setup")
    specimen("permissions", 1, "Доступ к статистике использования", "text")
    specimen("permissions", 2, "Способ 1. Поверх других приложений", "text")
    specimen("permissions", 3, "focus_permissions_done")
    route("knowledge")
    specimen("knowledge", 1, "learning_level_1")
    # Real level selection on this disposable profile, no completed quiz or
    # invented learning progress is inserted to obtain the next two controls.
    tap_control("learning_level_1")
    tap_control("learning_level_confirm")
    specimen("knowledge", 2, "learning_start")
    specimen("knowledge", 3, "learning_review_start")
    route("qibla")
    specimen("tools", 1, "qibla_compass")
    route("tasbih")
    specimen("tools", 2, "tasbih_counter")
    route("home")
    specimen("tools", 3, "home_reading_reminders")
    route("shop")
    # Use the app's published read-only catalogue, never fabricated sellers.
    deadline = time.monotonic() + 45
    partner = None
    while time.monotonic() < deadline:
        root = ui()
        partner = next((n for n in root.iter("node")
                        if "shop_partner_" in n.get("resource-id", "")), None)
        if partner is not None:
            break
        time.sleep(1)
    assert partner is not None, "Published shop has no visible partner card; do not fabricate a guide seller"
    tag = partner.get("resource-id")
    specimen("shop", 1, tag)
    tap_control(tag)
    # Public card contact is captured but never activated.
    specimen("shop", 2, "Написать в Telegram", "text")
    route("shop")
    specimen("shop", 3, "shop_merchant")
    # Verified public catalogue snapshot provides actual prayer places and
    # known coordinates; unknown facilities stay unknown in the screenshot.
    places = json.loads(Path("shop-bot/places/berkat_yurt_verified_2026-11-07.json").read_text())["places"]
    first = places[0]
    put_pref("places_json", "string", json.dumps({"places": places, "seller_bot": "", "donate_url": ""}, ensure_ascii=False))
    put_pref("city", "string", "Беркат-Юрт")
    put_pref("city_id", "string", "")
    put_pref("lat", "float", first["lat"])
    put_pref("lon", "float", first["lon"])
    route("places")
    specimen("places", 1, "places_map")
    tap_control("place_card_" + first["id"])
    specimen("places", 2, "place_details_expand")
    specimen("places", 3, "place_details_route")


def capture_quran():
    route("quran")
    specimen("quran", 1, "quran_read_entry")
    tap_control("quran_search")
    shell("input", "text", "114")
    shell("input", "keyevent", "KEYCODE_BACK")
    tap_control("quran_surah_114")
    tap_control("quran_audio_reader_entry")
    observe("quran_ayah_sheet")
    specimen("quran", 2, "ayah_repeat_3")
    specimen("quran", 3, "quran_action_bookmark")


if __name__ == "__main__":
    mode = sys.argv[1] if len(sys.argv) > 1 else "common"
    try:
        local_setup()
        if mode in {"common", "all"}:
            capture_common()
        if mode in {"quran", "all"}:
            capture_quran()
        assert mode in {"common", "quran", "all"}, mode
        expected = 9 if mode == "common" else 1 if mode == "quran" else 10
        assert len(MANIFEST) == expected and all(len(v) == 3 and all(v) for v in MANIFEST.values())
        print(json.dumps({"complete": True, "topics": list(MANIFEST), "images": len(EVIDENCE),
                          "webpBytes": sum(p.stat().st_size for p in ASSETS.glob("*.webp"))}))
    except Exception:
        (OUT / "guide-capture-failure.txt").write_text(traceback.format_exc())
        try:
            capture("guide-capture-failure")
        except Exception:
            pass
        raise
