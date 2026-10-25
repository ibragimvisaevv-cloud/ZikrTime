// Кабинет продавца магазина «Зикр-тайм» — мини-приложение Telegram.
// Данные приходят от API (облачная функция Яндекса); пользователь определяется по подписи Telegram (initData).
(function () {
  "use strict";
  const tg = window.Telegram ? window.Telegram.WebApp : null;
  const API = window.ZT_API || "";
  const app = document.getElementById("app");
  let me = null;           // ответ «me»: продавец, его компании, модерация
  let tab = "mine";        // mine | mod | settings
  const stack = [];        // экраны для кнопки «Назад»

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

  async function api(action, data) {
    const res = await fetch(API, {
      method: "POST",
      headers: { "Content-Type": "text/plain" },
      body: JSON.stringify(Object.assign({ action, initData: tg ? tg.initData : "" }, data || {})),
    });
    let body = {};
    try { body = await res.json(); } catch (e) { /* пусто */ }
    if (!res.ok) throw new Error(body.error || "Нет связи с сервером");
    if (action === "me") me = body;
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
      input.onchange = () => {
        const file = input.files && input.files[0];
        if (!file) return resolve(null);
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
    render();
  }
  function current() { return stack[stack.length - 1] || null; }

  function render() {
    const scr = current();
    if (tg) {
      if (scr) { tg.BackButton.show(); } else { tg.BackButton.hide(); }
    }
    if (!me) return;
    if (!scr) return renderHome();
    if (scr.type === "seller") return renderSellerForm(scr);
    if (scr.type === "products") return renderSeller(scr);
    if (scr.type === "product") return renderProductForm(scr);
    if (scr.type === "promo") return renderPromo(scr);
  }

  function statusChip(s) {
    const map = { approved: ["ok", "✅ Опубликована"], pending: ["wait", "⏳ На проверке"], rejected: ["bad", "❌ Отклонена"], hidden: ["", "🙈 Скрыта"] };
    const [cls, txt] = map[s.status] || ["", s.status];
    let out = `<span class="chip ${cls}">${txt}</span>`;
    if (s.partner) out += `<span class="chip gold">⭐ Партнёр</span>`;
    if (s.promo_until) out += `<span class="chip gold">⬆️ Продвигается до ${date(s.promo_until)}</span>`;
    else if (s.promo_request && s.promo_request.status === "claimed") out += `<span class="chip wait">💰 Оплата проверяется</span>`;
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
    const modCount = mod ? mod.pending.length + mod.promo.length + mod.reviews.length : 0;
    let html = `<div class="hero"><h1>Кабинет продавца</h1><p>Ассаляму алейкум, ${esc(me.user.first_name)}! Здесь ваши компании и товары в магазине приложения «Зикр-тайм».</p></div>`;
    if (me.admin) {
      html += `<div class="tabs">
        <button data-tab="mine" class="${tab === "mine" ? "on" : ""}">Мои компании</button>
        <button data-tab="mod" class="${tab === "mod" ? "on" : ""}">Модерация${modCount ? `<span class="badge">${modCount}</span>` : ""}</button>
        ${me.owner ? `<button data-tab="settings" class="${tab === "settings" ? "on" : ""}">Настройки</button>` : ""}
      </div>`;
    }
    if (tab === "mod" && me.admin) html += moderationHtml();
    else if (tab === "settings" && me.owner) html += settingsHtml();
    else {
      html += me.sellers.length ? me.sellers.map(sellerCard).join("") :
        `<div class="card empty">У вас пока нет компаний.<br>Добавьте свою — после проверки её увидят пользователи приложения.</div>`;
      if (me.sellers.length < me.limits.sellers || me.admin) html += `<button class="btn primary" id="add">➕ Добавить компанию</button>`;
    }
    app.innerHTML = html;
    app.querySelectorAll("[data-tab]").forEach((b) => b.onclick = () => { tab = b.dataset.tab; tap(); render(); });
    app.querySelectorAll(".seller[data-sid]").forEach((el) => el.onclick = () => go({ type: "products", sid: el.dataset.sid }));
    const add = document.getElementById("add");
    if (add) add.onclick = () => go({ type: "seller", sid: null });
    if (tab === "mod") bindModeration();
    if (tab === "settings") bindSettings();
  }

  // ---------------------------------------------------------------- компания: просмотр
  function sellerById(sid) { return me.sellers.find((s) => s.id === sid); }

  function renderSeller(scr) {
    const s = sellerById(scr.sid);
    if (!s) { stack.length = 0; return render(); }
    let html = sellerCard(s).replace('class="card seller"', 'class="card seller" id="edit"');
    html += `<div class="row"><button class="btn ghost" id="editBtn">✏️ Изменить данные</button>
      ${s.status === "approved" ? `<button class="btn gold" id="promo">⬆️ Продвинуть</button>` : ""}</div>`;
    if (s.status === "pending") html += `<p class="sub" style="margin-top:10px">Компания на проверке. Товары можно добавлять уже сейчас — они появятся вместе с компанией.</p>`;
    html += `<h2>Товары (${s.products.length}/${me.limits.products})</h2><div class="card">`;
    html += s.products.length ? s.products.map((p) => `<div class="product" data-pid="${esc(p.id)}">
        <div class="thumb" style="${p.images[0] ? `background-image:url('${esc(p.images[0])}')` : ""}"></div>
        <div class="t"><div>${esc(p.title)}</div><div class="p">${p.price != null ? money(p.price) + (p.unit ? " / " + esc(p.unit) : "") : "цена по запросу"}</div></div></div>`).join("")
      : `<div class="empty">Товаров пока нет</div>`;
    html += `</div><button class="btn primary" id="addProduct">➕ Добавить товар</button>`;
    if (s.status === "approved" || s.status === "hidden") {
      html += `<button class="btn ghost" id="vis">${s.status === "approved" ? "🙈 Скрыть из приложения" : "👁 Снова показать в приложении"}</button>`;
    }
    html += `<button class="btn danger" id="del">🗑 Удалить компанию</button>`;
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
    if (vis) vis.onclick = () => run(vis, async () => { await api("seller_visibility", { sid: s.id }); haptic("success"); render(); });
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
    app.innerHTML = `<h1>${s ? "Данные компании" : "Новая компания"}</h1>
      <p class="sub">${s ? "Изменения в опубликованной компании видны в приложении через пару минут." : "После проверки компания появится в магазине приложения."}</p>
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
      <button class="btn primary" id="save">${s ? "💾 Сохранить" : "📨 Отправить на проверку"}</button>`;
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
      toast(s ? "Сохранено" : "Отправлено на проверку 🙏");
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
      <button class="btn primary" id="save">💾 Сохранить товар</button>
      ${p ? `<button class="btn danger" id="del">🗑 Удалить товар</button>` : ""}`;
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
    const req = scr.request || (s.promo_request && s.promo_request.status === "new" ? s.promo_request : null);
    const prices = Object.entries(me.prices).sort((a, b) => a[0] - b[0]);
    let html = `<div class="hero"><h1>⬆️ Продвижение</h1><p>«${esc(s.name)}» будет показываться в приложении выше остальных компаний — с пометкой «Реклама». Остальные компании идут в случайном порядке, он меняется каждый день.</p></div>`;
    if (s.promo_until) html += `<div class="card">Сейчас продвигается до <b>${date(s.promo_until)}</b>. Новый срок добавится к текущему.</div>`;
    if (s.promo_request && s.promo_request.status === "claimed") {
      html += `<div class="card">💰 Ваша оплата за ${s.promo_request.days} дн. проверяется. Мы пришлём сообщение в Telegram, как только включим продвижение.</div>`;
    } else if (req) {
      html += `<div class="card"><h2 style="margin-top:0">Оплата: ${money(req.price)} за ${req.days} дн.</h2>
        <p>1. Переведите <b>${money(req.price)}</b>${me.pay_url ? " по кнопке ниже" : " владельцу магазина"}.</p>
        <p>2. В комментарии к переводу укажите код: <span class="code" id="code">${esc(req.id)}</span></p>
        <p>3. Нажмите «Я оплатил» — мы проверим перевод и включим продвижение.</p>
        ${me.pay_url ? `<button class="btn gold" id="pay">💳 Перейти к оплате</button>` : ""}
        <button class="btn primary" id="claim">✅ Я оплатил</button></div>`;
    } else {
      html += `<h2>Выберите срок</h2><div class="tariffs">${prices.map(([d, p], i) => `<div class="tariff ${i === prices.length - 1 ? "best" : ""}" data-days="${d}">
        <div>${d} дней</div><b>${money(p)}</b><div class="muted">${Math.round(p / d)} ₽ в день</div></div>`).join("")}</div>`;
    }
    app.innerHTML = html;
    app.querySelectorAll(".tariff").forEach((t) => t.onclick = () => run(null, async () => {
      tap();
      const r = await api("promo_request", { sid: s.id, days: +t.dataset.days });
      scr.request = r.request;
      render();
    }));
    const pay = document.getElementById("pay");
    if (pay) pay.onclick = () => { if (tg) tg.openLink(me.pay_url); else window.open(me.pay_url); };
    const code = document.getElementById("code");
    if (code) code.onclick = () => { try { navigator.clipboard.writeText(code.textContent); toast("Код скопирован"); } catch (e) { /* нет */ } };
    const claim = document.getElementById("claim");
    if (claim) claim.onclick = (e) => run(e.target, async () => {
      await api("promo_claim", { rid: req.id });
      await api("me");
      scr.request = null;
      haptic("success");
      toast("Спасибо! Проверим и включим 🙏");
      render();
    });
  }

  // ---------------------------------------------------------------- модерация (владелец и модераторы)
  function moderationHtml() {
    const m = me.moderation;
    let html = `<h2>Новые компании (${m.pending.length})</h2>`;
    html += m.pending.length ? m.pending.map((s) => `<div class="card">${sellerCard(s).replace(/<div class="card seller"[^>]*>/, '<div class="seller">')}
      <p class="muted">${esc(s.description || "")}</p>
      <div class="row"><button class="btn primary" data-mod="ok" data-sid="${esc(s.id)}">✅ Одобрить</button>
      <button class="btn ghost" data-mod="no" data-sid="${esc(s.id)}">❌ Отклонить</button></div></div>`).join("")
      : `<div class="card empty">Новых компаний нет</div>`;
    html += `<h2>Оплаты продвижения (${m.promo.length})</h2>`;
    html += m.promo.length ? m.promo.map((r) => `<div class="card"><b>${esc(r.seller)}</b> — ${r.days} дн., <b>${money(r.price)}</b>
      <div class="muted">${r.username ? "@" + esc(r.username) + " · " : ""}код в комментарии: <span class="code">${esc(r.id)}</span></div>
      <div class="row" style="margin-top:10px"><button class="btn gold" data-promo="ok" data-rid="${esc(r.id)}">✅ Оплата получена</button>
      <button class="btn ghost" data-promo="no" data-rid="${esc(r.id)}">❌ Нет оплаты</button></div></div>`).join("")
      : `<div class="card empty">Заявок нет</div>`;
    html += `<h2>Отзывы (${m.reviews.length})</h2>`;
    html += m.reviews.length ? m.reviews.map((r) => `<div class="card review"><b>${esc(r.target_name)}</b>
      <div class="stars">${"★".repeat(r.r)}${"☆".repeat(5 - r.r)}</div>${r.t ? `<p>«${esc(r.t)}»</p>` : ""}<div class="muted">— ${esc(r.n || "без имени")}</div>
      <div class="row" style="margin-top:10px"><button class="btn primary" data-rev="ok" data-rid="${esc(r.id)}">✅ Опубликовать</button>
      <button class="btn ghost" data-rev="no" data-rid="${esc(r.id)}">❌ Отклонить</button></div></div>`).join("")
      : `<div class="card empty">Новых отзывов нет</div>`;
    html += `<h2>Все компании (${m.all.length})</h2><div class="card">`;
    html += m.all.map((s) => `<div class="product"><div class="t"><div>${esc(s.name)}</div>
      <div class="muted">${{ approved: "✅", pending: "⏳", rejected: "❌", hidden: "🙈" }[s.status] || ""} товаров: ${s.products}${s.promo_until ? " · ⬆️ до " + date(s.promo_until) : ""}</div></div>
      <button class="chip ${s.partner ? "gold" : ""}" data-mod="partner" data-sid="${esc(s.id)}">⭐ ${s.partner ? "Партнёр" : "Сделать партнёром"}</button>
      ${s.status === "approved" ? `<button class="chip bad" data-mod="hide" data-sid="${esc(s.id)}">Скрыть</button>` : s.status !== "pending" ? `<button class="chip ok" data-mod="ok" data-sid="${esc(s.id)}">Показать</button>` : ""}</div>`).join("");
    return html + `</div>`;
  }

  function bindModeration() {
    const act = (sel, action, key) => app.querySelectorAll(sel).forEach((b) => b.onclick = (e) => run(b, async () => {
      e.stopPropagation();
      const data = { do: b.dataset[key.split(":")[0]] };
      data[key.split(":")[1]] = b.dataset[key.split(":")[1]];
      const r = await api(action, data);
      if (r.moderation) me.moderation = r.moderation;
      haptic("success"); render();
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
      <label>Ссылка для оплаты (CloudTips, СБП)</label><input id="payurl" value="${esc(me.pay_url)}" placeholder="https://pay.cloudtips.ru/p/…">
      <p class="muted">Продавцы переводят по этой ссылке и указывают код заявки в комментарии. Вы подтверждаете оплату во вкладке «Модерация» или кнопкой в боте.</p>
      <button class="btn primary" id="saveSettings">💾 Сохранить</button></div>`;
  }

  function bindSettings() {
    document.getElementById("saveSettings").onclick = (e) => run(e.target, async () => {
      const r = await api("settings", { prices: { 7: +document.getElementById("p7").value, 30: +document.getElementById("p30").value }, pay_url: document.getElementById("payurl").value.trim() });
      me.prices = r.prices; me.pay_url = r.pay_url;
      haptic("success"); toast("Сохранено");
    });
  }

  // ---------------------------------------------------------------- запуск
  async function start() {
    if (tg) {
      tg.ready();
      tg.expand();
      try { tg.setHeaderColor("secondary_bg_color"); } catch (e) { /* старая версия */ }
      tg.BackButton.onClick(back);
    }
    if (!tg || !tg.initData) {
      app.innerHTML = `<div class="card empty">Откройте кабинет из Telegram-бота: кнопка «📱 Открыть кабинет».</div>`;
      return;
    }
    try {
      await api("me");
      render();
    } catch (e) {
      app.innerHTML = `<div class="card empty">${esc(e.message)}<br><br><button class="btn primary" onclick="location.reload()">Повторить</button></div>`;
    }
  }
  start();
})();
