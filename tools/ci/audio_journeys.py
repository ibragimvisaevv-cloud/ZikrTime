"""Real Alafasy playback/download journeys on a disposable Android 15 profile.

Only small, publisher-hosted recordings are used. QA snapshots are read-only:
all play, pause, seek, download, cancel and delete operations use real UI buttons.
No prayer, blocking, payment, message or other production write is performed.
"""
import traceback
import uuid
from pathlib import Path

# Reuse established ADB, visible-bound, screenshot and synthetic-profile helpers.
# Only definitions are loaded; no previous guide/map/product test suite is run.
helpers = Path("tools/ci/guide_journeys.py")
exec(compile(helpers.read_text().split("\ndef fresh_skip_and_upgrade():")[0], str(helpers), "exec"))

STREAM = 98
OFFLINE = 114
UNCACHED = 108
EXPECTED_OFFLINE_BYTES = 807637
ACTIVE_GROUP = "initialization"

# AlertDialog owns a separate Compose window and does not inherit the screen's
# testTagsAsResourceId flag. Match only these known dialogs by their visible
# title and exact label, then tap the real enabled clickable ancestor.
AUDIO_DIALOG_CONTROLS = {
    "audio_wifi_only": ("Память для аудио", "Wi-Fi и другие безлимитные сети"),
    "audio_clear_cache": ("Память для аудио", "Очистить временный кэш"),
    "audio_storage_close": ("Память для аудио", "Закрыть"),
    "audio_confirm_clear_cache": ("Очистить временное аудио?", "Очистить временный кэш"),
    "audio_confirm_delete": ("Удалить скачанную суру?", "Удалить"),
}
_resource_find_control = find_control


def find_control(root, identifier, attribute="resource-id"):
    tagged = _resource_find_control(root, identifier, attribute)
    if tagged is not None or attribute != "resource-id" or identifier not in AUDIO_DIALOG_CONTROLS:
        return tagged
    title_text, label_text = AUDIO_DIALOG_CONTROLS[identifier]
    titles = [n for n in root.iter("node")
              if n.get("text") == title_text and n.get("package") == PKG]
    if len(titles) != 1 or visible_bounds(root, titles[0]) != bounds(titles[0]):
        return None
    parents = {child: parent for parent in root.iter() for child in parent}
    window = titles[0]
    while window is not None and window.get("resource-id") != "android:id/content":
        window = parents.get(window)
    if window is None:
        return None
    matches = []
    for label in window.iter("node"):
        if label.get("text") != label_text:
            continue
        target = label
        while target is not None and target is not window and target.get("clickable") != "true":
            target = parents.get(target)
        if target is not None and target is not window and target.get("enabled") == "true":
            if all(target is not existing for existing in matches):
                matches.append(target)
    assert len(matches) <= 1, (identifier, "Ambiguous dialog action")
    return matches[0] if matches else None


def dialog_visible_bounds(root, node):
    # A non-scrollable ScrollView still clips the expanded semantics touch area.
    area = list(visible_bounds(root, node))
    parents = {child: parent for parent in root.iter() for child in parent}
    parent = parents.get(node)
    while parent is not None:
        if parent.get("class") in {"android.widget.ScrollView", "android.widget.HorizontalScrollView"}:
            left, top, right, bottom = bounds(parent)
            area = [max(area[0], left), max(area[1], top), min(area[2], right), min(area[3], bottom)]
        parent = parents.get(parent)
    return tuple(area)


def open_route(route="quran_audio", surah=0, restart=False):
    args = ["am", "start"]
    if restart:
        args.append("-S")
    args += ["-W", "-n", PKG + "/.MainActivity", "--es", "qa_screen", route,
             "--es", "qa_lang", "ru", "--ez", "qa_tilt", "false", "--ez", "qa_parallax", "false"]
    if surah:
        args += ["--ei", "qa_surah", str(surah)]
    shell(*args)
    time.sleep(.5)


def probe():
    identifier = uuid.uuid4().hex
    shell("am", "start", "-W", "-n", PKG + "/.MainActivity",
          "--ez", "qa_audio_probe", "true", "--es", "qa_audio_probe_id", identifier)
    for _ in range(5):
        raw = shell("run-as", PKG, "cat", "files/qa-audio-state.json")
        snapshot = json.loads(raw)
        if snapshot.get("probeId") == identifier:
            with (OUT / "audio-snapshots.jsonl").open("a") as handle:
                handle.write(json.dumps({"group": ACTIVE_GROUP, **snapshot}) + "\n")
            return snapshot
        time.sleep(.2)
    raise AssertionError("Audio diagnostic snapshot is stale")


def wait_state(predicate, description, timeout=45):
    deadline = time.monotonic() + timeout
    latest = {}
    while time.monotonic() < deadline:
        latest = probe()
        if predicate(latest):
            return latest
        time.sleep(.7)
    raise AssertionError(description + ": " + json.dumps(latest, ensure_ascii=False))


def audio_ready(surah=None):
    observe("audio_screen")
    snapshot = wait_state(lambda s: s["storage"]["ready"] and s["player"]["connected"], "Audio screen failed to connect")
    assert not snapshot["player"]["playWhenReady"], "Opening the audio screen started playback"
    if surah is not None:
        # Selection is UI-only until the user taps Listen; a previous player ID
        # must never be interpreted as evidence that the selected surah played.
        locate_control("audio_play_pause")
    return snapshot


def playing(surah, timeout=60):
    return wait_state(lambda s: s["player"]["surahId"] == surah and s["player"]["isPlaying"],
                      "Selected recording did not play", timeout)


def pause():
    state = probe()
    if state["player"]["playWhenReady"]:
        tap_control("audio_play_pause")
    return wait_state(lambda s: not s["player"]["playWhenReady"] and not s["player"]["isPlaying"], "Pause did not stop playback")


def confirm_if_present(tag):
    root = ui()
    if find_control(root, tag) is not None:
        tap_control(tag)


def downloadable(surah, state):
    return state["storage"]["downloads"].get(str(surah))


def online(enabled):
    shell("cmd", "connectivity", "airplane-mode", "disable" if enabled else "enable")
    shell("svc", "wifi", "enable" if enabled else "disable")
    shell("svc", "data", "enable" if enabled else "disable")
    deadline = time.monotonic() + 40
    while time.monotonic() < deadline:
        text = shell("dumpsys", "connectivity")
        match = re.search(r"Active default network:\s*(\S+)", text)
        if match and ((match.group(1).lower() not in {"none", "null", "0"}) == enabled):
            (OUT / ("audio-network-online.txt" if enabled else "audio-network-offline.txt")).write_text(text)
            if enabled:
                time.sleep(2)
            return
        time.sleep(.8)
    raise AssertionError("Could not verify emulator network state " + str(enabled))


def select(surah, restart=False):
    open_route(surah=surah, restart=restart)
    observe("audio_screen")
    wait_state(lambda s: s["storage"]["ready"] and s["player"]["connected"], "Audio connection not ready")
    locate_control("audio_play_pause")


def require_reachable(tag, minimum_dp=32):
    node = locate_control(tag)
    root = ui()
    node = find_control(root, tag)
    area = visible_bounds(root, node)
    raw = bounds(node)
    if tag == "audio_wifi_only":
        # Compose expands the toggle's touch target beyond its visible 42dp row.
        # Require a fully readable label and enough actual in-viewport touch area.
        labels = [n for n in node.iter("node") if n.get("text") == AUDIO_DIALOG_CONTROLS[tag][1]]
        assert len(labels) == 1, (tag, "Wi-Fi setting label missing")
        label = labels[0]
        assert dialog_visible_bounds(root, label) == bounds(label), (tag, "Setting label is clipped")
        assert node.get("enabled") == "true" and node.get("clickable") == "true"
        area = dialog_visible_bounds(root, node)
        assert area[2] - area[0] >= minimum_dp * DENSITY / 160, (tag, "Touch target too narrow", area)
    else:
        assert area == raw, (tag, "Control is partially clipped", raw, area)
    assert area[3] - area[1] >= minimum_dp * DENSITY / 160, (tag, "Touch target too small", area)
    return root


def streaming_background_and_interruption():
    online(True)
    shell("wm", "size", "1080x1920")
    shell("settings", "put", "system", "font_scale", "1.0")
    seed("dark", onboarded=True, dismissed=True)
    # The existing sound picker is visible only when the alarm setting is on.
    # This disposable profile has no selected days, so nextCityFajr returns null
    # and no Fajr signal is scheduled.
    put_pref("alarm_on", "boolean", "true")
    put_pref("alarm_days", "set", [])
    fixture = prefs()
    assert fixture.get("alarm_on") == "true" and fixture.get("alarm_days") == []
    open_route("quran", restart=True)
    observe("quran_index_list")
    tap_control("quran_audio_entry")
    initial = audio_ready()
    assert not initial["storage"]["downloads"], "A new install must not download recordings automatically"
    assert initial["storage"]["wifiOnly"], "Downloads must default to unmetered networks"
    capture("audio-index-entry-dark")
    before = protected_preferences()

    select(STREAM)
    assert not probe()["player"]["playWhenReady"]
    tap_control("audio_play_pause")
    active = playing(STREAM)
    assert active["player"]["durationMs"] > 20_000, "Real recording duration was not detected"
    capture("audio-playing-dark")
    paused = pause()["player"]
    tap_control("audio_seek_forward")
    sought = probe()["player"]
    expected = min(paused["positionMs"] + 15_000, sought["durationMs"])
    assert abs(sought["positionMs"] - expected) <= 1_200, ("Forward seek", paused, sought)
    assert not sought["playWhenReady"], "Seeking a paused recording resumed it"
    tap_control("audio_seek_back")
    rewound = probe()["player"]
    assert abs(rewound["positionMs"] - max(0, sought["positionMs"] - 15_000)) <= 1_200
    tap_control("audio_play_pause")
    started = playing(STREAM)["player"]
    shell("input", "keyevent", "KEYCODE_HOME")
    time.sleep(4)
    sessions = shell("dumpsys", "media_session")
    notifications = shell("dumpsys", "notification", "--noredact")
    (OUT / "audio-background-media-session.txt").write_text(sessions)
    (OUT / "audio-background-notifications.txt").write_text(notifications)
    assert PKG in sessions and "PlaybackState" in sessions, "System media session missing in background"
    assert PKG in notifications and "MediaStyle" in notifications, "Media notification missing in background"
    returned = probe()["player"]
    assert returned["surahId"] == STREAM and returned["isPlaying"], "Playback stopped when the screen went to the background"
    assert returned["positionMs"] >= started["positionMs"] + 2_000, "Background playback did not advance"
    shell("input", "keyevent", "KEYCODE_MEDIA_PAUSE")
    wait_state(lambda s: not s["player"]["playWhenReady"], "System media pause was not handled")
    tap_control("audio_play_pause")
    playing(STREAM)

    # Exercise the real adhan-preview button in the enabled fixture with no
    # scheduled days. Preview playback must not change that fixture or resume
    # Quran automatically when the preview stops.
    open_route("alarm")
    tap_control("adhan_preview_athan_khalaf_one")
    interrupted = wait_state(lambda s: not s["player"]["playWhenReady"] and not s["player"]["isPlaying"],
                             "Adhan preview did not pause Quran playback")
    capture("audio-paused-by-adhan-preview")
    tap_control("adhan_preview_athan_khalaf_one")
    time.sleep(1)
    assert not probe()["player"]["playWhenReady"], "Stopping adhan unexpectedly resumed Quran"
    assert protected_preferences() == before, "Audio or preview changed prayer/blocking preferences"
    fixture_after = prefs()
    assert fixture_after.get("alarm_on") == "true" and fixture_after.get("alarm_days") == [], \
        "Preview changed the enabled alarm fixture or its empty schedule"
    select(STREAM)
    capture("audio-return-after-interruption")
    assert not probe()["player"]["playWhenReady"]

    # Seek using the real slider, then replay an actually ended recording.
    tap_control("audio_play_pause")
    playing(STREAM)
    slider = locate_control("audio_seek")
    left, top, right, bottom = bounds(slider)
    shell("input", "swipe", (left + right) // 2, (top + bottom) // 2,
          right - 2, (top + bottom) // 2, 450)
    wait_state(lambda s: s["player"]["surahId"] == STREAM and s["player"]["ended"],
               "Seeking to the end did not produce ENDED", 30)
    ended_root = ui()
    ended_button = find_control(ended_root, "audio_play_pause")
    assert ended_button is not None
    assert "Слушать" in [n.get("text", "") for n in ended_button.iter("node")], \
        "An ended recording must offer Listen, not Pause"
    capture("audio-ended-ready-to-replay", ended_root)
    tap_control("audio_play_pause")
    replayed = playing(STREAM)["player"]
    assert 0 <= replayed["positionMs"] < 10_000, "Replay did not start near the beginning"

    # Stop saves the current position before clearing the player's media item.
    time.sleep(2)
    stop_started = time.monotonic()
    before_stop = probe()["player"]["positionMs"]
    tap_control("audio_stop")
    stopped_memory = prefs("quran_audio_playback_v1")
    saved_position = int(stopped_memory["position"])
    assert int(stopped_memory["surah"]) == STREAM
    assert before_stop <= saved_position <= before_stop + int((time.monotonic() - stop_started) * 1000) + 2_000, \
        ("Stop did not save its current position", before_stop, saved_position)
    select(STREAM, restart=True)
    restored_stop = audio_ready(STREAM)["player"]
    assert restored_stop["surahId"] == STREAM
    assert abs(restored_stop["positionMs"] - saved_position) <= 1_500
    assert not restored_stop["playWhenReady"] and not restored_stop["isPlaying"]
    capture("audio-stop-position-restored")
    return {"real_surah": STREAM, "play_pause_seek": True, "background_media_controls": True,
            "background_position_advances": True, "adhan_preview_pauses_without_auto_resume": True,
            "alarm_fixture_enabled_with_no_scheduled_days": True,
            "ended_offers_listen_and_restarts_from_beginning": True,
            "stop_position_survives_restart_without_autoplay": True,
            "prayer_and_blocking_settings_unchanged": True}


def downloaded_surah_works_offline():
    select(OFFLINE)
    tap_control("audio_download_player")
    finished = wait_state(lambda s: (downloadable(OFFLINE, s) or {}).get("status") == "COMPLETED",
                          "Real surah download did not complete", 150)
    item = downloadable(OFFLINE, finished)
    assert item["totalBytes"] == EXPECTED_OFFLINE_BYTES, item
    assert item["bytesDownloaded"] >= EXPECTED_OFFLINE_BYTES, item
    assert finished["storage"]["downloadedBytes"] >= EXPECTED_OFFLINE_BYTES
    capture("audio-downloaded-dark")

    # Stop releases active stream reads before cache maintenance; the real
    # explicit download remains in separate persistent storage.
    state = probe()["player"]
    if state["surahId"] is not None:
        # The visible selected surah can differ from the paused service item.
        select(state["surahId"])
        tap_control("audio_stop")
    tap_control("audio_storage_open")
    require_reachable("audio_wifi_only")
    # Toggle twice proves persistence/interaction without changing the user's default.
    tap_control("audio_wifi_only")
    assert not probe()["storage"]["wifiOnly"]
    tap_control("audio_wifi_only")
    assert probe()["storage"]["wifiOnly"]
    before_bytes = probe()["storage"]["downloadedBytes"]
    tap_control("audio_clear_cache")
    tap_control("audio_confirm_clear_cache")
    cleared = wait_state(lambda s: not s["storage"]["clearingCache"] and s["storage"]["temporaryBytes"] == 0,
                         "Temporary audio cache was not cleared")
    assert downloadable(OFFLINE, cleared)["status"] == "COMPLETED"
    assert cleared["storage"]["downloadedBytes"] == before_bytes, "Clearing temporary cache removed offline audio"
    capture("audio-cache-and-downloads")
    tap_control("audio_storage_close")

    online(False)
    select(OFFLINE, restart=True)
    audio_ready(OFFLINE)
    cold = wait_state(lambda s: (downloadable(OFFLINE, s) or {}).get("status") == "COMPLETED",
                      "Download lost after process restart")
    tap_control("audio_play_pause")
    first = playing(OFFLINE, timeout=30)["player"]
    time.sleep(3)
    later = probe()["player"]
    assert later["isPlaying"] and later["positionMs"] > first["positionMs"] + 1_000, "Offline playback did not advance"
    pause()
    capture("audio-offline-playback")
    return {"downloaded_surah": OFFLINE, "verified_size": EXPECTED_OFFLINE_BYTES,
            "offline_after_process_restart": True, "no_autoplay_on_restore": True,
            "clear_temporary_cache_preserves_download": True, "network_disabled": True}


def offline_error_cancel_retry_and_delete():
    select(UNCACHED)
    tap_control("audio_download_player")
    waiting = wait_state(lambda s: (downloadable(UNCACHED, s) or {}).get("status") in
                         {"QUEUED", "WAITING_FOR_WIFI", "WAITING_FOR_NETWORK", "PAUSED"}, "Offline download did not wait")
    assert downloadable(UNCACHED, waiting)["status"] != "COMPLETED", "Queued download falsely reported offline-ready"
    capture("audio-download-waits-for-network")
    tap_control("audio_cancel_player")
    wait_state(lambda s: downloadable(UNCACHED, s) is None, "Cancel did not remove pending download")
    tap_control("audio_play_pause")
    failed = wait_state(lambda s: s["player"]["surahId"] == UNCACHED and bool(s["player"].get("error")),
                        "Missing clear error for uncached offline playback", 90)
    assert not failed["player"]["isPlaying"]
    observe("audio_player_error")
    capture("audio-offline-error")
    online(True)
    # The main player action changes to Retry after an error.
    tap_control("audio_play_pause")
    playing(UNCACHED)
    pause()
    saved = probe()["player"]
    assert saved["positionMs"] > 0
    time.sleep(1)
    select(UNCACHED, restart=True)
    restored = audio_ready(UNCACHED)["player"]
    assert restored["surahId"] == UNCACHED and abs(restored["positionMs"] - saved["positionMs"]) <= 1_500, (saved, restored)
    time.sleep(2)
    assert not probe()["player"]["playWhenReady"], "Restart auto-started saved audio"
    capture("audio-restored-paused")

    select(OFFLINE)
    tap_control("audio_delete_player")
    observe("audio_confirm_delete")
    tap_control("audio_confirm_delete")
    deleted = wait_state(lambda s: downloadable(OFFLINE, s) is None, "Explicit deletion did not finish")
    assert deleted["storage"]["downloadedBytes"] < EXPECTED_OFFLINE_BYTES, "Deleted download still consumes its full storage"
    capture("audio-download-removed")
    return {"waiting_download_cancelled": True, "partial_not_offline_ready": True,
            "uncached_offline_error_visible": True, "online_retry_plays": True,
            "position_restored_without_autoplay": True, "explicit_download_deleted": True}


def large_font_reader_and_player():
    shell("wm", "size", "960x1920")
    shell("settings", "put", "system", "font_scale", "1.5")
    put_pref("appearance", "string", "light")
    open_route("quran", restart=True)
    observe("quran_index_list")
    require_reachable("quran_audio_entry")
    capture("audio-large-light-index-entry")
    tap_control("quran_search")
    shell("input", "text", "114")
    shell("input", "keyevent", "KEYCODE_BACK")
    tap_control("quran_surah_114")
    require_reachable("quran_audio_reader_entry")
    require_reachable("quran_quick_bookmark")
    require_reachable("quran_fullscreen")
    capture("audio-large-light-reader-entry")
    tap_control("quran_audio_reader_entry")
    audio_ready(OFFLINE)
    require_reachable("audio_play_pause")
    capture("audio-large-light-player")
    tap_control("audio_storage_open")
    require_reachable("audio_wifi_only")
    require_reachable("audio_clear_cache")
    capture("audio-large-light-storage")
    tap_control("audio_storage_close")
    shell("wm", "size", "1920x1080")
    time.sleep(.8)
    observe("audio_screen")
    require_reachable("audio_play_pause")
    capture("audio-landscape-large-player")
    require_reachable("audio_download_player")
    capture("audio-landscape-large-download")
    assert not probe()["player"]["playWhenReady"], "Rotation started audio"
    shell("input", "keyevent", "KEYCODE_BACK")
    require_reachable("quran_audio_reader_entry")
    require_reachable("quran_quick_bookmark")
    require_reachable("quran_fullscreen")
    capture("audio-landscape-large-reader")
    return {"font_scale": 1.5, "portrait_width_dp": 320, "palette": "light",
            "index_and_reader_entry_accessible": True, "reader_actions_preserved": True,
            "player_and_storage_actions_accessible": True, "landscape_buttons_accessible": True,
            "rotation_no_autoplay": True}


if __name__ == "__main__":
    OUT.mkdir(exist_ok=True)
    (OUT / "screens").mkdir(exist_ok=True)
    catalog = json.loads(Path("app/src/main/assets/quran_audio/alafasy.json").read_text())
    EXPECTED_OFFLINE_BYTES = next(t["size_bytes"] for t in catalog["tracks"] if t["surah_id"] == OFFLINE)
    report = {"profile": "isolated_debug_emulator", "audio_source": catalog["source"],
              "production_writes": False, "previous_suites_rerun": False, "groups": {}}
    try:
        shell("wm", "density", DENSITY)
        # Configure the isolated Android profile before any activity is created.
        # qa_lang then observes ru and does not recreate the first Compose tree.
        shell("am", "force-stop", PKG)
        shell("cmd", "locale", "set-app-locales", PKG, "--user", "0", "--locales", "ru")
        locale_state = shell("cmd", "locale", "get-app-locales", PKG, "--user", "0").strip()
        (OUT / "audio-app-locale.txt").write_text(locale_state + "\n")
        assert locale_state == f"Locales for {PKG} for user 0 are [ru]", locale_state
        report["app_locale"] = "ru"
        actions = {"stream_background_interruption": streaming_background_and_interruption,
                   "download_and_offline": downloaded_surah_works_offline,
                   "failure_cancel_retry_restore_delete": offline_error_cancel_retry_and_delete,
                   "large_font_reader_player": large_font_reader_and_player}
        for ACTIVE_GROUP, action in actions.items():
            print("Quran audio check:", ACTIVE_GROUP, flush=True)
            report["groups"][ACTIVE_GROUP] = action()
            (OUT / "audio-passed.json").write_text(json.dumps(report, ensure_ascii=False, indent=2))
        crashes = adb("logcat", "-d", "-b", "crash")
        (OUT / "crashes.txt").write_text(crashes)
        assert not re.search(r"FATAL EXCEPTION|Fatal signal", crashes), "App crash recorded"
        report["complete"] = True
        (OUT / "audio-passed.json").write_text(json.dumps(report, ensure_ascii=False, indent=2))
    except Exception:
        (OUT / "audio-failure.txt").write_text(ACTIVE_GROUP + "\n" + traceback.format_exc())
        try:
            capture("audio-failure")
            (OUT / "audio-logcat.txt").write_text(adb("logcat", "-d", "-s", "ExoPlayerImpl", "DownloadManager", "AndroidRuntime"))
        except Exception:
            pass
        raise
    finally:
        shell("cmd", "connectivity", "airplane-mode", "disable")
        shell("svc", "wifi", "enable")
        shell("svc", "data", "enable")
        shell("settings", "put", "system", "font_scale", "1.0")
        shell("wm", "size", "1080x1920")
