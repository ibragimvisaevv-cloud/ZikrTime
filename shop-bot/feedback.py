"""Private feedback inbox. Photos stay Telegram file IDs in the private bot database."""
import html
import re
import secrets
import time

KINDS = {"bug": "Ошибка приложения", "text": "Неточность в тексте", "idea": "Предложение"}
STATUSES = {"new": "Новое", "working": "В работе", "question": "Нужно уточнение", "closed": "Закрыто", "rejected": "Отклонено"}
MAX_TEXT = 3000
MAX_PHOTOS = 5


def button(text, action):
    return {"text": text, "callback_data": "fb:" + action}


def keyboard(*rows):
    return {"inline_keyboard": list(rows)}


def attachment(message):
    photos = message.get("photo") or []
    if photos:
        photo = photos[-1]
        if photo.get("file_id"):
            return {"method": "sendPhoto", "file_id": photo["file_id"]}
    doc = message.get("document") or {}
    if doc.get("mime_type") in ("image/png", "image/jpeg", "image/webp") and doc.get("file_id"):
        if 0 < doc.get("file_size", 0) <= 10 * 1024 * 1024:
            return {"method": "sendDocument", "file_id": doc["file_id"]}
    return None


class FeedbackMixin:
    def feedback_owner(self, uid):
        return bool(self.db.get("owner")) and uid == self.db["owner"]

    def feedback_home(self, uid, context=""):
        self.user(uid)["state"] = None
        self.user(uid)["feedback_context"] = context[:48] if re.fullmatch(r"[A-Za-z0-9_-]*", context) else ""
        self.dirty = True
        rows = [[button(label, "new:" + kind)] for kind, label in KINDS.items()]
        rows.append([button("Мои обращения", "list:mine:0")])
        if self.feedback_owner(uid):
            rows.append([button("Входящие обращения", "list:inbox:0")])
        self.send(uid, "<b>Предложения и ошибки</b>\n\nОпишите проблему или идею, при необходимости приложите до 5 фото. "
                       "Обращение и вложения увидит владелец ZikrTime. Перед отправкой можно всё проверить.",
                  {"inline_keyboard": rows})

    def feedback_new(self, uid, kind):
        if kind not in KINDS:
            return
        self.user(uid)["state"] = {"flow": "feedback", "id": secrets.token_hex(6), "kind": kind,
                                  "text": "", "photos": [], "messages": [],
                                  "context": self.user(uid).get("feedback_context", "")}
        self.dirty = True
        self.send(uid, "<b>" + KINDS[kind] + "</b>\n\nНапишите, что произошло и как это повторить, или опишите идею. "
                       "Для неточности в тексте укажите раздел, суру и аят. Можно прислать скриншот с подписью.\n"
                       "Не прикладывайте пароли и платёжные данные. /cancel — отмена.")

    def feedback_input(self, uid, state, message):
        text = (message.get("text") or message.get("caption") or "").strip()
        photo = attachment(message)
        mid = message.get("message_id")
        if mid in state.get("messages", []):
            return
        if state["flow"] == "feedback_reply":
            return self.feedback_reply_input(uid, state, message, text, photo)
        if not text and not photo:
            return self.send(uid, "Пришлите текст, фото или изображение PNG/JPEG/WebP до 10 МБ.")
        combined = "\n".join(t for t in (state.get("text", ""), text) if t)
        if len(combined) > MAX_TEXT:
            return self.send(uid, "Описание должно быть не длиннее 3000 символов. /cancel — начать заново.")
        if photo and len(state["photos"]) >= MAX_PHOTOS:
            return self.send(uid, "Можно приложить до 5 фото. Нажмите «Проверить обращение».",
                             keyboard([button("Проверить обращение", "preview:" + state["id"])]))
        state["text"] = combined
        if photo:
            state["photos"].append(photo)
        state["messages"] = (state.get("messages", []) + [mid])[-30:]
        self.dirty = True
        self.send(uid, "Добавлено. Фото: " + str(len(state["photos"])) + "/5. Можно дописать текст или добавить фото.",
                  keyboard([button("Проверить обращение", "preview:" + state["id"])],
                           [button("Отменить", "home")]))

    def feedback_photos(self, chat, photos, ticket):
        for photo in photos:
            field = "document" if photo["method"] == "sendDocument" else "photo"
            self.tg.call(photo["method"], chat_id=chat, caption="Обращение " + str(ticket),
                         **{field: photo["file_id"]})

    def feedback_preview(self, uid, nonce):
        state = self.user(uid).get("state") or {}
        if state.get("flow") != "feedback" or state.get("id") != nonce:
            return self.send(uid, "Этот черновик уже отправлен или отменён.")
        if not state["text"]:
            return self.send(uid, "Добавьте текстовое описание перед отправкой.")
        self.feedback_photos(uid, state["photos"], "— предпросмотр")
        context = ("\nВерсия / экран: " + html.escape(state["context"])) if state.get("context") else ""
        self.send(uid, "<b>" + KINDS[state["kind"]] + "</b>\n" + html.escape(state["text"]) + context,
                  keyboard([button("Отправить владельцу", "send:" + nonce)],
                           [button("Отменить", "home")]))

    def feedback_submit(self, uid, nonce):
        state = self.user(uid).get("state") or {}
        if state.get("flow") != "feedback" or state.get("id") != nonce or not state.get("text"):
            return self.send(uid, "Этот черновик уже отправлен или отменён.")
        if not self.db.get("owner"):
            return self.send(uid, "Приём обращений пока не настроен. Черновик сохранён.")
        tickets = self.db.setdefault("feedback_tickets", {})
        now = int(time.time())
        recent = sum(t["author"] == uid and t["created"] > now - 86400 for t in tickets.values())
        if recent >= 5:
            return self.send(uid, "За сутки можно отправить до 5 обращений. Существующее можно дополнить в «Моих обращениях».")
        self.db["feedback_sequence"] = self.db.get("feedback_sequence", 0) + 1
        tid = str(self.db["feedback_sequence"])
        ticket = {"id": tid, "author": uid, "kind": state["kind"], "text": state["text"],
                  "photos": list(state["photos"]), "context": state.get("context", ""),
                  "created": now, "updated": now, "status": "new", "replies": [], "reply_ids": []}
        tickets[tid] = ticket
        self.user(uid)["state"] = None
        self.dirty = True
        self.feedback_photos(self.db["owner"], ticket["photos"], "#" + tid)
        self.feedback_card(self.db["owner"], tid)
        self.send(uid, "Обращение #" + tid + " отправлено владельцу. Статус: «Новое».",
                  keyboard([button("Открыть обращение", "view:" + tid)]))

    def feedback_access(self, uid, tid):
        ticket = self.db.get("feedback_tickets", {}).get(tid)
        if ticket and (ticket["author"] == uid or self.feedback_owner(uid)):
            return ticket
        return None

    def feedback_card(self, uid, tid):
        ticket = self.feedback_access(uid, tid)
        if not ticket:
            return self.send(uid, "Обращение недоступно.")
        rows = []
        if self.feedback_owner(uid):
            rows = [[button("В работу", "status:" + tid + ":working"), button("Нужно уточнение", "ask:" + tid)],
                    [button("Ответить", "reply:" + tid), button("Закрыть", "status:" + tid + ":closed")],
                    [button("Отклонить", "status:" + tid + ":rejected")]]
        elif ticket["status"] not in ("closed", "rejected"):
            rows = [[button("Дополнить", "reply:" + tid)]]
        rows.append([button("Показать фото и переписку", "history:" + tid)])
        rows.append([button("К обращениям", "list:" + ("inbox" if self.feedback_owner(uid) else "mine") + ":0")])
        context = ("\nВерсия / экран: " + html.escape(ticket["context"])) if ticket.get("context") else ""
        self.send(uid, "<b>#" + tid + " · " + KINDS[ticket["kind"]] + "</b>\nСтатус: " +
                  STATUSES[ticket["status"]] + context + "\n\n" + html.escape(ticket["text"]),
                  {"inline_keyboard": rows})

    def feedback_list(self, uid, which, page):
        owner = self.feedback_owner(uid)
        if which not in ("mine", "inbox") or (which == "inbox" and not owner):
            return self.send(uid, "Раздел недоступен.")
        tickets = [t for t in self.db.get("feedback_tickets", {}).values()
                   if (which == "inbox" and t["status"] not in ("closed", "rejected")) or
                      (which == "mine" and t["author"] == uid)]
        tickets.sort(key=lambda t: (t["updated"], int(t["id"])), reverse=True)
        page = max(0, min(page, max(0, (len(tickets) - 1) // 10)))
        rows = [[button("#" + t["id"] + " · " + STATUSES[t["status"]] + " · " + KINDS[t["kind"]], "view:" + t["id"])]
                for t in tickets[page * 10:(page + 1) * 10]]
        nav = []
        if page:
            nav.append(button("Назад", "list:" + which + ":" + str(page - 1)))
        if (page + 1) * 10 < len(tickets):
            nav.append(button("Далее", "list:" + which + ":" + str(page + 1)))
        if nav:
            rows.append(nav)
        rows.append([button("Новое обращение", "home")])
        self.send(uid, ("Входящие" if which == "inbox" else "Мои обращения") + ": " + str(len(tickets)),
                  {"inline_keyboard": rows})

    def feedback_status(self, uid, tid, status):
        ticket = self.feedback_access(uid, tid)
        if not self.feedback_owner(uid) or not ticket or status not in ("working", "closed", "rejected"):
            return self.send(uid, "Действие недоступно.")
        if ticket["status"] != status:
            ticket["status"], ticket["updated"] = status, int(time.time())
            self.dirty = True
            self.send(ticket["author"], "Обращение #" + tid + ": " + STATUSES[status] + ".",
                      keyboard([button("Открыть", "view:" + tid)]))
        self.feedback_card(uid, tid)

    def feedback_reply_input(self, uid, state, message, text, photo):
        ticket = self.feedback_access(uid, state["ticket"])
        if not ticket or (state.get("ask") and not self.feedback_owner(uid)):
            return self.send(uid, "Обращение недоступно.")
        if ticket["status"] in ("closed", "rejected") and not self.feedback_owner(uid):
            return self.send(uid, "Обращение закрыто. Создайте новое, если проблема повторилась.")
        if not text and not photo:
            return self.send(uid, "Пришлите текст или фото с пояснением.")
        if len(text) > 2000 or len(ticket["replies"]) >= 50:
            return self.send(uid, "Ответ слишком большой или переписка заполнена. Максимум 2000 символов и 50 ответов.")
        key = str(uid) + ":" + str(message.get("message_id"))
        if key in ticket["reply_ids"]:
            return
        reply = {"by_owner": self.feedback_owner(uid), "text": text, "photos": [photo] if photo else [],
                 "created": int(time.time())}
        ticket["replies"].append(reply)
        ticket["reply_ids"].append(key)
        ticket["updated"] = reply["created"]
        ticket["status"] = "question" if state.get("ask") else ("working" if reply["by_owner"] else "new")
        self.user(uid)["state"] = None
        self.dirty = True
        recipient = ticket["author"] if reply["by_owner"] else self.db.get("owner")
        if recipient:
            self.feedback_photos(recipient, reply["photos"], "#" + ticket["id"])
            self.send(recipient, "<b>Ответ по обращению #" + ticket["id"] + "</b>\n" + html.escape(text),
                      keyboard([button("Открыть", "view:" + ticket["id"])]))
        self.send(uid, "Ответ отправлен.")
        self.feedback_card(uid, ticket["id"])

    def feedback_callback(self, uid, data):
        parts = data.split(":")
        action = parts[1] if len(parts) > 1 else ""
        if action == "home":
            return self.feedback_home(uid)
        if action == "new" and len(parts) == 3:
            return self.feedback_new(uid, parts[2])
        if action == "preview" and len(parts) == 3:
            return self.feedback_preview(uid, parts[2])
        if action == "send" and len(parts) == 3:
            return self.feedback_submit(uid, parts[2])
        if action == "list" and len(parts) == 4 and parts[3].isdigit() and len(parts[3]) < 7:
            return self.feedback_list(uid, parts[2], int(parts[3]))
        if action == "status" and len(parts) == 4:
            return self.feedback_status(uid, parts[2], parts[3])
        if action in ("view", "history", "reply", "ask") and len(parts) == 3:
            ticket = self.feedback_access(uid, parts[2])
            if not ticket:
                return self.send(uid, "Обращение недоступно.")
            if action == "view":
                return self.feedback_card(uid, parts[2])
            if action == "history":
                self.feedback_photos(uid, ticket["photos"], "#" + ticket["id"])
                for reply in ticket["replies"]:
                    self.feedback_photos(uid, reply["photos"], "#" + ticket["id"])
                    self.send(uid, ("Владелец" if reply["by_owner"] else "Автор") + ": " + html.escape(reply["text"] or "Фото"))
                return self.feedback_card(uid, parts[2])
            if action == "ask" and not self.feedback_owner(uid):
                return self.send(uid, "Действие недоступно.")
            if ticket["status"] in ("closed", "rejected") and not self.feedback_owner(uid):
                return self.send(uid, "Обращение закрыто.")
            self.user(uid)["state"] = {"flow": "feedback_reply", "ticket": parts[2], "ask": action == "ask"}
            self.dirty = True
            return self.send(uid, "Напишите уточняющий вопрос." if action == "ask" else "Пришлите ответ текстом или фото. /cancel — отмена.")
