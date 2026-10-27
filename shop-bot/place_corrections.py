"""Optional place fields and moderated corrections; no live I/O on import."""
from datetime import date
import re
import secrets
import time

FIELDS = {
    "address": ("Адрес", 150),
    "hours": ("Часы доступа", 100),
    "access_conditions": ("Условия доступа", 300),
    "entrance": ("Вход и ориентиры", 300),
    "wudu": ("Место для омовения", 0),
    "women": ("Женская зона", 0),
    "source_details": ("Источник сведений", 300),
    "confirmed_at": ("Дата фактического подтверждения", 10),
}
EXTRA_FIELDS = ("access_conditions", "entrance", "source_details", "confirmed_at")


def value_for(field, text):
    if field not in FIELDS:
        raise ValueError("Неизвестное поле")
    text = text.strip()
    if text == "?":
        return None if field in ("wudu", "women") else ""
    if field in ("wudu", "women"):
        raise ValueError("Выберите «Есть», «Нет» или «Нет данных» кнопкой.")
    if not text or len(text) > FIELDS[field][1]:
        raise ValueError(f"Нужно от 1 до {FIELDS[field][1]} символов; ? означает «нет данных».")
    if field == "confirmed_at":
        try:
            day = date.fromisoformat(text)
        except ValueError:
            raise ValueError("Нужна фактическая дата в формате ГГГГ-ММ-ДД или ?.") from None
        if day > date.today() or not re.fullmatch(r"\d{4}-\d{2}-\d{2}", text):
            raise ValueError("Укажите прошедшую дату фактической проверки, не дату импорта.")
    return text


class PlaceCorrectionsMixin:
    def start_place_correction(self, uid, pid):
        p = self.db["places"].get(pid)
        if not p or p.get("status") != "approved":
            return self.send(uid, "Место не найдено или скрыто с карты. Откройте актуальный список в приложении.")
        self.user(uid)["state"] = {"flow": "place_fix", "place_id": pid, "step": "field"}
        self.dirty = True
        rows = [[{"text": title, "callback_data": "pfx:" + field}] for field, (title, _) in FIELDS.items()]
        rows.append([{"text": "Отмена", "callback_data": "menu"}])
        from html import escape
        self.send(uid, "Что изменилось у места <b>" + escape(p["name"]) + "</b>?\nПереход из приложения ещё не отправляет заявку.", {"inline_keyboard": rows})

    def correction_field(self, uid, field):
        st = self.user(uid).get("state") or {}
        if st.get("flow") != "place_fix" or st.get("step") != "field" or field not in FIELDS:
            return
        st.update(field=field, step="value")
        self.dirty = True
        if field in ("wudu", "women"):
            rows = [[{"text": title, "callback_data": "pfv:" + code}]
                    for code, title in (("yes", "Есть"), ("no", "Нет"), ("unknown", "Нет данных"))]
            return self.send(uid, FIELDS[field][0], {"inline_keyboard": rows})
        note = " Указывайте дату, когда сведения действительно проверялись (ГГГГ-ММ-ДД), а не импорт или обновление записи." if field == "confirmed_at" else ""
        self.send(uid, FIELDS[field][0] + ": напишите новое значение, или ? если оно неизвестно." + note)

    def correction_input(self, uid, st, text):
        if st.get("step") != "value":
            return self.send(uid, "Выберите поле или подтвердите отправку кнопкой. /cancel — отмена.")
        try:
            value = value_for(st["field"], text)
        except ValueError as exc:
            return self.send(uid, str(exc))
        self.correction_preview(uid, value)

    def correction_boolean(self, uid, code):
        st = self.user(uid).get("state") or {}
        if (st.get("flow") != "place_fix" or st.get("step") != "value"
                or st.get("field") not in ("wudu", "women") or code not in ("yes", "no", "unknown")):
            return
        self.correction_preview(uid, {"yes": True, "no": False, "unknown": None}[code])

    def correction_preview(self, uid, value):
        from html import escape
        st = self.user(uid)["state"]
        st.update(value=value, step="confirm")
        self.dirty = True
        shown = "есть" if value is True else "нет" if value is False else "нет данных" if value in (None, "") else str(value)
        place = self.db["places"].get(st["place_id"], {})
        self.send(uid, "Место: <b>" + escape(place.get("name", st["place_id"])) + "</b> (" + escape(st["place_id"]) + ")\nПредложение: <b>" + escape(FIELDS[st["field"]][0]) + "</b> → " + escape(shown)
                  + "\nОтправить на проверку владельцу? Публичная карточка изменится только после одобрения.",
                  {"inline_keyboard": [[{"text": "Отправить на проверку", "callback_data": "pfsend"}],
                                       [{"text": "Отмена", "callback_data": "menu"}]]})

    def send_place_correction(self, uid):
        st = self.user(uid).get("state") or {}
        if st.get("flow") != "place_fix" or st.get("step") != "confirm":
            return
        place = self.db["places"].get(st["place_id"])
        if not place or place.get("status") != "approved":
            self.user(uid)["state"] = None
            self.dirty = True
            return self.send(uid, "Место больше не доступно. Заявка не отправлена.")
        pending = self.db.setdefault("place_corrections", {})
        if sum(r.get("status") == "pending" and r.get("author") == uid for r in pending.values()) >= 10:
            return self.send(uid, "У вас уже 10 предложений на проверке. Дождитесь решения владельца.")
        # Telegram update deduplication and persisted flow clearing prevent repeat submission.
        rid = "f" + secrets.token_hex(6)
        req = {"id": rid, "place_id": st["place_id"], "field": st["field"], "value": st["value"],
               "previous": place.get(st["field"]), "author": uid, "status": "pending", "created": time.time()}
        pending[rid] = req
        self.user(uid)["state"] = None
        self.dirty = True
        self.send(uid, "Предложение отправлено на проверку. Карточка пока не изменена.")
        for admin in self.db["admins"]:
            self.show_place_correction(admin, req)

    def show_place_correction(self, uid, req):
        from html import escape
        p = self.db["places"].get(req["place_id"], {})
        def show(v):
            return "есть" if v is True else "нет" if v is False else "нет данных" if v in (None, "") else str(v)
        text = ("<b>Исправление места: " + escape(p.get("name", req["place_id"])) + "</b>\nID: "
                + escape(req["place_id"]) + "\n" + escape(FIELDS[req["field"]][0]) + "\nБыло: "
                + escape(show(req.get("previous"))) + "\nПредложено: " + escape(show(req["value"]))
                + "\nПодтвердите сведения перед одобрением. Дата одобрения не считается датой фактической проверки.")
        self.send(uid, text, {"inline_keyboard": [[{"text": "Одобрить", "callback_data": "pfmod:ok:" + req["id"]},
                                                     {"text": "Отклонить", "callback_data": "pfmod:no:" + req["id"]}]]})

    def corrections_list(self, uid):
        if not self.is_admin(uid):
            return
        reqs = [r for r in self.db.setdefault("place_corrections", {}).values() if r["status"] == "pending"]
        if not reqs:
            return self.send(uid, "Исправлений мест на проверке нет.")
        for req in sorted(reqs, key=lambda r: r["created"])[:20]:
            self.show_place_correction(uid, req)

    def moderate_place_correction(self, uid, action, rid):
        if not self.is_admin(uid) or action not in ("ok", "no"):
            return
        req = self.db.setdefault("place_corrections", {}).get(rid)
        if not req or req.get("status") != "pending":
            return self.send(uid, "Предложение уже обработано или не найдено.")
        p = self.db["places"].get(req["place_id"])
        if action == "ok" and (not p or p.get("status") != "approved" or p.get(req["field"]) != req.get("previous")):
            return self.send(uid, "Карточка уже изменилась или скрыта. Проверьте актуальные сведения; старую заявку можно отклонить.")
        if action == "ok":
            p[req["field"]] = req["value"]
            p["edited"] = True  # Preserve moderated values on the next OSM import.
            p["updated"] = time.time()  # Technical update, deliberately not confirmed_at.
            self.places_dirty = True
        req.update(status="approved" if action == "ok" else "rejected", decided_by=uid, decided_at=time.time())
        self.dirty = True
        self.send(uid, "Исправление опубликовано." if action == "ok" else "Исправление отклонено.")
        self.send(req["author"], "Ваше исправление места одобрено." if action == "ok" else "Ваше исправление места отклонено; публичная карточка не изменена.")
