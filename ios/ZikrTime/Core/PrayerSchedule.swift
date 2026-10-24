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
                      s.city.latitude, s.city.longitude, s.method.aladhanId, s.madhab.rawValue)
    }

    private func key(year: Int, month: Int) -> String {
        prefix + signature() + String(format: "_%04d-%02d", year, month)
    }

    func day(_ date: LocalDay) -> Day? {
        guard Store.shared.onlineTimes else { return nil }
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
        let today = LocalDay.today()
        var months = [(today.year, today.month)]
        if today.daysInMonth - today.day < 10 {
            months.append(today.month == 12 ? (today.year + 1, 1) : (today.year, today.month + 1))
        }
        let city = Store.shared.city
        let method = Store.shared.method.aladhanId
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
        if let online = OnlineTimes.shared.day(day) {
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

    static func isOnline(_ day: LocalDay = .today()) -> Bool { OnlineTimes.shared.day(day) != nil }

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
