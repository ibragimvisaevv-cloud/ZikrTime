/* Synthetic Telegram/API fixtures. No production credentials or user data are used. */
const assert = require("node:assert/strict");
const fs = require("node:fs");
const http = require("node:http");
const path = require("node:path");
const { chromium } = require("playwright");

const repo = path.resolve(__dirname, "../..");
const output = path.resolve(process.env.MINIAPP_QA_OUT || path.join(repo, "ci-out/miniapp"));
fs.mkdirSync(output, { recursive: true });
const server = http.createServer((request, response) => {
  const name = new URL(request.url, "http://localhost").pathname.replace(/^\//, "") || "index.html";
  if (name === "config.js") {
    response.writeHead(200, { "Content-Type": "application/javascript" });
    response.end("window.ZT_API='/api';");
    return;
  }
  if (!["index.html", "app.js", "style.css"].includes(name)) { response.writeHead(404); response.end(); return; }
  response.writeHead(200, { "Content-Type": name.endsWith(".js") ? "application/javascript" : name.endsWith(".css") ? "text/css" : "text/html" });
  response.end(fs.readFileSync(path.join(repo, "shop-bot/miniapp", name)));
});

function fixture(owner) {
  return {
    user: { id: owner ? 1 : 2, first_name: owner ? "Владелец" : "Ибрагим", username: "fixture" },
    admin: owner, owner,
    sellers: [], categories: [{ id: "other", name: "Другое" }], prices: { 7: 100, 30: 300 },
    limits: { sellers: 3, products: 60, photos: 5 }, bot: "FixtureBot",
    tickets: { total: 0, tickets: [], offset: 0 }, places: { total: 0, places: [], offset: 0 },
    ...(owner ? { moderation: { pending: [], all: [], promo: [], reviews: [] }, inbox_counts: { tickets: 0, places: 0 } } : {}),
  };
}

async function scenario(browser, base, owner, theme) {
  const context = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 1, reducedMotion: "reduce" });
  const page = await context.newPage();
  const errors = [];
  page.on("pageerror", error => errors.push(error.message));
  const data = fixture(owner);
  const actions = [];
  await context.addInitScript(({ theme }) => {
    window.Telegram = { WebApp: { initData: "SYNTHETIC_TEST_ONLY", colorScheme: theme,
      ready() {}, expand() {}, setHeaderColor() {}, onEvent() {},
      BackButton: { show() {}, hide() {}, onClick() {} },
      HapticFeedback: { impactOccurred() {}, notificationOccurred() {} } } };
  }, { theme });
  await page.route("https://telegram.org/**", route => route.fulfill({ body: "", contentType: "application/javascript" }));
  await page.route("**/api", async route => {
    const body = route.request().postDataJSON();
    assert.equal(body.initData, "SYNTHETIC_TEST_ONLY");
    actions.push(body);
    let result;
    switch (body.action) {
      case "me": result = data; break;
      case "private_photo_upload": result = { photo_id: "fixture-photo-1" }; break;
      case "ticket_create": {
        const ticket = { id: "1", kind: body.kind, text: body.text, context: body.context, status: "new", created: 1794000000, updated: 1794000000, photos: body.photos, replies: [] };
        data.tickets = { total: 1, tickets: [ticket], offset: 0 };
        result = { ticket, me: data };
        break;
      }
      case "place_create": {
        const place = { id: "fixture-place", ...body, status: "pending", created: 1794000000 };
        data.places = { total: 1, places: [place], offset: 0 };
        result = { place, me: data };
        break;
      }
      case "users":
        assert.equal(owner, true);
        result = { stats: { total: 128, active7: 47, active30: 89, miniapp: 62, started: 100 }, users: [{ id: 100001, last_seen: 1794000000 }, { id: 100002, last_seen: null }], offset: 0 };
        break;
      default: throw new Error("Unhandled fixture action " + body.action);
    }
    await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(result) });
  });
  await page.goto(base);
  await page.getByRole("heading", { name: "Личный кабинет" }).waitFor();
  assert.equal(await page.locator('[data-tab="manage"]').count(), owner ? 1 : 0);
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
  await page.screenshot({ path: path.join(output, `miniapp-${owner ? "owner" : "user"}-${theme}.png`), fullPage: true });
  if (owner) {
    await page.getByRole("button", { name: "Управление", exact: true }).click();
    await page.getByRole("button", { name: "Пользователи", exact: true }).click();
    await page.getByText("128", { exact: true }).waitFor();
    await page.screenshot({ path: path.join(output, `miniapp-admin-stats-${theme}.png`), fullPage: true });
  } else if (theme === "dark") {
    await page.getByRole("button", { name: "Сообщить об ошибке Описание и скриншот" }).click();
    await page.getByLabel("Что произошло и как это повторить").fill("После выбора города не обновляется подпись источника.");
    await page.getByLabel("Версия приложения и раздел").fill("0.8.23 · Намаз");
    const chooser = page.waitForEvent("filechooser");
    await page.getByRole("button", { name: "Прикрепить фото" }).click();
    await (await chooser).setFiles({ name: "fixture.png", mimeType: "image/png", buffer: Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jT1sAAAAASUVORK5CYII=", "base64") });
    await page.getByRole("button", { name: "Прикрепить фото · 1/3" }).waitFor();
    await page.getByRole("button", { name: "Отправить владельцу" }).click();
    await page.getByText("Новое", { exact: true }).waitFor();
    assert.deepEqual(actions.find(a => a.action === "ticket_create").photos, ["fixture-photo-1"]);
    await page.screenshot({ path: path.join(output, "miniapp-request-dark.png"), fullPage: true });
    await page.getByRole("button", { name: "← Назад", exact: true }).click();
    await page.getByRole("button", { name: "Предложить место для намаза" }).click();
    await page.getByLabel("Название", { exact: true }).fill("Тестовая мечеть");
    await page.getByLabel("Населённый пункт и адрес").fill("Тестовый адрес");
    await page.getByLabel("Широта", { exact: true }).fill("43.1");
    await page.getByLabel("Долгота", { exact: true }).fill("45.3");
    await page.getByRole("button", { name: "Отправить на проверку", exact: true }).click();
    await page.getByText("Тестовая мечеть", { exact: true }).waitFor();
    assert.equal(actions.filter(a => a.action === "place_create").length, 1);
  }
  await page.setViewportSize({ width: 320, height: 700 });
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
  assert.deepEqual(errors, []);
  await context.close();
}

(async () => {
  await new Promise(resolve => server.listen(0, "127.0.0.1", resolve));
  const browser = await chromium.launch({ headless: true });
  try {
    const base = `http://127.0.0.1:${server.address().port}/`;
    await scenario(browser, base, false, "dark");
    await scenario(browser, base, false, "light");
    await scenario(browser, base, true, "dark");
    fs.writeFileSync(path.join(output, "miniapp-result.json"), JSON.stringify({ passed: true, fixtures: "synthetic", scenarios: ["private photo and issue", "place submission", "owner statistics", "light and dark", "320px layout"] }, null, 2));
  } finally {
    await browser.close();
    server.close();
  }
})().catch(error => { console.error(error); server.close(); process.exitCode = 1; });
