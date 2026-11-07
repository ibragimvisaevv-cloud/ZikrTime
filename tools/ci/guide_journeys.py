"""Focused guide checks using private synthetic state on a disposable emulator.

No permission is granted, no blocking service is enabled, and no message,
review, payment or other production write is sent by these journeys.
"""
import traceback
from pathlib import Path

# Import established ADB, preference and screenshot helpers without running any
# previous map, quiz, prayer or product test suite.
common = Path("tools/ci/features_controls.py")
exec(compile(common.read_text().split("mode = sys.argv[1]")[0], str(common), "exec"))

TOPICS = ("welcome", "prayer", "journal", "quran", "limits", "permissions",
          "knowledge", "places", "tools", "shop")
GUIDE_KEY = "guide_dismissed_v1"
DENSITY = 480
PROTECTED_KEYS = ("appearance", "city", "city_id", "lat", "lon", "online_times", "blocking",
                  "notify", "alarm_on", "packages", "apps_init", "limit")


def bounds(node):
    assert node is not None, "Missing UI element"
    return tuple(map(int, re.findall(r"-?\d+", node.get("bounds", ""))))


def visible_bounds(root, node):
    left, top, right, bottom = bounds(node)
    vl, vt, vr, vb = bounds(next(root.iter("node")))
    left, top, right, bottom = max(left, vl), max(top, vt), min(right, vr), min(bottom, vb)
    parents = {child: parent for parent in root.iter() for child in parent}
    parent = parents.get(node)
    while parent is not None:
        if parent.get("scrollable") == "true":
            pl, pt, pr, pb = bounds(parent)
            left, top, right, bottom = max(left, pl), max(top, pt), min(right, pr), min(bottom, pb)
        parent = parents.get(parent)
    # The settings page scrolls behind a separate floating bottom navigation.
    # A semantic bound below that panel is not proof of a tappable row.
    labels = {"Намаз", "Знания", "Магазин", "Лимиты", "Настройки"}
    bars = []
    for candidate in root.iter("node"):
        l, t, r, b = bounds(candidate)
        if t > vt + (vb - vt) / 2 and b - t < (vb - vt) / 3:
            if labels <= {n.get("text", "") for n in candidate.iter("node")}:
                bars.append(((r-l)*(b-t), candidate))
    if bars:
        _, bar = min(bars, key=lambda pair: pair[0])
        if not any(n is node for n in bar.iter("node")):
            nl, nt, nr, nb = bounds(bar)
            if left < nr and right > nl and bottom > nt:
                bottom = min(bottom, nt - 12)
    return left, top, right, bottom


def observe(identifier, tries=8):
    """Wait for a new screen to attach, without scrolling a different screen."""
    for _ in range(tries):
        root = ui()
        if find_control(root, identifier) is not None:
            return root
        time.sleep(.4)
    raise AssertionError("Screen not reached: " + identifier)


def locate_control(identifier, attribute="resource-id"):
    previous_signature = None
    direction = 1
    for _ in range(12):
        root = ui()
        node = find_control(root, identifier, attribute)
        if node is not None:
            l, t, r, b = visible_bounds(root, node)
            if r - l >= 24 and b - t >= 48:
                node.set("bounds", f"[{l},{t}][{r},{b}]")
                return node
            # Already known above the viewport: search upwards, not repeatedly
            # towards the bottom as the old page-only locator did.
            if bounds(node)[3] <= bounds(next(root.iter("node")))[1] + 72:
                direction = -1
        scrolls = [(visible_bounds(root, n), n) for n in root.iter("node") if n.get("scrollable") == "true"]
        scrolls = [(area, n) for area, n in scrolls if area[3]-area[1] >= 72]
        if not scrolls:
            time.sleep(.4)
            continue
        area, owner = max(scrolls, key=lambda pair: pair[0][3]-pair[0][1])
        signature = ET.tostring(owner, encoding="unicode")
        if signature == previous_signature:
            direction *= -1
        previous_signature = signature
        l, t, r, b = area
        start, end = (t + (b-t)*.82, t + (b-t)*.2)
        if direction < 0:
            start, end = end, start
        shell("input", "swipe", l + min(24, (r-l)//4), int(start), l + min(24, (r-l)//4), int(end), 320)
    raise AssertionError("Control not reachable: " + identifier)


def tap_control(identifier, attribute="resource-id"):
    node = locate_control(identifier, attribute)
    l, t, r, b = bounds(node)
    point = [(l+r)//2, (t+b)//2]
    (OUT / "tap-last.xml").write_bytes((OUT / "last-ui.xml").read_bytes())
    event = {"identifier": identifier, "bounds": [l,t,r,b], "point": point}
    (OUT / "tap-last.json").write_text(json.dumps(event, ensure_ascii=False))
    with (OUT / "tap-actions.jsonl").open("a") as handle:
        handle.write(json.dumps(event, ensure_ascii=False) + "\n")
    shell("input", "tap", *point)
    time.sleep(.45)


def capture(name, root=None):
    if root is None:
        root = ui()
    (OUT / (name + ".xml")).write_text(ET.tostring(root, encoding="unicode"))
    screenshot(name)
    return root


def page(topic, capture_name=None):
    root = observe("guide_page_" + topic)
    assert find_control(root, "guide_screen") is not None
    if capture_name:
        capture(capture_name, root)
    return root


def fixed_action(root, tag, minimum_dp=36):
    node = find_control(root, tag)
    raw = bounds(node)
    area = visible_bounds(root, node)
    assert area == raw, (tag, "action clipped", raw, area)
    assert area[3] - area[1] >= minimum_dp * DENSITY / 160, (tag, "small action", area)
    return area


def seed(theme, onboarded, dismissed=None):
    root = ET.Element("map")
    booleans = {"onboarded": onboarded, "online_times": False, "blocking": False,
                "notify": False, "alarm_on": False, "apps_init": True,
                "prayer_history_migrated_v1": True}
    if dismissed is not None:
        booleans[GUIDE_KEY] = dismissed
    for key, value in booleans.items():
        ET.SubElement(root, "boolean", name=key, value=str(value).lower())
    strings = {"appearance": theme, "city": "Москва", "city_id": "moscow",
               "places_json": '{"places":[],"seller_bot":"","donate_url":""}',
               "reviews_json": '{"reviews":{}}',
               "places_at": str(int(time.time()*1000)+3_600_000),
               "reviews_at": str(int(time.time()*1000)+3_600_000)}
    for key, value in strings.items():
        ET.SubElement(root, "string", name=key).text = value
    ET.SubElement(root, "float", name="lat", value="55.7558")
    ET.SubElement(root, "float", name="lon", value="37.6173")
    ET.SubElement(root, "int", name="limit", value="37")
    ET.SubElement(ET.SubElement(root, "set", name="packages"), "string").text = "example.synthetic"
    shell("am", "force-stop", PKG)
    shell("run-as", PKG, "mkdir", "-p", "shared_prefs")
    write_tree(root)


def start(route="home"):
    # No qa_onboarded override: the fresh/existing policy must read real stored
    # state. onboarding_finish only enters the existing final setup screen.
    shell("am", "start", "-S", "-W", "-n", PKG + "/.MainActivity",
          "--es", "qa_screen", route, "--es", "qa_lang", "ru",
          "--ez", "qa_tilt", "false", "--ez", "qa_parallax", "false")
    time.sleep(.6)


def protected_preferences():
    current = prefs()
    return {key: current.get(key) for key in PROTECTED_KEYS}


def permissions():
    raw = shell("dumpsys", "package", PKG)
    grants = sorted(set(re.findall(r"(android\.permission\.[A-Z_]+): granted=(true|false)", raw)))
    def op_mode(operation):
        # Read-only permission checks may update rejection timestamps. Compare
        # the authorization mode, never incidental last-access diagnostics.
        state = shell("appops", "get", PKG, operation)
        match = re.search(re.escape(operation) + r":\s*([a-z_]+)", state)
        return match.group(1) if match else "default"
    return {"runtime_grants": grants,
            "accessibility": shell("settings", "get", "secure", "enabled_accessibility_services").strip(),
            "overlay": op_mode("SYSTEM_ALERT_WINDOW"),
            "usage": op_mode("GET_USAGE_STATS")}


def choose_topic(topic):
    tap_control("guide_contents")
    observe("guide_contents_dialog")
    tap_control("guide_topic_" + topic)
    return page(topic)


def fresh_skip_and_upgrade():
    shell("wm", "size", "1080x1920")
    shell("settings", "put", "system", "font_scale", "1.0")
    seed("dark", onboarded=False)
    start("onboarding_finish")
    observe("onboarding_step_4")
    tap_control("onboarding_next")
    root = page("welcome", "guide-first-welcome-dark")
    assert prefs().get(GUIDE_KEY) == "false", "Fresh guide incorrectly marked dismissed"
    before = protected_preferences()
    before_permissions = permissions()
    tap_control("guide_next")
    page("prayer")
    tap_control("guide_back")
    page("welcome")
    tap_control("guide_skip")
    assert prefs().get(GUIDE_KEY) == "true", "Skip not persisted"
    start()
    root = observe("home_prayer_journal")
    assert find_control(root, "guide_screen") is None
    assert protected_preferences() == before, "Skipping guide changed user settings"
    assert permissions() == before_permissions, "Guide granted or changed phone permissions"
    # Existing installations must not be forced through new first-run education.
    seed("dark", onboarded=True)
    start()
    root = observe("home_prayer_journal")
    assert find_control(root, "guide_screen") is None
    assert prefs().get(GUIDE_KEY) == "true", "Existing installation migration not persisted"
    return {"fresh_setup_enters_guide": True, "back_and_skip": True,
            "skip_survives_process_restart": True, "upgrade_not_forced": True,
            "skip_preserves_settings_and_permissions": True}


def replay_and_destinations():
    start("more")
    tap_control("settings_guide")
    page("welcome", "guide-settings-replay")
    before = protected_preferences()
    before_permissions = permissions()
    for topic in TOPICS[1:]:
        tap_control("guide_next")
        root = page(topic, "guide-dark-" + topic)
        fixed_action(root, "guide_finish" if topic == "shop" else "guide_next")
        fixed_action(root, "guide_back")
        if topic == "quran":
            tap_control("guide_open")
            observe("quran_index_list")
            shell("input", "keyevent", "KEYCODE_BACK")
            page("quran", "guide-return-from-quran")
        if topic == "limits":
            tap_control("guide_open")
            observe("guide_return")
            observe("focus_choose_apps")
            tap_control("guide_return")
            page("limits", "guide-return-from-limits")
    assert protected_preferences() == before, "Browsing guide or opening its destinations changed settings"
    assert permissions() == before_permissions, "Browsing guide changed permissions"
    tap_control("guide_finish")
    observe("settings_guide")
    tap_control("settings_guide")
    page("welcome")
    choose_topic("permissions")
    capture("guide-contents-jump-permissions")
    tap_control("guide_close")
    observe("settings_guide")
    return {"settings_replay": True, "all_topics_reachable": list(TOPICS),
            "next_back_controls_unclipped": True, "contents_jump": True,
            "quran_and_limits_return_to_same_topic": True,
            "finish_returns_to_settings": True, "new_replay_starts_at_welcome": True,
            "browsing_preserves_settings_and_permissions": True}


def large_font_rotation_and_finish():
    shell("wm", "size", "960x1920")
    shell("settings", "put", "system", "font_scale", "1.5")
    seed("light", onboarded=False)
    start("onboarding_finish")
    observe("onboarding_step_4")
    tap_control("onboarding_next")
    root = page("welcome", "guide-large-light-welcome")
    fixed_action(root, "guide_next")
    fixed_action(root, "guide_skip")
    choose_topic("limits")
    root = capture("guide-large-light-limits")
    fixed_action(root, "guide_next")
    fixed_action(root, "guide_back")
    # Real display/configuration change without force-stopping the activity.
    shell("wm", "size", "1920x1080")
    time.sleep(.7)
    root = page("limits", "guide-landscape-large-limits")
    fixed_action(root, "guide_next")
    fixed_action(root, "guide_back")
    fixed_action(root, "guide_skip")
    tap_control("guide_next")
    root = page("permissions", "guide-landscape-large-permissions")
    fixed_action(root, "guide_next")
    tap_control("guide_back")
    page("limits")
    shell("wm", "size", "960x1920")
    time.sleep(.7)
    page("limits")
    choose_topic("shop")
    root = capture("guide-large-light-finish")
    fixed_action(root, "guide_finish")
    tap_control("guide_finish")
    assert prefs().get(GUIDE_KEY) == "true", "Completion not persisted"
    start()
    root = observe("home_prayer_journal")
    assert find_control(root, "guide_screen") is None
    # The permanent entry must remain usable with the same large font.
    start("more")
    tap_control("settings_guide")
    page("welcome", "guide-large-settings-replay")
    tap_control("guide_close")
    observe("settings_guide")
    return {"font_scale": 1.5, "portrait_width_dp": 320, "palette": "light",
            "rotation_retains_topic": True, "landscape_controls_unclipped": True,
            "completion_survives_restart": True, "large_settings_replay": True}


if __name__ == "__main__":
    OUT.mkdir(exist_ok=True)
    (OUT / "screens").mkdir(exist_ok=True)
    report = {"profile": "isolated_debug_emulator", "production_writes": False,
              "previous_suites_rerun": False, "groups": {}}
    active = "initialization"
    try:
        shell("wm", "density", DENSITY)
        actions = {"fresh_skip_upgrade": fresh_skip_and_upgrade,
                   "replay_and_destinations": replay_and_destinations,
                   "large_font_rotation_finish": large_font_rotation_and_finish}
        for active, action in actions.items():
            print("Guide check:", active, flush=True)
            report["groups"][active] = action()
            (OUT / "guide-passed.json").write_text(json.dumps(report, ensure_ascii=False, indent=2))
        crashes = adb("logcat", "-d", "-b", "crash")
        (OUT / "crashes.txt").write_text(crashes)
        assert not re.search(r"FATAL EXCEPTION|Fatal signal", crashes), "App crash recorded"
        report["complete"] = True
        (OUT / "guide-passed.json").write_text(json.dumps(report, ensure_ascii=False, indent=2))
    except Exception:
        (OUT / "guide-failure.txt").write_text(active + "\n" + traceback.format_exc())
        try:
            capture("guide-failure")
        except Exception:
            pass
        raise
    finally:
        shell("settings", "put", "system", "font_scale", "1.0")
        shell("wm", "size", "1080x1920")
