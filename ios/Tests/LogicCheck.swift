// Проверка логики iPhone-версии на Linux (без Xcode): swiftc ... && ./logic-check
// Сверка расчёта времени намаза с библиотекой Adhan (те же случаи, что в Android-тесте), Кибла, серии, разбор файлов.
import Foundation

var failures = 0
func check(_ ok: Bool, _ message: @autoclosure () -> String) {
    if !ok {
        failures += 1
        print("ОШИБКА:", message())
    }
}

let cases: [(String, Double, Double, String, String, AsrMadhab, [String])] = [
    ("Moscow", 55.7558, 37.6173, "Europe/Moscow", "2026-10-24", .standard, ["05:14", "07:19", "12:14", "14:33", "17:08", "19:05"]),
    ("Moscow", 55.7558, 37.6173, "Europe/Moscow", "2026-10-24", .hanafi, ["05:14", "07:19", "12:14", "15:13", "17:08", "19:05"]),
    ("Moscow", 55.7558, 37.6173, "Europe/Moscow", "2026-06-21", .standard, ["01:49", "03:45", "12:31", "17:03", "21:18", "23:08"]),
    ("Moscow", 55.7558, 37.6173, "Europe/Moscow", "2026-06-21", .hanafi, ["01:49", "03:45", "12:31", "18:20", "21:18", "23:08"]),
    ("Moscow", 55.7558, 37.6173, "Europe/Moscow", "2026-12-21", .standard, ["06:36", "08:57", "12:28", "13:45", "15:58", "18:11"]),
    ("Moscow", 55.7558, 37.6173, "Europe/Moscow", "2026-12-21", .hanafi, ["06:36", "08:57", "12:28", "14:10", "15:58", "18:11"]),
    ("Moscow", 55.7558, 37.6173, "Europe/Moscow", "2026-03-20", .standard, ["04:25", "06:32", "12:37", "15:50", "18:43", "20:43"]),
    ("Moscow", 55.7558, 37.6173, "Europe/Moscow", "2026-03-20", .hanafi, ["04:25", "06:32", "12:37", "16:39", "18:43", "20:43"]),
    ("Grozny", 43.3178, 45.6949, "Europe/Moscow", "2026-10-24", .standard, ["04:46", "06:22", "11:41", "14:33", "17:00", "18:30"]),
    ("Grozny", 43.3178, 45.6949, "Europe/Moscow", "2026-10-24", .hanafi, ["04:46", "06:22", "11:41", "15:16", "17:00", "18:30"]),
    ("Grozny", 43.3178, 45.6949, "Europe/Moscow", "2026-06-21", .standard, ["01:56", "04:17", "11:59", "16:05", "19:41", "21:51"]),
    ("Grozny", 43.3178, 45.6949, "Europe/Moscow", "2026-06-21", .hanafi, ["01:56", "04:17", "11:59", "17:19", "19:41", "21:51"]),
    ("Grozny", 43.3178, 45.6949, "Europe/Moscow", "2026-12-21", .standard, ["05:43", "07:26", "11:55", "14:07", "16:24", "18:02"]),
    ("Grozny", 43.3178, 45.6949, "Europe/Moscow", "2026-12-21", .hanafi, ["05:43", "07:26", "11:55", "14:42", "16:24", "18:02"]),
    ("Grozny", 43.3178, 45.6949, "Europe/Moscow", "2026-03-20", .standard, ["04:25", "06:01", "12:05", "15:29", "18:09", "19:40"]),
    ("Grozny", 43.3178, 45.6949, "Europe/Moscow", "2026-03-20", .hanafi, ["04:25", "06:01", "12:05", "16:20", "18:09", "19:40"]),
    ("Makkah", 21.3891, 39.8579, "Asia/Riyadh", "2026-10-24", .standard, ["05:05", "06:20", "12:05", "15:22", "17:49", "19:00"]),
    ("Makkah", 21.3891, 39.8579, "Asia/Riyadh", "2026-10-24", .hanafi, ["05:05", "06:20", "12:05", "16:13", "17:49", "19:00"]),
    ("Makkah", 21.3891, 39.8579, "Asia/Riyadh", "2026-06-21", .standard, ["04:14", "05:39", "12:22", "15:42", "19:06", "20:26"]),
    ("Makkah", 21.3891, 39.8579, "Asia/Riyadh", "2026-06-21", .hanafi, ["04:14", "05:39", "12:22", "17:01", "19:06", "20:26"]),
    ("Makkah", 21.3891, 39.8579, "Asia/Riyadh", "2026-12-21", .standard, ["05:34", "06:54", "12:19", "15:23", "17:44", "18:58"]),
    ("Makkah", 21.3891, 39.8579, "Asia/Riyadh", "2026-12-21", .hanafi, ["05:34", "06:54", "12:19", "16:08", "17:44", "18:58"]),
    ("Makkah", 21.3891, 39.8579, "Asia/Riyadh", "2026-03-20", .standard, ["05:11", "06:25", "12:28", "15:53", "18:32", "19:41"]),
    ("Makkah", 21.3891, 39.8579, "Asia/Riyadh", "2026-03-20", .hanafi, ["05:11", "06:25", "12:28", "16:50", "18:32", "19:41"]),
    ("London", 51.5074, -0.1278, "Europe/London", "2026-10-24", .standard, ["05:48", "07:40", "12:45", "15:17", "17:49", "19:34"]),
    ("London", 51.5074, -0.1278, "Europe/London", "2026-10-24", .hanafi, ["05:48", "07:40", "12:45", "15:58", "17:49", "19:34"]),
    ("London", 51.5074, -0.1278, "Europe/London", "2026-06-21", .standard, ["02:31", "04:43", "13:02", "17:25", "21:22", "23:27"]),
    ("London", 51.5074, -0.1278, "Europe/London", "2026-06-21", .hanafi, ["02:31", "04:43", "13:02", "18:40", "21:22", "23:27"]),
    ("London", 51.5074, -0.1278, "Europe/London", "2026-12-21", .standard, ["05:59", "08:04", "11:59", "13:38", "15:53", "17:51"]),
    ("London", 51.5074, -0.1278, "Europe/London", "2026-12-21", .hanafi, ["05:59", "08:04", "11:59", "14:07", "15:53", "17:51"]),
    ("London", 51.5074, -0.1278, "Europe/London", "2026-03-20", .standard, ["04:10", "06:03", "12:08", "15:27", "18:14", "20:01"]),
    ("London", 51.5074, -0.1278, "Europe/London", "2026-03-20", .hanafi, ["04:10", "06:03", "12:08", "16:16", "18:14", "20:01"]),
]

for (city, lat, lon, zone, date, madhab, expected) in cases {
    let tz = TimeZone(identifier: zone)!
    let day = LocalDay(key: date)!
    let times = PrayerCalculator.calculate(day: day, latitude: lat, longitude: lon, method: .mwl, madhab: madhab)
    for (i, p) in Prayer.allCases.enumerated() {
        let parts = expected[i].split(separator: ":").map { Int($0)! }
        let want = day.at(hour: parts[0], minute: parts[1], in: tz)
        let diff = abs(times[p]!.timeIntervalSince(want)) / 60
        check(diff <= 2, "\(city) \(date) \(madhab) \(p): разница \(Int(diff)) мин")
    }
}

// Метод ДУМ РФ — как на aladhan.com для Москвы 24.10.2026 (05:29, 07:18, 12:14, 17:08, 18:51)
do {
    let tz = TimeZone(identifier: "Europe/Moscow")!
    let day = LocalDay(year: 2026, month: 10, day: 24)
    let t = PrayerCalculator.calculate(day: day, latitude: 55.7558, longitude: 37.6173, method: .russia, madhab: .standard)
    for (p, hm) in [(Prayer.fajr, (5, 29)), (.sunrise, (7, 18)), (.dhuhr, (12, 14)), (.maghrib, (17, 8)), (.isha, (18, 51))] {
        let diff = abs(t[p]!.timeIntervalSince(day.at(hour: hm.0, minute: hm.1, in: tz))) / 60
        check(diff <= 2, "RUSSIA \(p): разница \(Int(diff)) мин")
    }
}

// Московская Соборная мечеть: официальное время dumrf.ru 24.10.2026 — 05:15, 07:14, 12:19, 14:34, 17:12, 18:50
do {
    let tz = TimeZone(identifier: "Europe/Moscow")!
    let day = LocalDay(year: 2026, month: 10, day: 24)
    let t = PrayerCalculator.calculate(day: day, latitude: 55.7558, longitude: 37.6173, method: .moscowCathedral, madhab: .standard)
    let want: [(Prayer, Int, Int)] = [(.fajr, 5, 15), (.sunrise, 7, 14), (.dhuhr, 12, 19), (.asr, 14, 34), (.maghrib, 17, 12), (.isha, 18, 50)]
    for (p, h, m) in want {
        check(t[p]! == day.at(hour: h, minute: m, in: tz), "MOSCOW_CATHEDRAL \(p): не совпадает с dumrf.ru")
    }
    let html = """
    <div class="namaz"><h3>Время намазов на сегодня 13 Джумада-ль-Уля (Москва):</h3>
    <ul><li><span>Фаджр</span> <b>05:15</b></li><li><span>Шурук</span> <b>07:14</b></li>
    <li><span>Зухр</span> <b>12:19</b></li><li><span>Аср</span> <b>14:34</b></li>
    <li><span>Магриб</span>&nbsp;<b>17:12</b></li><li><span>Иша</span> <b>18:50</b></li></ul></div>
    """
    let parsed = DumrfParser.parse(html)
    check(parsed?[.fajr]?.hour == 5 && parsed?[.fajr]?.minute == 15, "dumrf: фаджр")
    check(parsed?[.isha]?.hour == 18 && parsed?[.isha]?.minute == 50, "dumrf: иша")
    check(DumrfParser.parse("<html><body>Новости</body></html>") == nil, "dumrf: пустая страница")
    check(DumrfParser.parse("Время намазов: Фаджр 18:00 Шурук 07:14 Зухр 12:19 Аср 14:34 Магриб 17:12 Иша 18:50") == nil, "dumrf: порядок")
    check(DumrfParser.isMoscow(latitude: 55.75, longitude: 37.62) && !DumrfParser.isMoscow(latitude: 43.31, longitude: 45.69), "dumrf: Москва")
}

// Порядок времён в течение года для всех методов
for method in CalcMethod.allCases {
    var day = LocalDay(year: 2026, month: 1, day: 1)
    for _ in 0..<365 {
        let t = PrayerCalculator.calculate(day: day, latitude: 55.7558, longitude: 37.6173, method: method, madhab: .hanafi)
        let ordered = Prayer.allCases.map { t[$0]! }
        check(ordered == ordered.sorted(), "\(method) \(day.key): порядок нарушен")
        day = day.adding(days: 1)
    }
}

// Кибла
check(abs(Qibla.bearing(lat: 55.7558, lon: 37.6173) - 176.4) < 0.5, "Кибла Москва")
check(abs(Qibla.distanceKm(lat: 55.7558, lon: 37.6173) - 3822) < 30, "Расстояние Москва")
check(abs(Qibla.bearing(lat: 40.7128, lon: -74.0060) - 58.5) < 0.5, "Кибла Нью-Йорк")

// Дни и серии
let d = LocalDay(year: 2026, month: 12, day: 31)
check(d.adding(days: 1).key == "2027-01-01", "Переход года")
check(LocalDay(year: 2026, month: 10, day: 24).weekday == 6, "Суббота")
check(LocalDay(year: 2026, month: 2, day: 1).daysInMonth == 28, "Февраль")
let today = LocalDay(year: 2026, month: 10, day: 24)
let streak: Set<LocalDay> = [today, today.adding(days: -1), today.adding(days: -2), today.adding(days: -5)]
check(Streaks.current(streak, today: today) == 3, "Серия")
check(Streaks.best(streak) == 3, "Лучшая серия")

// Файлы с вопросами и городами (общие с Android)
let root = URL(fileURLWithPath: CommandLine.arguments.count > 1 ? CommandLine.arguments[1] : ".")
for lang in ["ru", "en", "uz", "tg", "kk", "ky", "az", "tr"] {
    let url = root.appendingPathComponent("app/src/main/assets/quiz/\(lang).json")
    if let data = try? Data(contentsOf: url), let bank = try? QuizBank.parse(data) {
        check(bank.questions.count >= 80, "\(lang): мало вопросов")
        check(bank.categories.count == 6, "\(lang): темы")
        let order = bank.roundOrder(category: nil, recent: [])
        check(order.count == bank.questions.count, "\(lang): порядок раунда")
        let correctKept = order.allSatisfy { item in
            item.options[item.answerIndex] == bank.questions.first(where: { $0.id == item.id })!.options[0]
        }
        check(correctKept, "\(lang): перемешивание")
    } else {
        check(false, "\(lang): не читается quiz")
    }
}
if let data = try? Data(contentsOf: root.appendingPathComponent("app/src/main/assets/cities.json")), let catalog = try? CityCatalog.parse(data) {
    check(catalog.cities.count > 50, "Мало городов")
    check(catalog.cities.contains { $0.id == "moscow" }, "Нет Москвы")
} else {
    check(false, "Не читается cities.json")
}
if let data = try? Data(contentsOf: root.appendingPathComponent("app/src/main/assets/shop/catalog.json")),
   let shop = try? ShopCatalog.parse(data) {
    check(!shop.sellers.isEmpty && !shop.products.isEmpty, "Магазин: пустой каталог")
    check(shop.products.allSatisfy { shop.seller($0.seller) != nil }, "Магазин: товар без продавца")
    check(shop.categories.allSatisfy { !$0.name.get("tg").isEmpty }, "Магазин: названия категорий")
} else {
    check(false, "Не читается shop/catalog.json")
}
check(ShopCatalog.price(2500, currency: "RUB") == "2 500 ₽" && ShopCatalog.price(990, currency: "RUB") == "990 ₽", "Цена")
check("بِسْمِ".hasArabic && !"Muhammad ﷺ".hasArabic, "Определение арабского текста")

print(failures == 0 ? "Логика iPhone-версии: всё верно" : "Ошибок: \(failures)")
exit(failures == 0 ? 0 : 1)
