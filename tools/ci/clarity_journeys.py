"""Map gesture ownership, compact previews and clear task navigation on Android.

All state is synthetic and private to a disposable debug emulator. No reviews,
corrections, seller submissions or messages are sent to production services.
"""
import traceback
from pathlib import Path

# Reuse the established ADB/cache helpers without executing the old 0.8.24 suite.
previous = Path("tools/ci/places_fix_journeys.py")
exec(compile(previous.read_text().split('if __name__ == "__main__":')[0], str(previous), "exec"))


def start(route="places"):
    shell("am", "start", "-S", "-W", "-n", PKG + "/.MainActivity",
          "--es", "qa_screen", route, "--es", "qa_lang", "ru", "--ez", "qa_onboarded", "true",
          "--ez", "qa_tilt", "false", "--ez", "qa_parallax", "false")
    time.sleep(1)
    root = ui()
    assert any(n.get("package") == PKG for n in root.iter("node")), route
    return root


def clipped_bounds(root, node):
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
    return left, top, right, bottom


def uncovered_bounds(root, node):
    """Exclude the floating nav bar from the scroll content's tappable area.

    Compose exposes the full scrolling viewport behind the separate nav overlay.
    Its semantic bounds alone do not prove that a point receives content taps.
    Identify the actual five-tab container, not a guessed screen fraction.
    """
    area = clipped_bounds(root, node)
    required = {"Намаз", "Знания", "Магазин", "Лимиты", "Настройки"}
    vl, vt, vr, vb = bounds(next(root.iter("node")))
    bars = []
    for candidate in root.iter("node"):
        l, t, r, b = bounds(candidate)
        if t <= vt + (vb - vt) / 2 or b - t >= (vb - vt) / 3:
            continue
        if required <= {n.get("text", "") for n in candidate.iter("node")}:
            bars.append(((r-l)*(b-t), candidate))
    if bars:
        _, bar = min(bars, key=lambda pair: pair[0])
        # Nav controls themselves remain tappable. Modal sheets expose no tab
        # container in their window, so their bounds are unchanged as well.
        if not any(n is node for n in bar.iter("node")):
            nl, nt, nr, nb = bounds(bar)
            l, t, r, b = area
            if l < nr and r > nl and b > nt:
                area = (l, t, r, min(b, nt - 12))
    return area


def locate_control(identifier, attribute="resource-id"):
    for _ in range(16):
        root = ui()
        node = find_control(root, identifier, attribute)
        if node is not None:
            l, t, r, b = uncovered_bounds(root, node)
            raw_height = bounds(node)[3] - bounds(node)[1]
            if r - l >= 24 and b - t >= min(48, max(24, raw_height)):
                node.set("bounds", f"[{l},{t}][{r},{b}]")
                return node
        owner = None
        for tag in ("place_details_scroll", "place_preview_scroll", "places_scroll"):
            owner = find_control(root, tag)
            if owner is not None:
                break
        if owner is None:
            candidates = [(uncovered_bounds(root, n), n) for n in root.iter("node")
                          if n.get("scrollable") == "true"]
            candidates = [(area, n) for area, n in candidates if area[3] - area[1] > 72]
            if not candidates:
                # Cold Quran/app-picker navigation may briefly expose only a
                # loading view. Wait within the same bounded 16-attempt budget;
                # do not swipe or fail before its real controls attach.
                time.sleep(.5)
                continue
            _, owner = max(candidates, key=lambda pair: pair[0][3] - pair[0][1])
        l, t, r, b = uncovered_bounds(root, owner)
        assert b - t >= 72, ("Unusable scroll area", identifier, (l, t, r, b))
        # A page drag must START below the map. The outer gutter may lie outside
        # the LazyColumn itself; dragging there would test an inert area.
        start_y = int(t + (b - t) * .82)
        if find_control(root, "place_details_sheet") is None and find_control(root, "places_map") is not None:
            ml, mt, mr, mb = bounds(find_control(root, "places_map"))
            x = (l + r) // 2
            start_y = max(start_y, mb + 24)
            assert start_y < b - 8, ("Map leaves no page gesture area", (l,t,r,b), (ml,mt,mr,mb))
        else:
            x = l + min(16, (r - l) // 4)
        shell("input", "swipe", x, start_y, x, int(t + (b - t) * .2), 350)
    raise AssertionError("Control not reachable: " + identifier)


def tap_control(identifier, attribute="resource-id"):
    node = locate_control(identifier, attribute)
    l, t, r, b = bounds(node)
    point = [(l+r)//2, (t+b)//2]
    # Reuse the locator's last dump for diagnostics; no additional UI roundtrip.
    (OUT / "tap-last.xml").write_bytes((OUT / "last-ui.xml").read_bytes())
    trace = {"identifier": identifier, "attribute": attribute, "bounds": [l,t,r,b], "point": point}
    (OUT / "tap-last.json").write_text(json.dumps(trace, ensure_ascii=False))
    with (OUT / "tap-actions.jsonl").open("a") as handle:
        handle.write(json.dumps(trace, ensure_ascii=False) + "\n")
    shell("input", "tap", *point)
    time.sleep(.7)


def camera(root):
    node = next((n for n in root.iter("node") if n.get("content-desc", "").startswith("map-camera:")), None)
    assert node is not None, "Debug camera observer absent from native map"
    values = tuple(float(v) for v in node.get("content-desc").split(":", 1)[1].split(","))
    assert len(values) == 3, values
    return values


def camera_changed(first, second):
    return abs(first[0] - second[0]) + abs(first[1] - second[1]) > 0.000001


def map_gestures():
    shell("wm", "size", "1080x1920")
    shell("settings", "put", "system", "font_scale", "1.0")
    seed("dark")
    root = start()
    area = bounds(find_control(root, "places_map"))
    l, t, r, b = area
    assert b - t >= 180 * DENSITY / 160
    before = camera(root)
    events = []
    # All directions must stay with MapKit. Vertical drags expose the original
    # parent LazyColumn interception; horizontal drags catch asymmetric fixes.
    for name, points in {
        "up": (.5, .72, .5, .28), "down": (.5, .28, .5, .72),
        "left": (.72, .5, .28, .5), "right": (.28, .5, .72, .5),
    }.items():
        x1, y1, x2, y2 = points
        shell("input", "swipe", int(l + (r-l)*x1), int(t + (b-t)*y1),
              int(l + (r-l)*x2), int(t + (b-t)*y2), 700)
        root = ui()
        after_area = bounds(find_control(root, "places_map"))
        after = camera(root)
        assert max(abs(a-v) for a, v in zip(area, after_area)) <= 3, ("Map pan scrolled page", name, area, after_area)
        assert camera_changed(before, after), ("Map did not pan", name, before, after)
        assert find_control(root, "place_details_sheet") is None, "Drag selected a mosque"
        events.append({"direction": name, "from": before, "to": after, "map_bounds": after_area})
        before = after
    capture("clarity-map-panned")
    vl, vt, vr, vb = bounds(next(root.iter("node")))
    # Start in the text area below the map, not on a native map or filter row.
    shell("input", "swipe", (l+r)//2, min(vb - 220, b + 160),
          (l+r)//2, max(vt + 120, t + 100), 500)
    after = ui()
    shifted = find_control(after, "places_map")
    # Accessibility bounds are CLIPPED to the LazyColumn viewport: the top of
    # a partly hidden map stays at the viewport top while its bottom moves up.
    # Require real content displacement, not a change of that clipped top.
    shifted_bounds = bounds(shifted) if shifted is not None else None
    assert shifted_bounds is None or shifted_bounds[3] < b - 20, (
        "Page no longer scrolls outside map", area, shifted_bounds)
    capture("clarity-page-scrolled")
    # A fresh Activity restores the city camera and tests a real sparse marker.
    root = start()
    l, t, r, b = bounds(find_control(root, "places_map"))
    for offset in (0, -18, 18):
        shell("input", "tap", (l+r)//2, (t+b)//2+offset)
        root = ui()
        if find_control(root, "place_details_sheet") is not None:
            break
    assert find_control(root, "place_details_sheet") is not None, "Native marker did not open preview"
    preview = preview_geometry("clarity-native-marker-preview", normal=True)
    close_sheet()
    return {"drags": events, "outside_map_scroll": True,
            "outside_drag_map_bounds_before": area,
            "outside_drag_map_bounds_after": shifted_bounds,
            "native_marker_preview": preview}


def preview_geometry(name, normal=False):
    root = capture(name)
    _, _, width, height = bounds(next(root.iter("node")))
    sheet = assert_visible(root, "place_details_sheet", width, height)
    assert find_control(root, "place_details_scroll") is None, "A tap unexpectedly opened expanded details"
    assert find_control(root, "place_preview_scroll") is not None
    if normal:
        assert bounds(sheet)[3] - bounds(sheet)[1] <= height * .72, ("Preview covers the screen", bounds(sheet), height)
    assert_visible(root, "place_details_close", width, height)
    # These actions are part of the compact panel; no expanding gesture needed.
    locate_control("place_details_route")
    locate_control("place_details_expand")
    return {"viewport_px": [width, height], "sheet_bounds": bounds(sheet), "compact_on_open": True}


def details(name):
    tap_control("place_details_expand")
    root = ui()
    assert find_control(root, "place_details_scroll") is not None, "Details button did not expand panel"
    locate_control("place_details_reviews")
    root = capture(name + "-reviews")
    _, _, width, height = bounds(next(root.iter("node")))
    assert_visible(root, "place_details_close", width, height)
    assert_visible(root, "place_details_route", width, height)
    tap_control("place_details_collapse")
    result = preview_geometry(name + "-collapsed")
    root = ui()
    body = find_control(root, "place_preview_scroll")
    # After reading the bottom, collapse starts at the mosque name again.
    texts = [n.get("text", "") for n in body.iter("node") if n.get("text")]
    assert any("мечет" in text.lower() for text in texts), texts
    return result


def preview_profiles():
    result = {}
    for name, width, height, font, theme in [
        ("normal", 1080, 1920, "1.0", "dark"),
        ("large", 1080, 1920, "1.5", "dark"),
        ("compact-light", 960, 1920, "1.5", "light"),
    ]:
        shell("wm", "size", f"{width}x{height}")
        shell("settings", "put", "system", "font_scale", font)
        seed(theme)
        start()
        tap_control("place_card_" + EMPTY_ID)
        geometry = preview_geometry("clarity-" + name + "-preview", normal=name == "normal")
        details("clarity-" + name)
        close_sheet(back=True)
        if name == "normal":
            tap_control("place_card_" + FULL_ID)
            preview_geometry("clarity-long-preview", normal=True)
            details("clarity-long")
            close_sheet()
        result[name] = {"requested_px": [width, height], "font_scale": font, "theme": theme,
                        "geometry": geometry, "explicit_expand_and_collapse": True, "back_closes": True}
    return result


def rotation_preview():
    shell("wm", "size", "1080x1920")
    shell("settings", "put", "system", "font_scale", "1.5")
    seed("dark")
    start()
    tap_control("place_card_" + EMPTY_ID)
    shell("wm", "size", "1920x1080")
    preview_geometry("clarity-landscape-preview")
    details("clarity-landscape")
    close_sheet()
    return {"preview_survives_rotation": True, "details_and_actions_reachable": True}


def search_flow():
    shell("wm", "size", "1080x1920")
    shell("settings", "put", "system", "font_scale", "1.0")
    seed("dark")
    start()
    tap_control("places_search")
    shell("input", "text", "nothing-matches")
    shell("input", "keyevent", "KEYCODE_BACK")
    locate_control(russian("places_search_empty"), "text")
    capture("clarity-search-empty")
    return {"empty_result_visible": True, "keyboard_dismisses": True}


def label_at(identifier, expected):
    node = locate_control(identifier)
    text = " ".join(n.get("text", "") for n in node.iter("node"))
    if expected not in text:
        # Compose may expose a button's label as its own sibling semantics.
        locate_control(expected, "text")
    return node


def navigation():
    shell("wm", "size", "1080x1920")
    shell("settings", "put", "system", "font_scale", "1.0")
    seed("dark")
    start("home")
    locate_control("Лимиты", "text")
    locate_control("Настройки", "text")
    capture("clarity-home")
    label_at("home_prayer_journal", "История намазов")
    tap_control("home_prayer_journal")
    locate_control("journal_date")
    capture("clarity-home-prayer-history")
    start("home")
    label_at("home_quran_open", "Читать Коран")
    capture("clarity-home-actions")
    tap_control("home_quran_open")
    locate_control("quran_index_list")
    start("home")
    label_at("home_set_limits", "Настроить лимит")
    tap_control("home_set_limits")
    locate_control("Лимиты", "text")
    locate_control("focus_choose_apps")
    capture("clarity-limits-actions")
    tap_control("focus_choose_apps")
    target = "focus_app_" + FOCUS_TARGET
    locate_control(target)
    before = set(prefs().get("packages", []))
    tap_control(target)
    assert set(prefs().get("packages", [])) == before.symmetric_difference({FOCUS_TARGET})
    # The new explicit Done action returns to the feature that opened the list.
    tap_control("focus_apps_done")
    locate_control("focus_choose_apps")
    tap_control("focus_settings_open")
    locate_control("2. Общее время на день", "text")
    capture("clarity-limit-settings")
    tap_control("focus_permissions_open")
    locate_control("Разрешения телефона", "text")
    locate_control("focus_permissions_done")
    capture("clarity-phone-access")
    tap_control("focus_permissions_done")
    locate_control("focus_choose_apps")

    start("knowledge")
    if prefs().get("learning_level_v1") is None:
        tap_control("learning_level_1")
        tap_control("learning_level_confirm")
    label_at("learning_start", "Ответить на вопросы")
    capture("clarity-learning-actions")
    locate_control("Бонус — это дополнительное время для приложений, выбранных в разделе «Лимиты». Учиться можно и без ограничения приложений.", "text")
    capture("clarity-learning-bonus-explained")
    # Return to the button above the explanation with a fresh view, preserving
    # the selected level; do not simulate quiz completion or grant a reward.
    start("knowledge")
    tap_control("learning_start")
    session = learning()["sessions"]["normal"]
    target = {"ASK": "learning_read", "CARDS": "learning_card_next",
              "QUIZ": "learning_answer_0", "RESULT": "learning_complete"}[session["stage"]]
    locate_control(target)
    capture("clarity-question-entry")

    start("more")
    locate_control("Город, уведомления и оформление", "text")
    capture("clarity-settings")
    locate_control(russian("settings_language"), "text")
    locate_control("Другие разделы", "text")
    locate_control("Мечети рядом и маршрут до них", "text")
    capture("clarity-other-sections")
    tap_control("Мечети рядом и маршрут до них", "text")
    locate_control("places_scroll")

    # Confirm the changed labels/actions stay reachable at 320dp and 150% font,
    # and in the light palette. Main navigation remains labelled rather than
    # becoming a row of unexplained icons.
    shell("wm", "size", "960x1920")
    shell("settings", "put", "system", "font_scale", "1.5")
    seed("light")
    start("home")
    locate_control("Лимиты", "text")
    locate_control("Настройки", "text")
    label_at("home_quran_open", "Читать Коран")
    capture("clarity-large-home-actions")
    tap_control("home_quran_open")
    locate_control("quran_index_list")
    start("focus")
    label_at("focus_setup", "Выдать разрешения")
    tap_control("focus_setup")
    locate_control("Разрешения телефона", "text")
    locate_control("focus_permissions_done")
    capture("clarity-large-phone-access")
    tap_control("focus_permissions_done")
    locate_control("focus_choose_apps")
    start("more")
    locate_control(russian("settings_language"), "text")
    capture("clarity-large-settings")
    return {"home_labeled_actions_reach_quran_history_and_limits": True,
            "bottom_nav_labels": ["Лимиты", "Настройки"],
            "focus_app_selection_and_done": True, "dedicated_phone_permissions": True,
            "quiz_action_reaches_normal_round": True, "quiz_stage": session["stage"],
            "bonus_explained": True, "settings_task_groups_and_places_route": True,
            "large_font_light_navigation": True}


if __name__ == "__main__":
    OUT.mkdir(exist_ok=True)
    (OUT / "screens").mkdir(exist_ok=True)
    report = {"profile": "isolated_debug_emulator", "production_writes": False,
              "native_map_camera_observed": True, "groups": {}}
    active = "initialization"
    try:
        shell("wm", "density", DENSITY)
        actions = {"map_gestures": map_gestures, "preview_profiles": preview_profiles,
                   "rotation": rotation_preview, "search": search_flow, "navigation": navigation}
        if __import__("os").environ.get("NAVIGATION_ONLY") == "true":
            inherited = json.loads((OUT / "inherited-clarity.json").read_text())
            assert inherited["map_inputs_unchanged"] and inherited["map_assertions_unchanged"]
            assert set(inherited["groups"]) == {"map_gestures", "preview_profiles", "rotation", "search"}
            report["groups"].update(inherited["groups"])
            report["inherited_evidence"] = {k: v for k, v in inherited.items() if k != "groups"}
            actions = {"navigation": navigation}
            (OUT / "clarity-passed.json").write_text(json.dumps(report, ensure_ascii=False, indent=2))
        for active, action in actions.items():
            print("Clarity check:", active, flush=True)
            report["groups"][active] = action()
            (OUT / "clarity-passed.json").write_text(json.dumps(report, ensure_ascii=False, indent=2))
        crashes = adb("logcat", "-d", "-b", "crash")
        (OUT / "crashes.txt").write_text(crashes)
        assert not re.search(r"FATAL EXCEPTION|Fatal signal", crashes), "App crash recorded"
        report["complete"] = True
        (OUT / "clarity-passed.json").write_text(json.dumps(report, ensure_ascii=False, indent=2))
    except Exception:
        (OUT / "clarity-failure.txt").write_text(active + "\n" + traceback.format_exc())
        try:
            capture("clarity-failure")
        except Exception:
            pass
        raise
    finally:
        shell("settings", "put", "system", "font_scale", "1.0")
        shell("wm", "size", "1080x1920")
