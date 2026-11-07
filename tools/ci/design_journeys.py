"""0.8.23 native controls and visual evidence on a disposable Android emulator.

Run with no arguments for all checks, or --flow NAME / --gallery to repeat only
the affected verification on the same APK. No live user data is written.
"""
import argparse
import traceback
from pathlib import Path

common = Path("tools/ci/features_controls.py")
exec(compile(common.read_text().split("mode = sys.argv[1]")[0], str(common), "exec"))


def start(route):
    shell("am", "start", "-S", "-W", "-n", PKG + "/.MainActivity",
          "--es", "qa_screen", route, "--es", "qa_lang", "ru",
          "--ez", "qa_onboarded", "true", "--ez", "qa_tilt", "false",
          "--ez", "qa_parallax", "false")
    time.sleep(1)
    tree = ui()
    assert any(n.get("package") == PKG for n in tree.iter("node")), route
    return tree


def russian(key):
    for folder in ("values-ru", "values"):
        for file in Path("app/src/main/res", folder).glob("*.xml"):
            root = ET.parse(file).getroot()
            for node in root.findall("string"):
                if node.get("name") == key:
                    return "".join(node.itertext()).replace("\\'", "'").replace('\\"', '"')
    raise AssertionError("Missing label: " + key)


def capture(name):
    tree = ui()
    (OUT / ("design-" + name + ".xml")).write_text(ET.tostring(tree, encoding="unicode"))
    screenshot("design-" + name)
    return tree


def assert_pref(key, expected):
    # UI writes use SharedPreferences.apply. Read complete snapshots from the
    # existing bounded reader instead of imposing an arbitrary multi-second wait.
    actual = prefs().get(key)
    assert actual == expected, (key, expected, actual)


def focus():
    start("focus")
    locate_control("focus_choose_apps")
    capture("focus-actions")
    tap_control("focus_choose_apps")
    root = ui()
    candidates = [n for n in root.iter("node")
                  if n.get("resource-id", "").split("/")[-1].startswith("focus_app_")]
    assert candidates, "Install the existing focus fixture if the emulator has no eligible launcher app"
    target = candidates[0].get("resource-id").split("/")[-1]
    package = target.removeprefix("focus_app_")
    before = set(prefs().get("packages", []))
    tap_control(target)
    expected = before.symmetric_difference({package})
    assert set(prefs().get("packages", [])) == expected
    capture("focus-app-selection")
    start("apps")
    assert set(prefs().get("packages", [])) == expected
    selected = locate_control(target)
    assert selected.get("checked") == str(package in expected).lower(), ET.tostring(selected)
    tap_control(target)
    assert set(prefs().get("packages", [])) == before
    start("focus")
    tap_control("focus_settings_open")
    locate_control("Настроить ограничения", "text")
    locate_control(russian("focus_daily_limit"), "text")
    capture("focus-settings")
    locate_control(russian("focus_permissions"), "text")
    capture("focus-permissions")
    shell("input", "keyevent", "KEYCODE_BACK")
    return {"choose_apps_opens_picker": True, "selected_package": package,
            "selection_persists_after_restart": True, "selection_restored": True,
            "limits_and_permissions_reachable": True}


def ensure_level():
    start("knowledge")
    if prefs().get("learning_level_v1") is None:
        # Choose through the first-use UI. The debug route does not set a level.
        tap_control("learning_level_1")
        tap_control("learning_level_confirm")
        start("knowledge")


def knowledge():
    ensure_level()
    locate_control("learning_start")
    capture("knowledge-start")
    tap_control("learning_start")
    # A first visit offers preparation. A resumed round may already be on a
    # card or question, so use the actual saved stage to choose the assertion.
    session = learning()["sessions"]["normal"]
    identifiers = {"ASK": "learning_read", "CARDS": "learning_card_next",
                   "QUIZ": "learning_answer_0", "RESULT": "learning_complete"}
    if session["stage"] == "CARDS" and session.get("card", 0) >= 4:
        locate_control("learning_card_quiz")
    elif session["stage"] == "RESULT":
        locate_control(russian("learning_complete"), "text")
    else:
        locate_control(identifiers[session["stage"]])
    capture("knowledge-round")
    start("knowledge")
    tap_control("learning_resume")
    assert learning()["sessions"]["normal"] == session
    return {"start_reaches_real_round": True, "stage": session["stage"],
            "round_persists_after_restart": True}


def more():
    start("more")
    locate_control("Намаз и напоминания", "text")
    capture("more-prayer-settings")
    tap_control("Настроить ограничения", "text")
    locate_control("focus_choose_apps")
    start("more")
    locate_control(russian("group_practice"), "text")
    capture("more-practice")
    locate_control(russian("group_application"), "text")
    locate_control(russian("settings_language"), "text")
    capture("more-application")
    locate_control("Помощь и поддержка", "text")
    capture("more-help")
    start("prayer_settings")
    tap_control("Параметры расчёта", "text")
    locate_control(russian("prayer_asr_section"), "text")
    capture("calculation-options")
    shell("input", "keyevent", "KEYCODE_BACK")
    return {"settings_groups_reachable": True, "focus_entry_opens_focus": True,
            "calculation_sheet_reachable": True}


def reminders():
    shell("pm", "grant", PKG, "android.permission.POST_NOTIFICATIONS")
    put_pref("personal_progress_enabled", "boolean", "true")
    start("prayer_journal")
    if prefs().get("prayer_check_enabled") == "true":
        tap_control("prayer_check_enabled")
    tap_control("prayer_check_enabled")
    tap_control("prayer_check_delay_15")
    assert_pref("prayer_check_enabled", "true")
    assert_pref("prayer_check_delay", "15")
    capture("prayer-check-on")
    start("prayer_journal")
    assert_pref("prayer_check_enabled", "true")
    assert_pref("prayer_check_delay", "15")
    chip = locate_control("prayer_check_delay_15")
    assert chip.get("checked") == "true" or chip.get("selected") == "true", ET.tostring(chip)
    tap_control("prayer_check_enabled")
    assert_pref("prayer_check_enabled", "false")
    assert not prefs().get("prayer_check_due")
    capture("prayer-check-off")
    return {"explicit_opt_in": True, "delay_minutes": 15,
            "choice_persists_after_restart": True, "disable_clears_next_reminder": True}


def permission_button(allow):
    suffixes = (["permission_allow_foreground_only_button", "permission_allow_one_time_button", "permission_allow_button"]
                if allow else ["permission_deny_button", "permission_deny_and_dont_ask_again_button"])
    root = ui()
    for suffix in suffixes:
        node = next((n for n in root.iter("node") if n.get("resource-id", "").endswith("/" + suffix)), None)
        if node is not None:
            tap_control(node.get("resource-id"))
            return
    raise AssertionError("Expected Android camera permission dialog")


def camera():
    def wait_for_preview():
        deadline = time.monotonic() + 45
        while time.monotonic() < deadline:
            root = ui()
            if find_control(root, russian("qibla_camera_error"), "text") is not None:
                capture("qibla-camera-error")
                raise AssertionError("Camera preview reported an error")
            if (find_control(root, russian("qibla_camera_close"), "text") is not None and
                    find_control(root, russian("qibla_camera_note"), "text") is not None and
                    find_control(root, russian("qibla_camera_loading"), "text") is None):
                return
            time.sleep(1)
        capture("qibla-camera-timeout")
        raise AssertionError("Camera preview did not reach STREAMING within 45 seconds")

    permission = "android.permission.CAMERA"
    shell("pm", "revoke", PKG, permission)
    shell("pm", "clear-permission-flags", PKG, permission, "user-set", "user-fixed")
    start("qibla")
    tap_control(russian("qibla_camera_open"), "text")
    permission_button(allow=True)
    locate_control(russian("qibla_camera_close"), "text")
    wait_for_preview()
    root = capture("qibla-camera")
    assert find_control(root, russian("qibla_camera_note"), "text") is not None, "Captured camera overlay is missing"
    assert find_control(root, russian("qibla_camera_loading"), "text") is None, "Captured preview is still loading"
    assert find_control(root, russian("qibla_camera_error"), "text") is None, "Captured preview has an error"
    assert re.search(r"android\.permission\.CAMERA:\s+granted=true", shell("dumpsys", "package", PKG))
    tap_control(russian("qibla_camera_close"), "text")
    locate_control(russian("qibla_camera_open"), "text")
    tap_control(russian("qibla_camera_open"), "text")
    locate_control(russian("qibla_camera_close"), "text")
    wait_for_preview()
    shell("input", "keyevent", "KEYCODE_BACK")
    locate_control(russian("qibla_camera_open"), "text")
    shell("pm", "revoke", PKG, permission)
    shell("pm", "clear-permission-flags", PKG, permission, "user-set", "user-fixed")
    start("qibla")
    tap_control(russian("qibla_camera_open"), "text")
    permission_button(allow=False)
    locate_control(russian("qibla_camera_permission"), "text")
    capture("qibla-camera-denied")
    tap_control(russian("qibla_camera_close"), "text")
    locate_control(russian("qibla_camera_open"), "text")
    return {"runtime_permission_requested": True, "granted_and_opened": True,
            "close_and_back_return_to_compass": True, "denial_explained": True,
            "preview_error_on_emulator": False, "preview_streaming": True,
            "preview_reopened": True}


ROUTES = ("home", "focus", "knowledge", "more", "prayer_settings", "alarm",
          "qibla", "tasbih", "about", "apps", "progress", "azkar", "quran",
          "shop", "places", "ramadan", "zakat", "prayer_journal", "reading_reminders",
          "onboarding_welcome", "onboarding_perms")


def gallery():
    paths = []
    font_before = shell("settings", "get", "system", "font_scale").strip()
    try:
        shell("settings", "put", "system", "font_scale", "1.0")
        ensure_level()
        for theme in ("dark", "light"):
            put_pref("appearance", "string", theme)
            for route in ROUTES:
                print("Gallery", theme, route, flush=True)
                start(route)
                name = theme + "-" + route
                capture(name)
                assert_pref("appearance", theme)
                paths.append("screens/design-" + name + ".png")
        # An additional compact viewport checks the larger text setting on the
        # screens whose action labels changed. Existing screen size is restored.
        shell("settings", "put", "system", "font_scale", "1.5")
        put_pref("appearance", "string", "dark")
        for route in ("focus", "knowledge", "more"):
            start(route)
            target = {"focus": "focus_choose_apps", "knowledge": "learning_start", "more": "appearance_dark"}[route]
            locate_control(target)
            name = "large-" + route
            capture(name)
            paths.append("screens/design-" + name + ".png")
    finally:
        shell("settings", "put", "system", "font_scale", font_before if font_before not in ("", "null") else "1.0")
    return {"themes": ["dark", "light"], "routes": list(ROUTES),
            "large_text": 1.5, "screenshots": paths}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--flow", choices=("focus", "knowledge", "more", "reminders", "camera"))
    parser.add_argument("--gallery", action="store_true")
    args = parser.parse_args()
    OUT.mkdir(exist_ok=True)
    (OUT / "screens").mkdir(exist_ok=True)
    start("home")
    report = {"groups": {}, "profile": "isolated_debug_emulator"}
    flows = {"focus": focus, "knowledge": knowledge, "more": more, "reminders": reminders, "camera": camera}
    selected = {args.flow: flows[args.flow]} if args.flow else ({} if args.gallery else flows)
    if not args.flow:
        selected["gallery"] = gallery
    active = "startup"
    try:
        for active, run in selected.items():
            print("Design verification", active, flush=True)
            report["groups"][active] = run()
            (OUT / "design-passed.json").write_text(json.dumps(report, ensure_ascii=False, indent=2))
        crashes = adb("logcat", "-d", "-b", "crash")
        (OUT / "design-crashes.txt").write_text(crashes)
        assert not re.search(r"FATAL EXCEPTION|Fatal signal", crashes), "Crash buffer contains a fatal error"
    except Exception:
        (OUT / "design-failure.txt").write_text(active + "\n" + traceback.format_exc())
        raise
