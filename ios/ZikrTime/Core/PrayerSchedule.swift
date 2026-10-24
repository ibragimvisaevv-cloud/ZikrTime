import Foundation

/// Время намаза из интернета (aladhan.com): месяц целиком, хранится на телефоне.
@MainActor
final class OnlineTimes: ObservableObject {
    static let shared = OnlineTimes()

    struct Day {
        let zone: TimeZone
        let times: [Prayer: (hour: Int, minute: Int)]
    }

    /// Меняется после каждой загрузки — экраны перерисовываются.
    @Published private(set) var version = 0
    @Published private(set) var updatedAt: Date?
    @Published private(set) var loading = false

    private let prefix = "otimes_"
    private let order: [Prayer] = [.fajr, .sunrise, .dhuhr, .asr, .maghrib, .isha]
    private var memory: [String: [String: Day]] = [:]
    private let defaults = UserDefaults.standard

    private init() {
        updatedAt = defaults.object(forKey: "online_updated_at") as? Date
    }

    private func signature() -> String {
        let s = Store.shared
        return String(format: "%.3f_%.3f_%d_%@", locale: Locale(identifier: "en_US_POSIX"),
                      s.city.latitude, s.city.longitude, s.method.aladhanId ?? 0, s.madhab.rawValue)
    }

    private func key(year: Int, month: Int) -> String {
        prefix + signature() + String(format: "_%04d-%02d", year, month)
    }

    func day(_ date: LocalDay) -> Day? {
        guard Store.shared.onlineTimes, Store.shared.method.aladhanId != nil else { return nil }
        let k = key(year: date.year, month: date.month)
        if memory[k] == nil, let stored = defaults.string(forKey: k), let decoded = decode(stored) {
            memory[k] = decoded
        }
        return memory[k]?[date.key]
    }

    /// Загружает текущий месяц (и следующий, если до конца месяца меньше 10 дней).
    @discardableResult
    func refresh(force: Bool = false) async -> Bool {
        guard Store.shared.onlineTimes else { return false }
        guard let method = Store.shared.method.aladhanId else {
            // Московская Соборная мечеть — своё расписание с dumrf.ru.
            loading = true
            let ok = await DumrfLive.refresh(force: force)
            loading = false
            if ok {
                updatedAt = DumrfLive.updatedAt
                version += 1
            }
            return ok
        }
        let today = LocalDay.today()
        var months = [(today.year, today.month)]
        if today.daysInMonth - today.day < 10 {
            months.append(today.month == 12 ? (today.year + 1, 1) : (today.year, today.month + 1))
        }
        let city = Store.shared.city
        let school = Store.shared.madhab == .hanafi ? 1 : 0
        loading = true
        defer { loading = false }
        var ok = true
        var changed = false
        for (year, month) in months {
            let k = key(year: year, month: month)
            if !force && defaults.string(forKey: k) != nil { continue }
            if let encoded = await download(city: city, year: year, month: month, method: method, school: school) {
                defaults.set(encoded, forKey: k)
                memory[k] = nil
                changed = true
            } else {
                ok = ok && defaults.string(forKey: k) != nil
            }
        }
        if changed {
            updatedAt = Date()
            defaults.set(updatedAt, forKey: "online_updated_at")
            cleanup(keep: Set(months.map { key(year: $0.0, month: $0.1) }))
            version += 1
        }
        return ok
    }

    /// Удаляет данные других городов и старых месяцев (кроме прошлого месяца).
    private func cleanup(keep: Set<String>) {
        let today = LocalDay.today()
        let previous = today.month == 1 ? key(year: today.year - 1, month: 12) : key(year: today.year, month: today.month - 1)
        for k in defaults.dictionaryRepresentation().keys where k.hasPrefix(prefix) && !keep.contains(k) && k != previous {
            defaults.removeObject(forKey: k)
        }
    }

    private func download(city: City, year: Int, month: Int, method: Int, school: Int) async -> String? {
        let urlString = String(
            format: "https://api.aladhan.com/v1/calendar/%d/%d?latitude=%.4f&longitude=%.4f&method=%d&school=%d&latitudeAdjustmentMethod=3",
            locale: Locale(identifier: "en_US_POSIX"), year, month, city.latitude, city.longitude, method, school
        )
        guard let url = URL(string: urlString) else { return nil }
        var request = URLRequest(url: url)
        request.timeoutInterval = 15
        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            guard (response as? HTTPURLResponse)?.statusCode == 200 else { return nil }
            return encode(data)
        } catch {
            return nil
        }
    }

    /// Ответ aladhan.com → {"tz": "...", "days": {"2026-10-24": ["05:29", ...]}}.
    private func encode(_ data: Data) -> String? {
        guard let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let items = root["data"] as? [[String: Any]] else { return nil }
        var days: [String: [String]] = [:]
        var tz = ""
        for item in items {
            guard let timings = item["timings"] as? [String: String],
                  let meta = item["meta"] as? [String: Any], let zone = meta["timezone"] as? String,
                  let date = item["date"] as? [String: Any], let greg = date["gregorian"] as? [String: Any],
                  let ddmmyyyy = greg["date"] as? String else { return nil }
            tz = zone
            let p = ddmmyyyy.split(separator: "-").compactMap { Int($0) }
            guard p.count == 3 else { return nil }
            var list: [String] = []
            for name in ["Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha"] {
                guard let value = timings[name] else { return nil }
                let hhmm = String(value.trimmingCharacters(in: .whitespaces).prefix(5))
                guard Self.parse(hhmm) != nil else { return nil }
                list.append(hhmm)
            }
            days[LocalDay(year: p[2], month: p[1], day: p[0]).key] = list
        }
        guard !days.isEmpty, TimeZone(identifier: tz) != nil else { return nil }
        let object: [String: Any] = ["tz": tz, "days": days]
        guard let out = try? JSONSerialization.data(withJSONObject: object) else { return nil }
        return String(data: out, encoding: .utf8)
    }

    private func decode(_ stored: String) -> [String: Day]? {
        guard let data = stored.data(using: .utf8),
              let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let tz = root["tz"] as? String, let zone = TimeZone(identifier: tz),
              let days = root["days"] as? [String: [String]] else { return nil }
        var result: [String: Day] = [:]
        for (dateKey, list) in days where list.count == order.count {
            var times: [Prayer: (hour: Int, minute: Int)] = [:]
            for (i, p) in order.enumerated() {
                guard let hm = Self.parse(list[i]) else { return nil }
                times[p] = hm
            }
            result[dateKey] = Day(zone: zone, times: times)
        }
        return result
    }

    private static func parse(_ hhmm: String) -> (hour: Int, minute: Int)? {
        let parts = hhmm.split(separator: ":").compactMap { Int($0) }
        guard parts.count == 2, (0...23).contains(parts[0]), (0...59).contains(parts[1]) else { return nil }
        return (parts[0], parts[1])
    }
}

/// Время намаза для выбранного города: из интернета, если загружено, иначе расчёт на телефоне + поправки.
@MainActor
enum PrayerSchedule {

    static func times(for day: LocalDay) -> [Prayer: Date] {
        let s = Store.shared
        let base: [Prayer: Date]
        if s.method == .moscowCathedral {
            base = moscow(day)
        } else if let online = OnlineTimes.shared.day(day) {
            base = fromOnline(day, online)
        } else {
            base = PrayerCalculator.calculate(day: day, latitude: s.city.latitude, longitude: s.city.longitude,
                                              method: s.method, madhab: s.madhab)
        }
        var result: [Prayer: Date] = [:]
        for (p, t) in base {
            result[p] = t.addingTimeInterval(Double(s.adjustment(p)) * 60)
        }
        return result
    }

    static func isOnline(_ day: LocalDay = .today()) -> Bool {
        if Store.shared.method == .moscowCathedral {
            return Store.shared.onlineTimes && DumrfLive.isMoscow() && DumrfLive.corrections() != nil
        }
        return OnlineTimes.shared.day(day) != nil
    }

    /// Московская Соборная мечеть: на сегодня — как на dumrf.ru, на другие дни — формула с поправкой по сайту.
    /// В расписании ДУМ РФ аср стандартный; если выбран ханафитский — аср из расчёта.
    private static func moscow(_ day: LocalDay) -> [Prayer: Date] {
        let s = Store.shared
        let calc = PrayerCalculator.calculate(day: day, latitude: s.city.latitude, longitude: s.city.longitude,
                                              method: .moscowCathedral, madhab: s.madhab)
        guard s.onlineTimes else { return calc }
        let hanafi = s.madhab == .hanafi
        if let official = DumrfLive.official(day) {
            var result: [Prayer: Date] = [:]
            for p in Prayer.allCases {
                if hanafi && p == .asr { result[p] = calc[p] } else if let hm = official[p] {
                    result[p] = day.at(hour: hm.hour, minute: hm.minute, in: DumrfParser.moscowZone)
                }
            }
            return result
        }
        guard let corr = DumrfLive.corrections() else { return calc }
        var result = calc
        for p in Prayer.allCases where !(hanafi && p == .asr) {
            result[p] = calc[p]?.addingTimeInterval(Double(corr[p] ?? 0) * 60)
        }
        return result
    }

    private static func fromOnline(_ day: LocalDay, _ online: OnlineTimes.Day) -> [Prayer: Date] {
        var result: [Prayer: Date] = [:]
        var previous: Date?
        for p in Prayer.allCases {
            guard let hm = online.times[p] else { continue }
            var t = day.at(hour: hm.hour, minute: hm.minute, in: online.zone)
            // Летом на севере иша бывает после полуночи — это уже следующие сутки.
            if let prev = previous, t <= prev { t = t.addingTimeInterval(86_400) }
            previous = t
            result[p] = t
        }
        return result
    }

    /// Ближайший обязательный намаз строго после момента `after`.
    static func next(after now: Date = Date()) -> (prayer: Prayer, time: Date) {
        let today = LocalDay(now)
        for offset in 0...2 {
            let t = times(for: today.adding(days: offset))
            for p in Prayer.obligatory {
                if let time = t[p], time > now { return (p, time) }
            }
        }
        let tomorrow = times(for: today.adding(days: 1))
        return (.fajr, tomorrow[.fajr] ?? now)
    }

    /// Начало предыдущего намаза (для полоски прогресса до следующего).
    static func previousStart(before now: Date = Date()) -> Date? {
        let today = LocalDay(now)
        var starts: [Date] = []
        if let isha = times(for: today.adding(days: -1))[.isha] { starts.append(isha) }
        let t = times(for: today)
        starts += Prayer.obligatory.compactMap { t[$0] }
        return starts.filter { $0 <= now }.max()
    }
}

/// Официальное расписание Московской Соборной мечети (dumrf.ru). Сайт показывает только сегодняшний день:
/// на сегодня берём время ровно как на сайте, на другие дни — формулу с сегодняшней разницей «сайт − формула».
@MainActor
enum DumrfLive {
    private static let key = "dumrf_day" // "2026-10-24|05:15,07:14,12:19,14:34,17:12,18:50"
    private static let atKey = "dumrf_updated_at"

    static var updatedAt: Date? { UserDefaults.standard.object(forKey: atKey) as? Date }

    static func isMoscow() -> Bool {
        DumrfParser.isMoscow(latitude: Store.shared.city.latitude, longitude: Store.shared.city.longitude)
    }

    private static func stored() -> (day: LocalDay, times: [Prayer: (hour: Int, minute: Int)])? {
        guard let raw = UserDefaults.standard.string(forKey: key) else { return nil }
        let parts = raw.split(separator: "|")
        guard parts.count == 2, let day = LocalDay(key: String(parts[0])) else { return nil }
        let list = parts[1].split(separator: ",")
        guard list.count == Prayer.allCases.count else { return nil }
        var times: [Prayer: (hour: Int, minute: Int)] = [:]
        for (i, p) in Prayer.allCases.enumerated() {
            let hm = list[i].split(separator: ":").compactMap { Int($0) }
            guard hm.count == 2 else { return nil }
            times[p] = (hm[0], hm[1])
        }
        return (day, times)
    }

    static func official(_ day: LocalDay) -> [Prayer: (hour: Int, minute: Int)]? {
        guard isMoscow(), let s = stored(), s.day == day else { return nil }
        return s.times
    }

    /// Разница «сайт − формула» в минутах (если сверка была не раньше недели назад), не больше ±10 минут.
    static func corrections() -> [Prayer: Int]? {
        guard isMoscow(), let s = stored() else { return nil }
        let today = LocalDay.today(in: DumrfParser.moscowZone)
        let gap = abs(today.start(in: DumrfParser.moscowZone).timeIntervalSince(s.day.start(in: DumrfParser.moscowZone))) / 86_400
        guard gap <= 7.5 else { return nil }
        let city = Store.shared.city
        let calc = PrayerCalculator.calculate(day: s.day, latitude: city.latitude, longitude: city.longitude,
                                              method: .moscowCathedral, madhab: .standard)
        var result: [Prayer: Int] = [:]
        for p in Prayer.allCases {
            guard let hm = s.times[p], let c = calc[p] else { continue }
            let official = s.day.at(hour: hm.hour, minute: hm.minute, in: DumrfParser.moscowZone)
            let diff = Int((official.timeIntervalSince(c) / 60).rounded())
            result[p] = max(-10, min(10, diff))
        }
        return result
    }

    /// Загружает сегодняшнее расписание (не чаще раза в день, если не force).
    static func refresh(force: Bool) async -> Bool {
        guard isMoscow() else { return false }
        let today = LocalDay.today(in: DumrfParser.moscowZone)
        if !force, stored()?.day == today { return true }
        guard let url = URL(string: "https://dumrf.ru/") else { return false }
        var request = URLRequest(url: url)
        request.timeoutInterval = 15
        request.setValue("ZikrTime (iOS)", forHTTPHeaderField: "User-Agent")
        guard let result = try? await URLSession.shared.data(for: request),
              (result.1 as? HTTPURLResponse)?.statusCode == 200,
              let html = String(data: result.0, encoding: .utf8),
              let times = DumrfParser.parse(html) else { return stored() != nil }
        let list = Prayer.allCases.map { p -> String in
            let hm = times[p]!
            return String(format: "%02d:%02d", hm.hour, hm.minute)
        }
        UserDefaults.standard.set(today.key + "|" + list.joined(separator: ","), forKey: key)
        UserDefaults.standard.set(Date(), forKey: atKey)
        return true
    }
}
