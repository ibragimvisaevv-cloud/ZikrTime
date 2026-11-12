// Кабинет продавца магазина «Зикр-тайм» — мини-приложение Telegram.
// Данные приходят от API (облачная функция Яндекса); пользователь определяется по подписи Telegram (initData).
(function () {
  "use strict";
  const tg = window.Telegram ? window.Telegram.WebApp : null;
  const API = window.ZT_API || "";
  const app = document.getElementById("app");
  let me = null;           // ответ «me»: продавец, его компании, модерация
  let tab = "overview";
  const stack = [];        // экраны для кнопки «Назад»
  const nonce = () => window.crypto.randomUUID ? window.crypto.randomUUID() : Array.from(window.crypto.getRandomValues(new Uint8Array(16)), b => b.toString(16).padStart(2, "0")).join("");
  const statusText = { new: "Новое", working: "В работе", question: "Нужно уточнение", closed: "Решено", rejected: "Отклонено", pending: "На проверке", approved: "На карте", hidden: "Скрыто" };
  const kindText = { bug: "Ошибка приложения", idea: "Предложение", text: "Неточность в тексте" };
  let ownerView = "moderation";
  let ownerData = null;

  // ---------------------------------------------------------------- помощники
  const esc = (s) => String(s == null ? "" : s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
  const money = (v) => (v == null || v === "" ? "" : Number(v).toLocaleString("ru-RU") + " ₽");
  const date = (ts) => new Date(ts * 1000).toLocaleDateString("ru-RU");
  const haptic = (t) => { try { tg.HapticFeedback.notificationOccurred(t); } catch (e) { /* нет */ } };
  const tap = () => { try { tg.HapticFeedback.impactOccurred("light"); } catch (e) { /* нет */ } };

  function toast(text) {
    const el = document.getElementById("toast");
    el.textContent = text;
    el.classList.add("show");
    clearTimeout(toast.t);
    toast.t = setTimeout(() => el.classList.remove("show"), 2600);
  }

  async function api(action, data, updateMe = true) {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 30000);
    let res;
    try {
    res = await fetch(API, {
      method: "POST",
      headers: { "Content-Type": "text/plain" },
      body: JSON.stringify(Object.assign({ action, initData: tg ? tg.initData : "" }, data || {})),
      signal: controller.signal,
    });
    } catch (error) {
      throw new Error(error.name === "AbortError" ? "Сервер отвечает долго. Повторите попытку — форма сохранена." : "Проверьте соединение и повторите попытку.");
    } finally { clearTimeout(timer); }
    let body = {};
    try { body = await res.json(); } catch (e) { /* пусто */ }
    if (!res.ok) throw new Error(body.error || "Нет связи с сервером");
    if (action === "me" && updateMe) me = body;
    else if (body.me) me = body.me;
    return body;
  }

  async function run(btn, fn) {
    if (btn) btn.disabled = true;
    try { await fn(); } catch (e) { haptic("error"); toast(e.message || "Ошибка"); } finally { if (btn) btn.disabled = false; }
  }

  // Фото с телефона: уменьшаем до 1280 px и сжимаем в JPEG прямо в телефоне
  function pickPhoto() {
    return new Promise((resolve) => {
      const input = document.createElement("input");
      input.type = "file";
      input.accept = "image/*";
      input.oncancel = () => resolve(null);
      input.onchange = () => {
        const file = input.files && input.files[0];
        if (!file) return resolve(null);
        if (file.size > 25 * 1024 * 1024) { toast("Выберите фотографию меньше 25 МБ"); return resolve(null); }
        const img = new Image();
        img.onload = () => {
          const k = Math.min(1, 1280 / Math.max(img.width, img.height));
          const c = document.createElement("canvas");
          c.width = Math.round(img.width * k);
          c.height = Math.round(img.height * k);
          c.getContext("2d").drawImage(img, 0, 0, c.width, c.height);
          resolve(c.toDataURL("image/jpeg", 0.85));
          URL.revokeObjectURL(img.src);
        };
        img.onerror = () => resolve(null);
        img.src = URL.createObjectURL(file);
      };
      input.click();
    });
  }

  async function upload(sid) {
    const data = await pickPhoto();
    if (!data) return null;
    toast("Загружаем фото…");
    const r = await api("photo", { sid, data });
    return r.url;
  }

  // ---------------------------------------------------------------- навигация
  function go(screen) {
    stack.push(screen);
    render();
    window.scrollTo(0, 0);
  }
  function back() {
    stack.pop();
    if (!stack.length && tab === "manage" && ownerView === "tickets" && !ownerData) {
      loadOwner("tickets");
      return;
    }
    render();
  }
  function current() { return stack[stack.length - 1] || null; }

  function render() {
    const scr = current();
    if (tg) {
      if (scr) { tg.BackButton.show(); } else { tg.BackButton.hide(); }
    }
    if (!me) return;
    if (!scr) renderHome();
    else if (scr.type === "seller") renderSellerForm(scr);
    else if (scr.type === "products") renderSeller(scr);
    else if (scr.type === "product") renderProductForm(scr);
    else if (scr.type === "promo") renderPromo(scr);
    else if (scr.type === "request") renderRequest(scr);
    else if (scr.type === "ticket") renderTicket(scr);
    else if (scr.type === "place") renderPlace(scr);
    if (scr) {
      const previous = document.createElement("button");
      previous.className = "back";
      previous.textContent = "← Назад";
      previous.onclick = back;
      app.prepend(previous);
    }
    app.querySelectorAll("label").forEach(label => {
      const field = label.nextElementSibling;
      if (field && field.id && /^(INPUT|SELECT|TEXTAREA)$/.test(field.tagName)) label.htmlFor = field.id;
    });
    app.querySelectorAll(".seller[data-sid], .product[data-pid], .pic[id]").forEach(card => {
      card.tabIndex = 0;
      card.setAttribute("role", "button");
      card.onkeydown = event => { if (event.key === "Enter" || event.key === " ") { event.preventDefault(); card.click(); } };
    });
  }

  function companyStatus(status) {
    const map = { awaiting_payment: ["wait", me.listing_fee_enabled === true ? "Одобрена · ожидает оплаты" : "Одобрена"], approved: ["ok", "Опубликована"], pending: ["wait", "На проверке"], rejected: ["bad", "Отклонена"], hidden: ["", "Скрыта"] };
    return map[status] || ["", "Статус уточняется"];
  }

  function statusChip(s) {
    const [cls, txt] = companyStatus(s.status);
    let out = `<span class="chip ${cls}">${txt}</span>`;
    if (s.partner) out += `<span class="chip gold">Партнёр</span>`;
    if (s.promo_until) out += `<span class="chip gold">Продвигается до ${date(s.promo_until)}</span>`;
    else if (s.promo_request && s.promo_request.status === "claimed") out += `<span class="chip wait">Оплата проверяется</span>`;
    return out;
  }

  function sellerCard(s) {
    const cover = s.cover || s.logo;
    return `<div class="card seller" data-sid="${esc(s.id)}">
      <div class="cover" style="${cover ? `background-image:url('${esc(cover)}')` : ""}">${cover ? "" : "🏪"}</div>
      <div class="info"><div class="name">${esc(s.name)}</div>
        <div class="muted">${esc(s.city)} · товаров: ${s.products.length}</div>
        <div class="chips">${statusChip(s)}</div></div></div>`;
  }

  // ---------------------------------------------------------------- главный экран
  function renderHome() {
    const mod = me.moderation;
    const modCount = mod ? mod.pending.length + (me.inbox_counts ? me.inbox_counts.places + me.inbox_counts.tickets : 0) : 0;
    const tabs = [["overview", "Обзор"], ["mine", "Компании"], ["requests", "Обращения"]];
    if (me.admin) tabs.push(["manage", "Админ"]);
    let html = `<header class="hero"><div class="eyebrow">Зикр-тайм</div><h1>${{overview: "Личный кабинет", mine: "Мои компании", requests: "Предложения и помощь", manage: "Управление"}[tab]}</h1><p>${tab === "overview" ? `Ассаляму алейкум, ${esc(me.user.first_name || "")}!` : tab === "mine" ? "Карточки, товары и продвижение" : tab === "requests" ? "Помогите сделать приложение лучше" : "Заявки и работа приложения"}</p></header>`;
    html += `<nav class="tabs${me.admin ? " admin-tabs" : ""}" aria-label="Разделы кабинета">${tabs.map(([id, title]) => `<button data-tab="${id}" aria-current="${tab === id ? "page" : "false"}" class="${tab === id ? "on" : ""}">${title}${id === "manage" && modCount ? `<span class="badge">${modCount}</span>` : ""}</button>`).join("")}</nav>`;
    if (tab === "manage" && me.admin) {
      html += `<div class="section-tabs"><button data-owner-view="moderation" class="${ownerView === "moderation" ? "on" : ""}">Компании</button>${me.owner ? [["tickets", "Обращения"], ["places", "Места"], ["users", "Пользователи"], ["settings", "Настройки"]].map(([id, name]) => `<button data-owner-view="${id}" class="${ownerView === id ? "on" : ""}">${name}</button>`).join("") : ""}</div>`;
      html += ownerView === "settings" ? settingsHtml() : ownerView === "moderation" ? moderationHtml() : ownerPanel();
    } else if (tab === "requests") html += requestsHtml();
    else if (tab === "overview") {
      html += `<div class="action-grid"><button class="action-card" data-go="mine"><span class="action-icon">▤</span><strong>Моя компания</strong><span>Товары, фотографии и статус размещения</span><b aria-hidden="true">↗</b></button><button class="action-card" data-go="place"><span class="action-icon sand">⌖</span><strong>Место для намаза</strong><span>Предложите мечеть или молельную комнату</span><b aria-hidden="true">↗</b></button></div><h2>Связаться с нами</h2><div class="card action-list"><button data-new="bug"><span><strong>Сообщить об ошибке</strong><small>Описание и скриншот</small></span><b>→</b></button><button data-new="idea"><span><strong>Предложить улучшение</strong><small>Что сделать удобнее</small></span><b>→</b></button></div>`;
      if (me.tickets.total) html += `<h2>Последние обращения</h2>${ticketRows(me.tickets.tickets.slice(0, 3))}`;
      html += `<details class="card account"><summary>Мой аккаунт</summary><p class="muted">Telegram ID ${esc(me.user.id)}</p><button class="btn ghost" id="accountCode">Получить код входа на сайт проекта</button><p id="codeResult" role="status"></p><a href="https://storage.yandexcloud.net/zikrtime-shop/privacy.html" target="_blank" rel="noopener">Конфиденциальность</a></details>`;
    } else {
      html += me.sellers.length ? me.sellers.map(sellerCard).join("") :
        `<div class="card empty"><h2>Расскажите о своём деле</h2><p>Создайте карточку компании, добавьте товары и отправьте на проверку.</p></div>`;
      if (me.sellers.length < me.limits.sellers || me.admin) html += `<button class="btn primary" id="add">Добавить компанию</button>`;
    }
    app.innerHTML = html;
    app.querySelectorAll("[data-tab]").forEach((b) => b.onclick = () => {
      tab = b.dataset.tab; tap();
      if (tab === "manage" && ownerView === "moderation") loadOwner("moderation");
      else render();
    });
    app.querySelectorAll(".seller[data-sid]").forEach((el) => el.onclick = () => go({ type: "products", sid: el.dataset.sid }));
    const add = document.getElementById("add");
    if (add) add.onclick = () => go({ type: "seller", sid: null });
    bindCommunity();
    if (tab === "manage" && ownerView === "moderation") bindModeration();
    if (tab === "manage" && ownerView === "settings") bindSettings();
    const code = document.getElementById("accountCode");
    if (code) code.onclick = () => run(code, async () => { const result = await api("account_code"); document.getElementById("codeResult").textContent = `${result.code} · действует 5 минут`; });
  }

  function ticketRows(rows) {
    return rows.map(t => `<button class="card ticket-row" data-ticket="${esc(t.id)}"><span class="row spread"><strong>${esc(kindText[t.kind] || t.kind)}</strong><span class="chip ${t.status === "closed" ? "ok" : ""}">${esc(statusText[t.status] || t.status)}</span></span><span class="preview">${esc(t.text)}</span><small>#${esc(t.id)} · ${date(t.updated)}</small></button>`).join("");
  }

  function placeRows(rows, admin = false) {
    return rows.map(p => `<article class="card"><div class="row spread"><strong>${esc(p.name)}</strong><span class="chip">${esc(statusText[p.status] || p.status)}</span></div><p class="muted">${esc(p.address || "Адрес не указан")}</p><p>${esc(p.note || "")}</p><a href="https://yandex.ru/maps/?pt=${encodeURIComponent(p.lon)},${encodeURIComponent(p.lat)}&z=17&l=map" target="_blank" rel="noopener">Посмотреть точку на карте ↗</a>${p.source_url ? `<p><a href="${esc(p.source_url)}" target="_blank" rel="noopener">Источник ↗</a></p>` : ""}<div class="private-photos">${(p.photos || []).map(id => `<button class="btn ghost" data-photo="${esc(id)}">Посмотреть фото</button>`).join("")}</div>${admin ? `<div class="row"><button class="btn primary" data-place="ok" data-pid="${esc(p.id)}">Проверил, добавить</button><button class="btn ghost" data-place="no" data-pid="${esc(p.id)}">Отклонить</button></div>` : ""}</article>`).join("");
  }

  function requestsHtml() {
    let html = `<div class="card action-list"><button data-new="bug"><span><strong>Сообщить об ошибке</strong><small>Приложите скриншот и опишите проблему</small></span><b>→</b></button><button data-new="idea"><span><strong>Предложить улучшение</strong><small>Идея для приложения</small></span><b>→</b></button><button data-new="text"><span><strong>Неточность в тексте</strong><small>Укажите раздел, суру или вопрос</small></span><b>→</b></button><button data-go="place"><span><strong>Предложить место для намаза</strong><small>Мечеть или молельная комната</small></span><b>→</b></button></div>`;
    html += `<h2>Мои обращения · ${me.tickets.total}</h2>`;
    html += me.tickets.total ? ticketRows(me.tickets.tickets) : `<div class="card empty">Здесь появятся ваши обращения, ответы и статус проверки.</div>`;
    if (me.tickets.offset + 30 < me.tickets.total) html += `<button class="btn ghost" id="moreTickets">Показать ещё</button>`;
    html += `<h2>Предложенные места · ${me.places.total}</h2>${placeRows(me.places.places)}`;
    if (me.places.offset + 30 < me.places.total) html += `<button class="btn ghost" id="morePlaces">Показать ещё</button>`;
    return html;
  }

  async function loadOwner(view, offset = 0) {
    ownerView = view;
    ownerData = null;
    render();
    if (view === "settings") return;
    if (view === "moderation") {
      const previous = me;
      try {
        const latest = await api("me", {}, false);
        // A moderation action may already have returned a more recent snapshot.
        if (tab === "manage" && ownerView === view && me === previous) { me = latest; render(); }
      } catch (error) {
        if (tab === "manage" && ownerView === view) toast(error.message);
      }
      return;
    }
    const action = view === "places" ? "my_places" : view;
    try {
      const result = await api(action, { inbox: true, offset });
      if (ownerView === view) { ownerData = result; render(); }
    } catch (error) {
      if (ownerView === view) { ownerData = { error: error.message }; render(); }
    }
  }

  function ownerPanel() {
    if (!ownerData) return `<div class="card empty" role="status">Загружаем…</div>`;
    if (ownerData.error) return `<div class="card empty">${esc(ownerData.error)}<button class="btn ghost" data-owner-view="${ownerView}">Повторить</button></div>`;
    let html = "";
    let total = ownerData.total || 0;
    if (ownerView === "tickets") html = ticketRows(ownerData.tickets) || `<div class="card empty">Обращений пока нет</div>`;
    if (ownerView === "places") html = `<p class="sub">Проверьте название, точку и действующее назначение места перед публикацией.</p>` + (placeRows(ownerData.places, true) || `<div class="card empty">Все места проверены</div>`);
    if (ownerView === "users") {
      const stats = ownerData.stats;
      total = stats.total;
      html = `<div class="stat-grid">${[[stats.total, "Всего в боте"], [stats.active7, "Активны за 7 дней"], [stats.active30, "Активны за 30 дней"], [stats.miniapp, "Открывали кабинет"]].map(([value, label]) => `<div class="card"><b>${value}</b><span>${label}</span></div>`).join("")}</div><p class="sub">Активность учитывается с обновления кабинета. Старые пользователи включены в общее количество.</p><div class="card"><h2>Пользователи</h2>${ownerData.users.map(u => `<div class="product"><div class="t"><strong>ID ${esc(u.id)}</strong><div class="muted">${u.last_seen ? "Был в боте «" + date(u.last_seen) + "»" : "Дата посещения ещё не определена"}</div></div></div>`).join("")}</div>`;
    }
    const offset = ownerData.offset || 0;
    if (total > 30) html += `<div class="row"><button class="btn ghost" data-page="${Math.max(0, offset - 30)}" ${offset === 0 ? "disabled" : ""}>Назад</button><button class="btn ghost" data-page="${offset + 30}" ${offset + 30 >= total ? "disabled" : ""}>Далее</button></div>`;
    return html;
  }

  function bindCommunity() {
    app.querySelectorAll("[data-new]").forEach(b => b.onclick = () => go({ type: "request", kind: b.dataset.new }));
    app.querySelectorAll("[data-go]").forEach(b => b.onclick = () => {
      if (b.dataset.go === "place") go({ type: "place" });
      else { tab = b.dataset.go; render(); }
    });
    app.querySelectorAll("[data-ticket]").forEach(b => b.onclick = () => run(b, async () => {
      const response = await api("ticket", { tid: b.dataset.ticket });
      go({ type: "ticket", ticket: response.ticket });
    }));
    app.querySelectorAll("[data-owner-view]").forEach(b => b.onclick = () => loadOwner(b.dataset.ownerView));
    app.querySelectorAll("[data-page]").forEach(b => b.onclick = () => loadOwner(ownerView, Number(b.dataset.page)));
    app.querySelectorAll("[data-photo]").forEach(b => b.onclick = () => run(b, async () => {
      const response = await api("private_photo", { photo_id: b.dataset.photo });
      const img = document.createElement("img");
      img.alt = "Вложение к обращению";
      img.className = "attachment";
      img.src = response.data;
      b.replaceWith(img);
    }));
    app.querySelectorAll("[data-place]").forEach(b => b.onclick = () => run(b, async () => {
      await api("mod_place", { pid: b.dataset.pid, do: b.dataset.place });
      await loadOwner("places", ownerData.offset);
    }));
    for (const [id, action, key] of [["moreTickets", "tickets", "tickets"], ["morePlaces", "my_places", "places"]]) {
      const b = document.getElementById(id);
      if (b) b.onclick = () => run(b, async () => {
        const next = await api(action, { offset: me[key].offset + 30 });
        next[key] = me[key][key].concat(next[key]);
        me[key] = next;
        render();
      });
    }
  }

  function photoControls(form) {
    return `<div class="pics">${form.photos.map((p, i) => `<div><img class="draft-photo" src="${p.preview}" alt="Вложение ${i + 1}"><button class="btn ghost" data-remove-photo="${i}">Убрать</button></div>`).join("")}</div><button class="btn ghost" id="attach" ${form.photos.length >= 3 ? "disabled" : ""}>Прикрепить фото · ${form.photos.length}/3</button><p class="sub">Фотографии увидит владелец. Для места они используются только при проверке.</p>`;
  }

  function bindPhotos(form, read) {
    const attach = document.getElementById("attach");
    attach.onclick = () => run(attach, async () => {
      read();
      const data = await pickPhoto();
      if (!data) return;
      const response = await api("private_photo_upload", { data, request_id: nonce() });
      form.photos.push({ id: response.photo_id, preview: data });
      render();
    });
    app.querySelectorAll("[data-remove-photo]").forEach(b => b.onclick = () => { read(); form.photos.splice(Number(b.dataset.removePhoto), 1); render(); });
  }

  function renderRequest(scr) {
    const f = scr.form || (scr.form = { text: "", context: "", photos: [], request_id: nonce() });
    app.innerHTML = `<h1>${kindText[scr.kind]}</h1><p class="sub">Ответ и статус появятся в разделе «Обращения».</p><div class="card"><label for="reportText">${scr.kind === "idea" ? "Что предлагаете улучшить" : "Что произошло и как это повторить"}</label><textarea id="reportText" maxlength="3000" rows="7" placeholder="Опишите подробнее…">${esc(f.text)}</textarea><label for="reportContext">Версия приложения и раздел</label><input id="reportContext" maxlength="120" value="${esc(f.context)}" placeholder="Например, 0.8.23 · Намаз">${photoControls(f)}</div><button class="btn primary" id="sendRequest">Отправить владельцу</button>`;
    const read = () => { f.text = document.getElementById("reportText").value; f.context = document.getElementById("reportContext").value; };
    app.querySelectorAll("input, textarea").forEach(e => e.oninput = read);
    bindPhotos(f, read);
    document.getElementById("sendRequest").onclick = e => run(e.target, async () => {
      read();
      if (f.text.trim().length < 10) return toast("Добавьте описание — хотя бы 10 символов");
      const result = await api("ticket_create", { ...f, photos: f.photos.map(p => p.id), kind: scr.kind });
      stack.pop();
      tab = "requests";
      go({ type: "ticket", ticket: result.ticket });
      toast("Обращение отправлено");
    });
  }

  function renderTicket(scr) {
    const t = scr.ticket;
    app.innerHTML = `<h1>${esc(kindText[t.kind] || t.kind)}</h1><p class="sub">#${esc(t.id)} · ${date(t.created)}</p><div class="card"><span class="chip">${esc(statusText[t.status])}</span><p class="message-text">${esc(t.text)}</p>${t.context ? `<p class="muted">${esc(t.context)}</p>` : ""}${(t.photos || []).map(id => `<button class="btn ghost" data-photo="${esc(id)}">Открыть вложение</button>`).join("")}${t.chat_photos ? `<p class="muted">${t.chat_photos} фото доступны в прежнем чате с ботом.</p>` : ""}</div>${t.replies.map(r => `<div class="card reply ${r.by_owner ? "owner" : ""}"><strong>${r.by_owner ? "Ответ владельца" : "Ваше сообщение"}</strong><p class="message-text">${esc(r.text)}</p><small>${date(r.created)}</small></div>`).join("")}`;
    if (me.owner || !["closed", "rejected"].includes(t.status)) {
      app.innerHTML += `<div class="card">${me.owner ? `<label for="ticketStatus">Статус</label><select id="ticketStatus">${["new", "working", "question", "closed", "rejected"].map(s => `<option value="${s}" ${s === t.status ? "selected" : ""}>${statusText[s]}</option>`).join("")}</select>` : ""}<label for="replyText">${me.owner ? "Ответ автору" : "Дополнить обращение"}</label><textarea id="replyText" maxlength="2000">${esc(scr.reply || "")}</textarea><button class="btn primary" id="reply">${me.owner ? "Сохранить и ответить" : "Отправить дополнение"}</button></div>`;
      document.getElementById("replyText").oninput = e => { scr.reply = e.target.value; };
      document.getElementById("reply").onclick = e => run(e.target, async () => {
        scr.replyNonce = scr.replyNonce || nonce();
        const request = { tid: t.id, text: scr.reply || "", request_id: scr.replyNonce };
        if (me.owner) request.status = document.getElementById("ticketStatus").value;
        const response = await api("ticket_update", request);
        scr.ticket = response.ticket;
        scr.reply = "";
        scr.replyNonce = null;
        if (ownerData && ownerView === "tickets") ownerData = null;
        render();
      });
    }
    bindCommunity();
  }

  function renderPlace(scr) {
    const f = scr.form || (scr.form = { name: "", kind: "mosque", address: "", lat: "", lon: "", hours: "", note: "", photos: [], request_id: nonce() });
    app.innerHTML = `<h1>Предложить место</h1><p class="sub">После проверки место появится на карте приложения.</p><div class="card"><label for="placeName">Название</label><input id="placeName" maxlength="100" value="${esc(f.name)}" placeholder="Название мечети"><label for="placeKind">Тип места</label><select id="placeKind">${[["mosque", "Мечеть"], ["room", "Молельная комната"], ["other", "Другое место для намаза"]].map(([id, title]) => `<option value="${id}" ${f.kind === id ? "selected" : ""}>${title}</option>`).join("")}</select><label for="placeAddress">Населённый пункт и адрес</label><input id="placeAddress" maxlength="200" value="${esc(f.address)}"><h2>Точка на карте</h2><p class="sub">Если вы сейчас у мечети, используйте своё местоположение. Иначе вставьте её координаты с карты.</p><button class="btn ghost" id="myLocation">Я сейчас у этого места</button><div class="row"><div><label for="placeLat">Широта</label><input id="placeLat" inputmode="decimal" value="${esc(f.lat)}" placeholder="43.00000"></div><div><label for="placeLon">Долгота</label><input id="placeLon" inputmode="decimal" value="${esc(f.lon)}" placeholder="45.00000"></div></div><label for="placeHours">Время доступа</label><input id="placeHours" maxlength="100" value="${esc(f.hours)}" placeholder="Если известно"><label for="placeNote">Что нужно знать посетителю</label><textarea id="placeNote" maxlength="1000" placeholder="Вход, омовение, женская зона…">${esc(f.note)}</textarea>${photoControls(f)}</div><button class="btn primary" id="sendPlace">Отправить на проверку</button>`;
    const mapping = { name: "placeName", kind: "placeKind", address: "placeAddress", lat: "placeLat", lon: "placeLon", hours: "placeHours", note: "placeNote" };
    const read = () => Object.entries(mapping).forEach(([key, id]) => f[key] = document.getElementById(id).value);
    app.querySelectorAll("input, textarea, select").forEach(e => e.oninput = read);
    bindPhotos(f, read);
    document.getElementById("myLocation").onclick = e => run(e.target, async () => {
      read();
      if (!navigator.geolocation) return toast("Введите координаты с карты");
      const position = await new Promise((resolve, reject) => navigator.geolocation.getCurrentPosition(resolve, () => reject(new Error("Геолокация недоступна. Введите координаты с карты.")), { enableHighAccuracy: true, timeout: 12000, maximumAge: 60000 }));
      f.lat = position.coords.latitude.toFixed(6); f.lon = position.coords.longitude.toFixed(6);
      render();
      toast("Проверьте, что это точка входа в мечеть");
    });
    document.getElementById("sendPlace").onclick = e => run(e.target, async () => {
      read();
      await api("place_create", { ...f, photos: f.photos.map(p => p.id) });
      stack.length = 0; tab = "requests"; render(); toast("Место отправлено на проверку");
    });
  }

  // ---------------------------------------------------------------- компания: просмотр
  function sellerById(sid) { return me.sellers.find((s) => s.id === sid); }

  function renderSeller(scr) {
    const s = sellerById(scr.sid);
    if (!s) { stack.length = 0; return render(); }
    let html = sellerCard(s).replace('class="card seller"', 'class="card seller" id="edit"');
    html += `<div class="row"><button class="btn ghost" id="editBtn">Изменить данные</button>
      ${s.status === "approved" ? `<button class="btn gold" id="promo">Продвинуть</button>` : ""}</div>`;
    if (s.orders && s.orders.length) html += `<div class="card"><h2>Операции</h2>${s.orders.map(orderLine).join("")}</div>`;
    if (s.status === "pending") html += `<p class="sub" style="margin-top:10px">Компания на проверке. Товары можно добавлять уже сейчас — они появятся вместе с компанией.</p>`;
    html += `<h2>Товары (${s.products.length}/${me.limits.products})</h2><div class="card">`;
    html += s.products.length ? s.products.map((p) => `<div class="product" data-pid="${esc(p.id)}">
        <div class="thumb" style="${p.images[0] ? `background-image:url('${esc(p.images[0])}')` : ""}"></div>
        <div class="t"><div>${esc(p.title)}</div><div class="p">${p.price != null ? money(p.price) + (p.unit ? " / " + esc(p.unit) : "") : "цена по запросу"}</div></div></div>`).join("")
      : `<div class="empty">Товаров пока нет</div>`;
    html += `</div><button class="btn primary" id="addProduct">Добавить товар</button>`;
    if (s.status === "approved" || s.status === "hidden") {
      html += `<button class="btn ghost" id="vis">${s.status === "approved" ? "Скрыть из приложения" : "Снова показать в приложении"}</button>`;
    }
    html += `<button class="btn danger" id="del">Удалить компанию</button>`;
    app.innerHTML = html;
    const open = () => go({ type: "seller", sid: s.id });
    document.getElementById("edit").onclick = open;
    document.getElementById("editBtn").onclick = open;
    const promo = document.getElementById("promo");
    if (promo) promo.onclick = () => go({ type: "promo", sid: s.id });
    app.querySelectorAll(".product[data-pid]").forEach((el) => el.onclick = () => go({ type: "product", sid: s.id, pid: el.dataset.pid }));
    document.getElementById("addProduct").onclick = () => {
      if (s.products.length >= me.limits.products) return toast(`Не больше ${me.limits.products} товаров`);
      go({ type: "product", sid: s.id, pid: null });
    };
    const vis = document.getElementById("vis");
    if (vis) vis.onclick = () => run(vis, async () => {
      const result = await api("seller_visibility", { sid: s.id, visible: s.status === "hidden" });
      haptic("success"); render(); toast(companyStatus(result.seller.status)[1]);
    });
    document.getElementById("del").onclick = (e) => confirmAsk(`Удалить «${s.name}» вместе с товарами?`, () => run(e.target, async () => {
      await api("seller_delete", { sid: s.id }); haptic("success"); toast("Компания удалена"); stack.length = 0; render();
    }));
  }

  function confirmAsk(text, ok) {
    if (tg && tg.showConfirm) tg.showConfirm(text, (yes) => yes && ok());
    else if (window.confirm(text)) ok();
  }

  // ---------------------------------------------------------------- компания: форма
  function renderSellerForm(scr) {
    const s = scr.sid ? sellerById(scr.sid) : null;
    const f = scr.form || (scr.form = s ? Object.assign({}, s) : { name: "", category: "other", city: "", description: "", address: "", phone: "", whatsapp: false, telegram: (me.user.username || ""), instagram: "", logo: "", cover: "" });
    const cats = me.categories.map((c) => `<option value="${esc(c.id)}" ${c.id === f.category ? "selected" : ""}>${esc(c.name)}</option>`).join("");
    const feeRequired = me.listing_fee_enabled === true;
    const publishDirectly = me.admin && !feeRequired;
    const createHint = feeRequired ? "Размещение карточки — 200 ₽ один раз после одобрения." : publishDirectly ?
      "Компания появится в каталоге сразу после публикации. Размещение бесплатно." : "После проверки компания появится в каталоге. Размещение бесплатно.";
    const hint = s ? (s.status === "rejected" ? "Исправьте данные и отправьте карточку на повторную проверку." : "Изменения в опубликованной компании видны в приложении через пару минут.") : createHint;
    const submit = s ? (s.status === "rejected" ? "Отправить повторно" : "Сохранить") :
      publishDirectly ? "Опубликовать компанию" : me.admin ? "Добавить компанию" : "Отправить на проверку";
    app.innerHTML = `<h1>${s ? "Данные компании" : "Новая компания"}</h1>
      <p class="sub">${hint}</p>
      <div class="card">
        <label>Обложка</label><div class="pic wide" id="cover" style="${f.cover ? `background-image:url('${esc(f.cover)}')` : ""}">${f.cover ? "" : "🌄"}</div>
        <label>Логотип</label><div class="pics"><div class="pic" id="logo" style="${f.logo ? `background-image:url('${esc(f.logo)}')` : ""}">${f.logo ? "" : "🏪"}</div></div>
        <label>Название *</label><input id="name" maxlength="60" value="${esc(f.name)}" placeholder="Например: Халяль Маркет">
        <label>Категория</label><select id="category">${cats}</select>
        <label>Город *</label><input id="city" maxlength="60" value="${esc(f.city)}" placeholder="Грозный">
        <label>Описание</label><textarea id="description" maxlength="600" placeholder="Чем вы занимаетесь, условия доставки…">${esc(f.description)}</textarea>
        <label>Адрес</label><input id="address" maxlength="150" value="${esc(f.address)}">
      </div>
      <div class="card">
        <label>Телефон</label><input id="phone" type="tel" value="${esc(f.phone)}" placeholder="+7 928 123-45-67">
        <div class="check"><input type="checkbox" id="whatsapp" ${f.whatsapp ? "checked" : ""}><span>Есть WhatsApp на этом номере</span></div>
        <label>Telegram</label><input id="telegram" value="${esc(f.telegram)}" placeholder="@shop">
        <label>Instagram</label><input id="instagram" value="${esc(f.instagram)}" placeholder="shop">
      </div>
      <button class="btn primary" id="save">${submit}</button>`;
    const read = () => ["name", "category", "city", "description", "address", "phone", "telegram", "instagram"].forEach((k) => f[k] = document.getElementById(k).value);
    ["cover", "logo"].forEach((k) => document.getElementById(k).onclick = () => run(null, async () => {
      read();
      const url = await upload(s ? s.id : null);
      if (url) { f[k] = url; toast("Фото загружено"); render(); }
    }));
    document.getElementById("save").onclick = (e) => run(e.target, async () => {
      read();
      f.whatsapp = document.getElementById("whatsapp").checked;
      const r = await api("seller_save", { sid: s ? s.id : null, fields: f });
      haptic("success");
      toast(s && s.status !== "rejected" ? "Сохранено" : companyStatus(r.seller.status)[1]);
      stack.pop();
      if (!s) stack.push({ type: "products", sid: r.seller.id });
      render();
    });
  }

  // ---------------------------------------------------------------- товар
  function renderProductForm(scr) {
    const s = sellerById(scr.sid);
    const p = scr.pid ? s.products.find((x) => x.id === scr.pid) : null;
    const f = scr.form || (scr.form = p ? JSON.parse(JSON.stringify(p)) : { title: "", price: "", old_price: "", unit: "", description: "", images: [] });
    app.innerHTML = `<h1>${p ? "Товар" : "Новый товар"}</h1><p class="sub">${esc(s.name)}</p>
      <div class="card">
        <label>Фото (до ${me.limits.photos})</label>
        <div class="pics">${f.images.map((u, i) => `<div class="pic" style="background-image:url('${esc(u)}')"><span class="x" data-i="${i}">✕</span></div>`).join("")}
          ${f.images.length < me.limits.photos ? `<div class="pic" id="addPic">＋</div>` : ""}</div>
        <label>Название *</label><input id="title" maxlength="80" value="${esc(f.title)}">
        <div class="row"><div style="flex:1"><label>Цена, ₽</label><input id="price" inputmode="numeric" value="${esc(f.price == null ? "" : f.price)}"></div>
          <div style="flex:1"><label>Старая цена</label><input id="old_price" inputmode="numeric" value="${esc(f.old_price == null ? "" : f.old_price)}"></div></div>
        <label>За что цена (шт, кг, сутки…)</label><input id="unit" maxlength="20" value="${esc(f.unit)}">
        <label>Описание</label><textarea id="description" maxlength="600">${esc(f.description)}</textarea>
      </div>
      <button class="btn primary" id="save">Сохранить товар</button>
      ${p ? `<button class="btn danger" id="del">Удалить товар</button>` : ""}`;
    const read = () => ["title", "price", "old_price", "unit", "description"].forEach((k) => f[k] = document.getElementById(k).value);
    app.querySelectorAll(".x[data-i]").forEach((x) => x.onclick = (e) => { e.stopPropagation(); read(); f.images.splice(+x.dataset.i, 1); render(); });
    const add = document.getElementById("addPic");
    if (add) add.onclick = () => run(null, async () => { read(); const url = await upload(s.id); if (url) { f.images.push(url); render(); } });
    document.getElementById("save").onclick = (e) => run(e.target, async () => {
      read();
      await api("product_save", { sid: s.id, pid: p ? p.id : null, fields: f });
      haptic("success"); toast("Товар сохранён"); back();
    });
    const del = document.getElementById("del");
    if (del) del.onclick = (e) => confirmAsk(`Удалить «${p.title}»?`, () => run(e.target, async () => {
      await api("product_delete", { sid: s.id, pid: p.id }); haptic("success"); back();
    }));
  }

  // ---------------------------------------------------------------- продвижение
  function renderPromo(scr) {
    const s = sellerById(scr.sid);
    const rates = Object.entries(me.prices).map(([days, price]) => `<p>${esc(days)} дн. · ${money(price)}</p>`).join("");
    const orders = (s.orders || []).map(orderLine).join("");
    app.innerHTML = `<div class="hero"><h1>Продвижение</h1><p>${esc(s.name)}</p></div><div class="card">${rates}</div>${orders ? '<div class="card">' + orders + '</div>' : ''}`;
  }

  function orderStatus(state) {
    return ({initializing: "Создаётся", waiting: "Ожидает оплаты", paid: "Оплачено", failed: "Не оплачено", review: "На проверке", refund_review: "Возврат на проверке", refunded: "Возвращено"})[state] || state;
  }

  function orderLine(order) {
    if (order.kind === "listing" && me.listing_fee_enabled !== true && ["waiting", "initializing"].includes(order.state)) {
      return "<p>Размещение · оплата не требуется</p>";
    }
    return `<p>${order.kind === "listing" ? "Размещение" : "Продвижение"} · ${money(order.amount / 100)} · ${esc(orderStatus(order.state))}</p>`;
  }

  // ---------------------------------------------------------------- модерация (владелец и модераторы)
  function moderationHtml() {
    const m = me.moderation;
    let html = `<h2>Новые компании (${m.pending.length})</h2>`;
    html += m.pending.length ? m.pending.map((s) => `<div class="card">${sellerCard(s).replace(/<div class="card seller"[^>]*>/, '<div class="seller">')}
      <p class="muted">${esc(s.description || "")}</p>
      <div class="row"><button class="btn primary" data-mod="ok" data-sid="${esc(s.id)}">Одобрить</button>
      <button class="btn ghost" data-mod="no" data-sid="${esc(s.id)}">Отклонить</button></div></div>`).join("")
      : `<div class="card empty">Новых компаний нет</div>`;
    html += `<h2>Оплаты продвижения (${m.promo.length})</h2>`;
    html += m.promo.length ? m.promo.map((r) => `<div class="card"><b>${esc(r.seller)}</b> — ${r.days} дн., <b>${money(r.price)}</b>
      <div class="muted">${r.username ? "@" + esc(r.username) + " · " : ""}код в комментарии: <span class="code">${esc(r.id)}</span></div>
      <div class="row" style="margin-top:10px"><button class="btn gold" data-promo="ok" data-rid="${esc(r.id)}">Оплата получена</button>
      <button class="btn ghost" data-promo="no" data-rid="${esc(r.id)}">Нет оплаты</button></div></div>`).join("")
      : `<div class="card empty">Заявок нет</div>`;
    html += `<h2>Все компании (${m.all.length})</h2><div class="card">`;
    html += m.all.map((s) => `<div class="product"><div class="t"><div>${esc(s.name)}</div>
      <div class="muted">${companyStatus(s.status)[1]} · товаров: ${s.products}${s.promo_until ? " · до " + date(s.promo_until) : ""}</div></div>
      <button class="chip ${s.partner ? "gold" : ""}" data-mod="partner" data-sid="${esc(s.id)}" aria-pressed="${!!s.partner}">${s.partner ? "Снять отметку партнёра" : "Сделать партнёром"}</button>
      ${s.status === "approved" ? `<button class="chip bad" data-mod="hide" data-sid="${esc(s.id)}">Скрыть</button>` : s.status === "hidden" ? `<button class="chip ok" data-mod="ok" data-sid="${esc(s.id)}">Показать в приложении</button>` : s.status === "rejected" ? `<button class="chip ok" data-mod="ok" data-sid="${esc(s.id)}">Одобрить</button>` : ""}</div>`).join("");
    return html + `</div>`;
  }

  function bindModeration() {
    const act = (sel, action, key) => app.querySelectorAll(sel).forEach((b) => b.onclick = (e) => run(b, async () => {
      e.stopPropagation();
      const data = { do: b.dataset[key.split(":")[0]] };
      data[key.split(":")[1]] = b.dataset[key.split(":")[1]];
      if (action === "mod_seller" && data.do === "partner") data.partner = b.getAttribute("aria-pressed") !== "true";
      const r = await api(action, data);
      if (r.moderation) me.moderation = r.moderation;
      haptic("success"); render();
      if (action === "mod_seller") {
        const company = me.moderation.all.find(s => s.id === data.sid);
        if (company) toast(data.do === "partner" ? (company.partner ? "Отметка партнёра добавлена" : "Отметка партнёра снята") : companyStatus(company.status)[1]);
      }
    }));
    act("[data-mod]", "mod_seller", "mod:sid");
    act("[data-promo]", "mod_promo", "promo:rid");
    act("[data-rev]", "mod_review", "rev:rid");
  }

  // ---------------------------------------------------------------- настройки владельца
  function settingsHtml() {
    return `<div class="card"><h2 style="margin-top:0">Тарифы продвижения</h2>
      <div class="row"><div style="flex:1"><label>7 дней, ₽</label><input id="p7" inputmode="numeric" value="${esc(me.prices["7"])}"></div>
      <div style="flex:1"><label>30 дней, ₽</label><input id="p30" inputmode="numeric" value="${esc(me.prices["30"])}"></div></div>
      <button class="btn primary" id="saveSettings">Сохранить</button></div>`;
  }

  function bindSettings() {
    document.getElementById("saveSettings").onclick = (e) => run(e.target, async () => {
      const r = await api("settings", { prices: { 7: +document.getElementById("p7").value, 30: +document.getElementById("p30").value } });
      me.prices = r.prices;
      haptic("success"); toast("Сохранено");
    });
  }

  // ---------------------------------------------------------------- запуск
  async function start() {
    const theme = () => { document.documentElement.dataset.theme = tg && tg.colorScheme === "dark" ? "dark" : "light"; };
    theme();
    if (tg) {
      tg.ready();
      tg.expand();
      try { tg.setHeaderColor("secondary_bg_color"); } catch (e) { /* старая версия */ }
      tg.BackButton.onClick(back);
      if (tg.onEvent) tg.onEvent("themeChanged", theme);
    }
    if (!tg || !tg.initData) {
      app.innerHTML = `<div class="card empty">Откройте кабинет из Telegram-бота: кнопка «📱 Открыть кабинет».</div>`;
      return;
    }
    try {
      await api("me");
      const section = new URLSearchParams(window.location.search).get("section");
      if (["overview", "mine", "requests"].includes(section)) tab = section;
      if (section === "place") { tab = "requests"; stack.push({ type: "place" }); }
      render();
    } catch (e) {
      app.innerHTML = `<div class="card empty">${esc(e.message)}<br><br><button class="btn primary" onclick="location.reload()">Повторить</button></div>`;
    }
  }
  start();
})();
