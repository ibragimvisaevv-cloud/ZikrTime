"""Authenticated Mini App inbox, private attachments and owner-only usage counts."""
import base64
import hashlib
import math
import re
import secrets
import time

from feedback import KINDS, STATUSES


class CommunityError(Exception):
    def __init__(self, message, code=400):
        super().__init__(message)
        self.code = code


def owner_only(bot, uid):
    if uid != bot.db.get("owner"):
        raise CommunityError("Раздел доступен владельцу", 403)


def touch(bot, uid, source):
    now = int(time.time())
    user = bot.user(uid)
    changed = False
    if not user.get("first_seen"):
        user["first_seen"] = now
        changed = True
    if now - user.get("last_seen", 0) >= 900:
        user["last_seen"] = now
        changed = True
    if source == "start" and not user.get("started_at"):
        user["started_at"] = now
        changed = True
    if source == "miniapp" and not user.get("miniapp_at"):
        user["miniapp_at"] = now
        changed = True
    bot.dirty = bot.dirty or changed


def request_nonce(req):
    value = str(req.get("request_id") or "")
    if not re.fullmatch(r"[a-zA-Z0-9_-]{12,80}", value):
        raise CommunityError("Обновите форму и попробуйте ещё раз")
    return value


def bounded_text(value, maximum, minimum=0):
    if not isinstance(value, str) or not minimum <= len(value.strip()) <= maximum:
        raise CommunityError(f"Текст должен содержать от {minimum} до {maximum} символов")
    return value.strip()


def owned_photos(bot, uid, ids):
    if not isinstance(ids, list) or len(ids) > 3 or not all(isinstance(i, str) for i in ids) or len(set(ids)) != len(ids):
        raise CommunityError("Можно приложить до 3 фотографий")
    result = []
    for photo_id in ids:
        photo = bot.db.get("private_photos", {}).get(str(photo_id))
        if not photo or photo["author"] != uid:
            raise CommunityError("Фотография недоступна", 404)
        result.append({"private_id": photo_id})
    return result


def photo_upload(bot, uid, req):
    nonce = request_nonce(req)
    photos = bot.db.setdefault("private_photos", {})
    existing = next((p for p in photos.values() if p["author"] == uid and p["nonce"] == nonce), None)
    if existing:
        return {"photo_id": existing["id"]}
    now = int(time.time())
    if sum(p["author"] == uid and p["created"] > now - 86400 for p in photos.values()) >= 15:
        raise CommunityError("За сутки можно загрузить до 15 фотографий", 429)
    encoded = req.get("data")
    if not isinstance(encoded, str) or len(encoded) > 3_400_000:
        raise CommunityError("Фото должно быть меньше 2,5 МБ")
    try:
        data = base64.b64decode(encoded.split(",")[-1], validate=True)
    except ValueError:
        raise CommunityError("Не удалось прочитать фото")
    if not data or len(data) > 2_500_000:
        raise CommunityError("Фото должно быть меньше 2,5 МБ")
    if data.startswith(b"\xff\xd8\xff"):
        mime = "image/jpeg"
    elif data.startswith(b"\x89PNG\r\n\x1a\n"):
        mime = "image/png"
    else:
        raise CommunityError("Выберите JPEG или PNG")
    # Deterministic key makes a retried optimistic transaction overwrite the same private object.
    photo_id = hashlib.sha256(f"{uid}:{nonce}".encode()).hexdigest()[:32]
    key = f"community/photos/{uid}/{photo_id}"
    bot.data.put(key, data, mime, cache="private, no-store")
    photos[photo_id] = {"id": photo_id, "key": key, "author": uid, "created": now, "mime": mime, "nonce": nonce}
    bot.dirty = True
    return {"photo_id": photo_id}


def photo_read(bot, uid, req):
    photo = bot.db.get("private_photos", {}).get(str(req.get("photo_id") or ""))
    if not photo or (photo["author"] != uid and uid != bot.db.get("owner")):
        raise CommunityError("Фотография недоступна", 404)
    raw = bot.data.get(photo["key"])
    if not raw:
        raise CommunityError("Фотография недоступна", 404)
    return {"data": "data:" + photo["mime"] + ";base64," + base64.b64encode(raw).decode()}


def ticket_out(ticket, details=False):
    fields = ("id", "kind", "status", "created", "updated", "text", "context")
    out = {key: ticket.get(key, "") for key in fields}
    if not details:
        out["text"] = out["text"][:180]
    else:
        out["photos"] = [p["private_id"] for p in ticket.get("photos", []) if "private_id" in p]
        out["chat_photos"] = sum("file_id" in p for p in ticket.get("photos", []))
        out["replies"] = [{"text": r["text"], "by_owner": r["by_owner"], "created": r["created"]}
                          for r in ticket.get("replies", [])]
    return out


def ticket_access(bot, uid, req):
    ticket = bot.db.get("feedback_tickets", {}).get(str(req.get("tid") or ""))
    if not ticket or (ticket["author"] != uid and uid != bot.db.get("owner")):
        raise CommunityError("Обращение недоступно", 404)
    return ticket


def inbox(bot, uid, req):
    all_tickets = req.get("inbox") is True
    if all_tickets:
        owner_only(bot, uid)
    tickets = [t for t in bot.db.get("feedback_tickets", {}).values() if all_tickets or t["author"] == uid]
    tickets.sort(key=lambda t: (t["updated"], str(t["id"])), reverse=True)
    offset = page_offset(req)
    return {"tickets": [ticket_out(t) for t in tickets[offset:offset + 30]], "total": len(tickets), "offset": offset}


def ticket_create(bot, uid, req):
    nonce = request_nonce(req)
    tickets = bot.db.setdefault("feedback_tickets", {})
    existing = next((t for t in tickets.values() if t["author"] == uid and t.get("request_id") == nonce), None)
    if existing:
        return {"ticket": ticket_out(existing, True)}
    if not bot.db.get("owner"):
        raise CommunityError("Приём обращений пока настраивается", 503)
    kind = req.get("kind")
    if kind not in KINDS:
        raise CommunityError("Выберите тип обращения")
    now = int(time.time())
    if sum(t["author"] == uid and t["created"] > now - 86400 for t in tickets.values()) >= 5:
        raise CommunityError("За сутки можно отправить до 5 обращений", 429)
    message = bounded_text(req.get("text"), 3000, 10)
    photos = owned_photos(bot, uid, req.get("photos", []))
    context = bounded_text(req.get("context", ""), 120)
    bot.db["feedback_sequence"] = bot.db.get("feedback_sequence", 0) + 1
    tid = str(bot.db["feedback_sequence"])
    ticket = {"id": tid, "author": uid, "kind": kind, "text": message, "photos": photos,
              "context": context, "created": now, "updated": now, "status": "new", "replies": [],
              "reply_ids": [], "request_id": nonce}
    tickets[tid] = ticket
    bot.dirty = True
    return {"ticket": ticket_out(ticket, True)}


def ticket_update(bot, uid, req):
    ticket = ticket_access(bot, uid, req)
    nonce = request_nonce(req)
    reply_id = f"mini:{uid}:{nonce}"
    if reply_id in ticket.get("reply_ids", []):
        return {"ticket": ticket_out(ticket, True)}
    is_owner = uid == bot.db.get("owner")
    status = req.get("status")
    if status is not None:
        owner_only(bot, uid)
        if status not in STATUSES:
            raise CommunityError("Неизвестный статус")
    if not is_owner and ticket["status"] in ("closed", "rejected"):
        raise CommunityError("Обращение закрыто. Создайте новое при необходимости")
    message = bounded_text(req.get("text", ""), 2000)
    if not status and not message:
        raise CommunityError("Введите ответ")
    if len(ticket.get("replies", [])) >= 50:
        raise CommunityError("В обращении уже 50 ответов")
    now = int(time.time())
    if message:
        ticket.setdefault("replies", []).append({"text": message, "by_owner": is_owner, "created": now, "photos": []})
    ticket["status"] = status or ("working" if is_owner else "new")
    ticket["updated"] = now
    ticket.setdefault("reply_ids", []).append(reply_id)
    bot.dirty = True
    return {"ticket": ticket_out(ticket, True)}


def place_out(place):
    out = {k: place.get(k, "") for k in ("id", "name", "kind", "address", "lat", "lon", "status", "note", "hours", "who", "wudu", "created", "source", "source_url", "possible_duplicate")}
    out["photos"] = [p["private_id"] for p in place.get("private_photos", [])]
    return out


def place_create(bot, uid, req):
    nonce = request_nonce(req)
    existing = next((p for p in bot.db["places"].values() if p.get("author") == uid and p.get("request_id") == nonce), None)
    if existing:
        return {"place": place_out(existing)}
    now = int(time.time())
    if sum(p.get("author") == uid and p.get("created", 0) > now - 86400 for p in bot.db["places"].values()) >= 5:
        raise CommunityError("За сутки можно предложить до 5 мест", 429)
    try:
        lat, lon = float(req.get("lat")), float(req.get("lon"))
    except (TypeError, ValueError):
        raise CommunityError("Укажите широту и долготу места")
    if not math.isfinite(lat) or not math.isfinite(lon) or not -90 <= lat <= 90 or not -180 <= lon <= 180:
        raise CommunityError("Проверьте координаты места")
    kind = req.get("kind")
    if kind not in ("mosque", "room", "other"):
        raise CommunityError("Выберите тип места")
    pid = "m" + secrets.token_hex(5)
    place = {"id": pid, "kind": kind, "name": bounded_text(req.get("name"), 100, 2),
             "lat": lat, "lon": lon, "address": bounded_text(req.get("address", ""), 200),
             "hours": bounded_text(req.get("hours", ""), 100), "note": bounded_text(req.get("note", ""), 1000),
             "who": req.get("who") if req.get("who") in ("all", "men", "women") else "",
             "wudu": req.get("wudu") if isinstance(req.get("wudu"), bool) else None,
             "status": "pending", "author": uid, "created": now, "request_id": nonce,
             "private_photos": owned_photos(bot, uid, req.get("photos", [])), "source": "community"}
    bot.db["places"][pid] = place
    bot.dirty = True
    return {"place": place_out(place)}


def places(bot, uid, req):
    all_places = req.get("inbox") is True
    if all_places:
        owner_only(bot, uid)
    rows = [p for p in bot.db["places"].values() if (p["status"] == "pending" if all_places else p.get("author") == uid)]
    rows.sort(key=lambda p: p.get("created", 0), reverse=True)
    offset = page_offset(req)
    return {"places": [place_out(p) for p in rows[offset:offset + 30]], "total": len(rows), "offset": offset}


def place_moderate(bot, uid, req):
    owner_only(bot, uid)
    pid, action = str(req.get("pid") or ""), req.get("do")
    if action not in ("ok", "no", "hide") or pid not in bot.db["places"]:
        raise CommunityError("Место или действие недоступно", 404)
    bot.moderate_place(uid, action, pid)
    return {"place": place_out(bot.db["places"][pid])}


def page_offset(req):
    try:
        offset = int(req.get("offset", 0))
    except (TypeError, ValueError):
        raise CommunityError("Неверная страница")
    if offset < 0 or offset > 1_000_000:
        raise CommunityError("Неверная страница")
    return offset


def users(bot, uid, req):
    owner_only(bot, uid)
    now = int(time.time())
    rows = sorted(bot.db["users"].items(), key=lambda item: item[1].get("last_seen", 0), reverse=True)
    offset = page_offset(req)
    counts = {"total": len(rows), "started": sum(bool(u.get("started_at")) for _, u in rows),
              "miniapp": sum(bool(u.get("miniapp_at")) for _, u in rows),
              "active7": sum(u.get("last_seen", 0) >= now - 7 * 86400 for _, u in rows),
              "active30": sum(u.get("last_seen", 0) >= now - 30 * 86400 for _, u in rows)}
    return {"stats": counts, "users": [{"id": int(key), "last_seen": u.get("last_seen"),
             "first_seen": u.get("first_seen"), "started": bool(u.get("started_at"))} for key, u in rows[offset:offset + 30]],
            "offset": offset}


ACTIONS = {"private_photo_upload": photo_upload, "private_photo": photo_read, "tickets": inbox,
           "ticket_create": ticket_create, "ticket": lambda b, u, r: {"ticket": ticket_out(ticket_access(b, u, r), True)},
           "ticket_update": ticket_update, "place_create": place_create, "my_places": places,
           "mod_place": place_moderate, "users": users}
