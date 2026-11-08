"""Focused 0.8.28 ayah-learning and illustrated-guide journeys.

Runs only changed behavior. All media operations use real visible UI controls;
the debug snapshot only observes the service. No religious text is modified.
"""
import traceback
from pathlib import Path

helpers = Path("tools/ci/audio_journeys.py")
exec(compile(helpers.read_text().split('\nif __name__ == "__main__":')[0], str(helpers), "exec"))

SURAH = 114
AYAH = 1


def open_reader(restart=False):
    open_route("quran", restart=restart)
    observe("quran_index_list")
    locate_control("quran_search")
    current = find_control(ui(), "quran_search")
    if current.get("text", ""):
        tap_control("quran_search_clear")
        assert find_control(ui(), "quran_search").get("text", "") == "", "Clear-search action did not clear the field"
    tap_control("quran_search")
    focused = find_control(ui(), "quran_search")
    assert focused.get("focused") == "true", "Search field did not acquire input focus"
    assert focused.get("text", "") == "", "Search field changed before typing"
    # A single `adb input text 114` immediately after an IME opens can race
    # Compose's selection updates: the observed failed run typed '41' into an
    # initially empty field. Type through real key events at a settled cursor,
    # and verify every prefix instead of proceeding to a different surah.
    prefix = ""
    for digit in str(SURAH):
        shell("input", "keyevent", "KEYCODE_MOVE_END")
        shell("input", "keyevent", "KEYCODE_" + digit)
        prefix += digit
        entered = find_control(ui(), "quran_search")
        actual = entered.get("text", "")
        with (OUT / "learning-search-input.jsonl").open("a") as handle:
            handle.write(json.dumps({"expected": prefix, "actual": actual,
                                     "focused": entered.get("focused")}) + "\n")
        assert actual == prefix, ("Search input changed or lost a typed digit", prefix, actual)
    shell("input", "keyevent", "KEYCODE_BACK")
    assert find_control(ui(), "quran_search").get("text", "") == str(SURAH), \
        "Closing the keyboard changed the exact surah query"
    tap_control("quran_surah_" + str(SURAH))
    observe("quran_audio_reader_entry")


def ayah_sheet(number=AYAH):
    tap_control("quran_ayah_actions_" + str(number))
    observe("quran_ayah_sheet")
    require_reachable("ayah_play_pause")
    return ui()


def verse_playing(number=AYAH, timeout=60):
    return wait_state(lambda s: s["player"]["surahId"] == SURAH and
                      s["player"].get("ayah") == number and s["player"]["isPlaying"],
                      f"Selected ayah {SURAH}:{number} did not play", timeout)["player"]


def verse_paused():
    if probe()["player"]["playWhenReady"]:
        tap_control("ayah_play_pause")
    return wait_state(lambda s: not s["player"]["playWhenReady"] and
                      not s["player"]["isPlaying"], "Ayah pause did not stop playback")["player"]


def verify_clip(player, ayah=AYAH):
    assert player["surahId"] == SURAH and player["ayah"] == ayah, player
    start, end = player.get("clipStartMs"), player.get("clipEndMs")
    assert isinstance(start, int) and isinstance(end, int) and 0 <= start < end
    source = json.loads(Path("app/src/main/assets/quran_audio/alafasy_ayah_timings.json").read_text())
    chapter = next(item for item in source["surahs"] if item["surah"] == SURAH)
    expected = next(row for row in chapter["ayahs"] if row[0] == ayah)
    assert [start, end] == expected[1:], ("Player clip differs from verified source boundaries", player, expected)
    assert abs(player["durationMs"] - (end - start)) <= 1500, ("Wrong clip duration", player)
    assert 0 <= player["positionMs"] <= player["durationMs"] + 500
    return {"surah": SURAH, "ayah": ayah, "startMs": start, "endMs": end,
            "durationMs": player["durationMs"]}


def learning_audio():
    online(True)
    shell("wm", "size", "1080x2400")
    shell("settings", "put", "system", "font_scale", "1.0")
    seed("dark", onboarded=True, dismissed=True)
    put_pref("alarm_on", "boolean", "true")
    put_pref("alarm_days", "set", [])
    before = protected_preferences()
    open_route("quran", restart=True)
    root = observe("quran_index_list")
    # Both primary actions must be visible together without scrolling.
    for tag in ("quran_read_entry", "quran_audio_entry"):
        fixed_action(root, tag, minimum_dp=48)
    capture("learning-read-listen-entry", root)
    open_reader()
    ayah_sheet()
    assert not probe()["player"]["playWhenReady"], "Opening an ayah panel started sound"
    tap_control("ayah_repeat_3")
    tap_control("ayah_speed_75")
    assert not probe()["player"]["playWhenReady"], "Configuring repeats or speed started sound"
    tap_control("ayah_play_pause")
    first = verse_playing()
    clip = verify_clip(first)
    assert first["repeatCount"] == 3 and abs(first["speed"] - .75) < .01
    assert not first["continueAfter"], "A new ayah must stop after its repetitions by default"
    capture("learning-ayah-repeat-playing")
    # A real source clip repeats to completion; no seeking to ENDED or injected
    # service command is used to manufacture the repeat count.
    complete = wait_state(lambda s: s["player"].get("ayah") == AYAH and
                          s["player"].get("repeatIteration") == 3 and
                          s["player"]["ended"] and not s["player"]["isPlaying"],
                          "Three real ayah repeats did not finish", 100)["player"]
    verify_clip(complete)
    capture("learning-ayah-three-repeats-finished")
    tap_control("ayah_play_pause")
    replay = verse_playing()
    assert replay["repeatIteration"] == 1 and replay["positionMs"] < 4_000, \
        ("Listening again did not restart a fresh repeat cycle", replay)
    verse_paused()
    tap_control("ayah_repeat_0")
    tap_control("ayah_speed_150")
    tap_control("ayah_play_pause")
    infinite = wait_state(lambda s: s["player"].get("ayah") == AYAH and
                          s["player"].get("repeatIteration", 0) >= 2 and s["player"]["isPlaying"],
                          "Infinite repeat did not enter its second real loop", 45)["player"]
    assert infinite["repeatCount"] == 0 and abs(infinite["speed"] - 1.5) < .01
    paused_loop = verse_paused()
    capture("learning-infinite-paused")
    open_reader(restart=True)
    ayah_sheet()
    restored_loop = probe()["player"]
    assert restored_loop["surahId"] == SURAH and restored_loop["ayah"] == AYAH
    assert restored_loop["repeatCount"] == 0 and restored_loop["repeatIteration"] == paused_loop["repeatIteration"]
    assert abs(restored_loop["speed"] - 1.5) < .01
    assert abs(restored_loop["positionMs"] - paused_loop["positionMs"]) < 1500
    assert not restored_loop["playWhenReady"], "Restoring a repeat cycle started sound"
    tap_control("ayah_repeat_1")
    # Ayah 114:2 is only 5.239s at normal speed. At the previous 1.5x,
    # Android's accessibility snapshot could outlast it before the real Pause
    # tap. Slow this explicit-next check so it observes and pauses that verse,
    # rather than accidentally testing a later auto-advanced selection.
    tap_control("ayah_speed_50")
    tap_control("ayah_continue")
    assert not probe()["player"]["playWhenReady"], "Continue toggle resumed paused sound"
    tap_control("ayah_play_pause")
    second = verse_playing(2, timeout=45)
    second_clip = verify_clip(second, ayah=2)
    assert second["continueAfter"] and second["repeatCount"] == 1
    verse_paused()
    tap_control("ayah_continue")
    tap_control("ayah_repeat_0")
    tap_control("ayah_speed_100")
    tap_control("ayah_play_pause")
    verse_playing(2)
    shell("input", "keyevent", "KEYCODE_HOME")
    time.sleep(3)
    sessions = shell("dumpsys", "media_session")
    notifications = shell("dumpsys", "notification", "--noredact")
    (OUT / "learning-background-media-session.txt").write_text(sessions)
    (OUT / "learning-background-notifications.txt").write_text(notifications)
    assert PKG in sessions and "PlaybackState" in sessions
    assert PKG in notifications and "MediaStyle" in notifications
    assert probe()["player"]["isPlaying"], "Ayah playback stopped on leaving the screen"
    shell("input", "keyevent", "KEYCODE_MEDIA_PAUSE")
    wait_state(lambda s: not s["player"]["playWhenReady"], "System media pause failed for an ayah")
    tap_control("ayah_play_pause")
    verse_playing(2)
    open_route("alarm")
    tap_control("adhan_preview_athan_khalaf_one")
    wait_state(lambda s: not s["player"]["playWhenReady"] and not s["player"]["isPlaying"],
               "Adhan preview did not pause repeating ayah")
    tap_control("adhan_preview_athan_khalaf_one")
    assert not probe()["player"]["playWhenReady"], "Stopping adhan resumed repeating ayah"
    assert protected_preferences() == before, "Learning audio changed prayer or blocking preferences"
    open_reader(restart=True)
    ayah_sheet()
    assert not probe()["player"]["playWhenReady"], "Restoring the app resumed repeating audio"
    capture("learning-restored-without-autoplay")
    return {"firstClip": clip, "secondClip": second_clip, "threeRealRepeatsFinish": True,
            "replayRestartsRepeatCycle": True,
            "repeatCounterSpeedAndPositionRestorePaused": True,
            "infiniteRepeatAndPause": True, "speeds": [.75, 1.5, .5, 1.0],
            "explicitContinueNextAyah": True, "backgroundMediaControls": True,
            "adhanPausesWithoutResume": True, "restorationDoesNotAutoplay": True,
            "prayerAndBlockingSettingsUnchanged": True}


def offline_ayah_and_saved_marks(check_saved_marks=True):
    # Reuse the same checked full-surah download for clipped ayah listening.
    # The app must not require a second set of per-ayah audio files.
    select(SURAH)
    tap_control("audio_download_player")
    finished = wait_state(lambda s: (downloadable(SURAH, s) or {}).get("status") == "COMPLETED",
                          "Surah download for offline ayah test did not complete", 150)
    expected = next(t["size_bytes"] for t in json.loads(Path(
        "app/src/main/assets/quran_audio/alafasy.json").read_text())["tracks"] if t["surah_id"] == SURAH)
    assert downloadable(SURAH, finished)["totalBytes"] == expected
    assert downloadable(SURAH, finished)["bytesDownloaded"] >= expected
    online(False)
    # Restart releases buffered media and any old cache reads. Restoring the
    # paused item does not prepare a source. Clear temporary bytes through the
    # real settings controls so offline success can only use the explicit copy.
    select(SURAH, restart=True)
    before_clear = probe()["storage"]
    tap_control("audio_storage_open")
    tap_control("audio_clear_cache")
    tap_control("audio_confirm_clear_cache")
    cleared = wait_state(lambda s: not s["storage"]["clearingCache"] and s["storage"]["temporaryBytes"] == 0,
                         "Temporary cache was not empty before offline playback")
    assert downloadable(SURAH, cleared)["status"] == "COMPLETED"
    assert downloadable(SURAH, cleared)["totalBytes"] == expected
    assert downloadable(SURAH, cleared)["bytesDownloaded"] >= expected
    assert cleared["storage"]["downloadedBytes"] == before_clear["downloadedBytes"], \
        "Clearing temporary audio removed explicit download bytes"
    capture("learning-offline-cache-cleared")
    tap_control("audio_storage_close")
    # A previously selected ayah must not make the full-surah player's Listen
    # button silently resume that clipped item just because the surah matches.
    tap_control("audio_play_pause")
    full = playing(SURAH, timeout=30)["player"]
    assert full.get("ayah") is None and full["durationMs"] > 45_000, full
    pause()
    open_reader(restart=True)
    ayah_sheet()
    assert not probe()["player"]["playWhenReady"]
    tap_control("ayah_repeat_1")
    tap_control("ayah_speed_75")
    tap_control("ayah_play_pause")
    offline = verse_playing(timeout=30)
    verify_clip(offline)
    verse_paused()
    after_clip = probe()
    assert after_clip["storage"]["temporaryBytes"] == 0, "Offline clip populated temporary audio unexpectedly"
    assert downloadable(SURAH, after_clip)["status"] == "COMPLETED"
    assert after_clip["storage"]["downloadedBytes"] == before_clear["downloadedBytes"]
    capture("learning-offline-ayah")
    tap_control("ayah_stop")
    result = {"sameFullSurahDownload": True, "downloadBytes": expected,
              "temporaryBytesBeforeClear": before_clear["temporaryBytes"],
              "temporaryCacheClearedBeforeOfflinePlayback": True,
              "temporaryCacheRemainsEmptyAfterOfflineClip": True,
              "clearCachePreservesExplicitDownload": True,
              "offlineAfterProcessRestart": True}
    if not check_saved_marks:
        online(True)
        return result
    # Bookmark and note are real local user operations on the selected verse;
    # audio selection must not retarget them to another verse or erase them.
    tap_control("quran_action_bookmark")
    if find_control(ui(), "quran_ayah_sheet") is None:
        ayah_sheet()
    tap_control("quran_note_open")
    observe("quran_stop_note")
    tap_control("quran_stop_note")
    shell("input", "text", "Ayah%stesting%snote")
    shell("input", "keyevent", "KEYCODE_BACK")
    tap_control("quran_stop_save")
    note = prefs("quran_reading")
    assert "114:1" in note.get("bookmarks", []) and note.get("note_114:1") == "Ayah testing note", note
    open_reader(restart=True)
    restored = prefs("quran_reading")
    assert "114:1" in restored.get("bookmarks", []) and restored.get("note_114:1") == "Ayah testing note"
    ayah_sheet()
    assert not probe()["player"]["playWhenReady"]
    capture("learning-saved-ayah-actions")
    online(True)
    result["selectedAyahBookmarkAndNoteSurviveRestart"] = True
    return result


def isolated_offline_check():
    """Seed just the media prerequisite; do not rerun repetition/background tests."""
    online(True)
    shell("wm", "size", "1080x2400")
    shell("settings", "put", "system", "font_scale", "1.0")
    seed("dark", onboarded=True, dismissed=True)
    open_reader(restart=True)
    ayah_sheet()
    assert not probe()["storage"]["downloads"], "Focused offline check requires a fresh debug profile"
    assert not probe()["player"]["playWhenReady"]
    tap_control("ayah_play_pause")
    verify_clip(verse_playing())
    tap_control("ayah_stop")
    primed = wait_state(lambda s: not s["player"]["playWhenReady"] and s["storage"]["temporaryBytes"] > 0,
                        "Real streaming did not populate temporary cache for the focused check")
    result = offline_ayah_and_saved_marks(check_saved_marks=False)
    result["temporaryCachePrimedByRealStream"] = primed["storage"]["temporaryBytes"]
    return result


def accessible_ayah_controls():
    shell("wm", "size", "960x1920")
    shell("settings", "put", "system", "font_scale", "1.5")
    put_pref("appearance", "string", "light")
    open_reader(restart=True)
    require_reachable("quran_audio_reader_entry")
    require_reachable("quran_quick_bookmark")
    require_reachable("quran_fullscreen")
    tap_control("quran_audio_reader_entry")
    observe("quran_ayah_sheet")
    for tag in ("ayah_play_pause", "ayah_repeat_10", "ayah_repeat_0", "ayah_speed_150", "ayah_continue"):
        require_reachable(tag)
    capture("learning-ayah-large-light")
    shell("wm", "size", "1920x1080")
    time.sleep(.7)
    observe("quran_ayah_sheet")
    for tag in ("ayah_play_pause", "ayah_repeat_3", "ayah_speed_75", "ayah_close"):
        require_reachable(tag)
    assert not probe()["player"]["playWhenReady"], "Rotation started ayah playback"
    capture("learning-ayah-landscape-large")
    return {"fontScale": 1.5, "widthDp": 320, "lightTheme": True,
            "speedRepeatAndPlayerReachable": True, "landscapeReachable": True,
            "rotationDoesNotAutoplay": True}


def illustrated_guide():
    shell("wm", "size", "1080x2400")
    shell("settings", "put", "system", "font_scale", "1.0")
    seed("dark", onboarded=False)
    start("onboarding_finish")
    observe("onboarding_step_4")
    tap_control("onboarding_next")
    page("welcome")
    visible_guide_image()
    tap_control("guide_skip")
    assert prefs().get(GUIDE_KEY) == "true"
    start()
    assert find_control(observe("home_prayer_journal"), "guide_screen") is None
    start("more")
    tap_control("settings_guide")
    before = protected_preferences()
    before_permissions = permissions()
    for topic in TOPICS:
        page(topic)
        for step in (1, 2, 3):
            tap_control("guide_step_" + str(step))
            observe(f"guide_image_{topic}_{step}")
            visible_guide_image()
        if topic in {"welcome", "limits", "quran"}:
            capture("learning-illustrated-guide-" + topic)
        if topic == "quran":
            tap_control("guide_zoom")
            observe("guide_zoom_dialog")
            require_reachable("guide_zoom_close")
            capture("learning-guide-screenshot-zoom")
            tap_control("guide_zoom_close")
            tap_control("guide_open")
            observe("quran_index_list")
            shell("input", "keyevent", "KEYCODE_BACK")
            page("quran")
        if topic != TOPICS[-1]:
            tap_control("guide_next")
    assert protected_preferences() == before and permissions() == before_permissions
    tap_control("guide_finish")
    observe("settings_guide")
    shell("wm", "size", "960x1920")
    shell("settings", "put", "system", "font_scale", "1.5")
    put_pref("appearance", "string", "light")
    start("more")
    tap_control("settings_guide")
    choose_topic("limits")
    visible_guide_image()
    require_reachable("guide_next")
    tap_control("guide_zoom")
    require_reachable("guide_zoom_close")
    capture("learning-guide-large-light-zoom")
    tap_control("guide_zoom_close")
    shell("wm", "size", "1920x1080")
    page("limits")
    require_reachable("guide_next")
    require_reachable("guide_close")
    capture("learning-guide-landscape-large")
    tap_control("guide_close")
    return {"freshSkipPersists": True, "settingsReplay": True, "topics": list(TOPICS),
            "allThirtyScreenshotStepsAndHotspots": True, "screenshotZoom": True,
            "openRealSectionAndReturn": True, "largeFontAndLandscapeControls": True,
            "settingsAndPermissionsUnchanged": True}


def visible_guide_image():
    # The generic locator accepts 48px of a control, less than the substantial
    # image area required here. Scroll only its real ancestor when a caption
    # pushes the image below the fold; the zoom dialog proves whole-image access.
    locate_control("guide_screenshot")
    minimum_height = 80 * DENSITY / 160
    previous_geometry = None
    for attempt in range(4):
        root = ui()
        node = find_control(root, "guide_screenshot")
        raw = bounds(node)
        left, top, right, bottom = visible_bounds(root, node)
        geometry = (raw, (left, top, right, bottom))
        if bottom - top >= minimum_height or attempt == 3 or geometry == previous_geometry:
            break
        previous_geometry = geometry
        parents = {child: parent for parent in root.iter() for child in parent}
        owner = parents.get(node)
        while owner is not None and owner.get("scrollable") != "true":
            owner = parents.get(owner)
        if owner is None or (raw[1] >= top and raw[3] <= bottom):
            break
        ol, ot, oright, ob = visible_bounds(root, owner)
        if ob - ot < minimum_height:
            break
        distance = min(max(72, int(minimum_height - (bottom - top)) + 36), (ob - ot) // 3)
        middle = (ot + ob) // 2
        end = middle - distance if raw[3] > bottom else middle + distance
        x = ol + min(18, (oright - ol) // 8)
        shell("input", "swipe", x, middle, x, end, 300)
        time.sleep(.25)
    assert right - left >= 100 * DENSITY / 160 and bottom - top >= minimum_height, (
        "Guide screenshot is insufficiently visible", raw, (left, top, right, bottom))
    assert find_control(root, "guide_hotspot") is not None, "Screenshot marker layer missing"
    return root


if __name__ == "__main__":
    OUT.mkdir(exist_ok=True)
    (OUT / "screens").mkdir(exist_ok=True)
    mode = sys.argv[1] if len(sys.argv) > 1 else "all"
    assert mode in {"all", "audio", "offline", "guide"}, mode
    report = {"profile": "isolated_debug_emulator", "productionWrites": False,
              "previousSuitesRerun": False, "mode": mode, "groups": {}}
    try:
        shell("wm", "density", DENSITY)
        shell("am", "force-stop", PKG)
        shell("cmd", "locale", "set-app-locales", PKG, "--user", "0", "--locales", "ru")
        locale = shell("cmd", "locale", "get-app-locales", PKG, "--user", "0").strip()
        assert locale == f"Locales for {PKG} for user 0 are [ru]", locale
        actions = {}
        if mode in {"all", "audio"}:
            actions.update({"ayah_repeats_speed_background": learning_audio,
                            "offline_ayah_saved_marks": offline_ayah_and_saved_marks,
                            "accessible_ayah_controls": accessible_ayah_controls})
        if mode == "offline":
            actions["offline_download_without_temporary_cache"] = isolated_offline_check
        if mode in {"all", "guide"}:
            actions["illustrated_guide"] = illustrated_guide
        for ACTIVE_GROUP, action in actions.items():
            print("Learning check:", ACTIVE_GROUP, flush=True)
            report["groups"][ACTIVE_GROUP] = action()
            (OUT / "learning-passed.json").write_text(json.dumps(report, ensure_ascii=False, indent=2))
        crashes = adb("logcat", "-d", "-b", "crash")
        (OUT / "crashes.txt").write_text(crashes)
        assert not re.search(r"FATAL EXCEPTION|Fatal signal", crashes), "App crash recorded"
        report["complete"] = True
        (OUT / "learning-passed.json").write_text(json.dumps(report, ensure_ascii=False, indent=2))
    except Exception:
        (OUT / "learning-failure.txt").write_text(ACTIVE_GROUP + "\n" + traceback.format_exc())
        try:
            capture("learning-failure")
            (OUT / "learning-logcat.txt").write_text(adb("logcat", "-d", "-s", "ExoPlayerImpl", "AndroidRuntime"))
        except Exception:
            pass
        raise
    finally:
        shell("cmd", "connectivity", "airplane-mode", "disable")
        shell("svc", "wifi", "enable")
        shell("svc", "data", "enable")
        shell("settings", "put", "system", "font_scale", "1.0")
        shell("wm", "size", "1080x1920")
