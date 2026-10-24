import Foundation

/// Календарный день без часового пояса (год, месяц, число).
struct LocalDay: Hashable, Comparable, Codable {
    let year: Int
    let month: Int
    let day: Int

    init(year: Int, month: Int, day: Int) {
        self.year = year
        self.month = month
        self.day = day
    }

    init(_ date: Date, in zone: TimeZone = .current) {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = zone
        let c = cal.dateComponents([.year, .month, .day], from: date)
        self.init(year: c.year!, month: c.month!, day: c.day!)
    }

    static func today(in zone: TimeZone = .current) -> LocalDay { LocalDay(Date(), in: zone) }

    /// Полночь этого дня в указанном часовом поясе.
    func start(in zone: TimeZone = .current) -> Date {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = zone
        return cal.date(from: DateComponents(year: year, month: month, day: day))!
    }

    /// Момент «этот день, часы:минуты» в указанном часовом поясе.
    func at(hour: Int, minute: Int, in zone: TimeZone) -> Date {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = zone
        return cal.date(from: DateComponents(year: year, month: month, day: day, hour: hour, minute: minute))!
    }

    func adding(days: Int) -> LocalDay {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "UTC")!
        let base = cal.date(from: DateComponents(year: year, month: month, day: day, hour: 12))!
        return LocalDay(cal.date(byAdding: .day, value: days, to: base)!, in: cal.timeZone)
    }

    /// День недели: 1 — понедельник … 7 — воскресенье.
    var weekday: Int {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "UTC")!
        let date = cal.date(from: DateComponents(year: year, month: month, day: day, hour: 12))!
        let w = cal.component(.weekday, from: date) // 1 — воскресенье
        return w == 1 ? 7 : w - 1
    }

    var daysInMonth: Int {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(identifier: "UTC")!
        let date = cal.date(from: DateComponents(year: year, month: month, day: 1))!
        return cal.range(of: .day, in: .month, for: date)!.count
    }

    /// «2026-10-24»
    var key: String { String(format: "%04d-%02d-%02d", year, month, day) }

    init?(key: String) {
        let parts = key.split(separator: "-").compactMap { Int($0) }
        guard parts.count == 3 else { return nil }
        self.init(year: parts[0], month: parts[1], day: parts[2])
    }

    static func < (a: LocalDay, b: LocalDay) -> Bool {
        (a.year, a.month, a.day) < (b.year, b.month, b.day)
    }
}

/// Пять обязательных намазов и восход (восход — не намаз, но нужен для расписания).
enum Prayer: Int, CaseIterable, Codable {
    case fajr, sunrise, dhuhr, asr, maghrib, isha

    var isPrayer: Bool { self != .sunrise }

    static let obligatory: [Prayer] = [.fajr, .dhuhr, .asr, .maghrib, .isha]

    /// Ключ строки с названием.
    var nameKey: String {
        switch self {
        case .fajr: return "prayer_fajr"
        case .sunrise: return "prayer_sunrise"
        case .dhuhr: return "prayer_dhuhr"
        case .asr: return "prayer_asr"
        case .maghrib: return "prayer_maghrib"
        case .isha: return "prayer_isha"
        }
    }
}

/// Методы расчёта (углы солнца под горизонтом для фаджра и иши), как в Android-версии.
enum CalcMethod: String, CaseIterable {
    case russia = "RUSSIA", mwl = "MWL", turkey = "TURKEY", makkah = "MAKKAH", egypt = "EGYPT", karachi = "KARACHI", isna = "ISNA"

    var fajrAngle: Double {
        switch self {
        case .russia: return 16
        case .mwl, .turkey, .karachi: return 18
        case .makkah: return 18.5
        case .egypt: return 19.5
        case .isna: return 15
        }
    }

    var ishaAngle: Double? {
        switch self {
        case .russia: return 15
        case .mwl, .turkey: return 17
        case .makkah: return nil
        case .egypt: return 17.5
        case .karachi: return 18
        case .isna: return 15
        }
    }

    /// Иша через N минут после магриба (Умм аль-Кура).
    var ishaMinutes: Int? { self == .makkah ? 90 : nil }

    /// Номер метода в API aladhan.com.
    var aladhanId: Int {
        switch self {
        case .russia: return 14
        case .mwl: return 3
        case .turkey: return 13
        case .makkah: return 4
        case .egypt: return 5
        case .karachi: return 1
        case .isna: return 2
        }
    }

    var nameKey: String { "method_" + rawValue.lowercased() }
}

/// Мазхаб для аср-намаза: тень равна предмету (1) или вдвое больше (2, ханафитский).
enum AsrMadhab: String, CaseIterable {
    case standard = "STANDARD", hanafi = "HANAFI"

    var shadowFactor: Double { self == .hanafi ? 2 : 1 }
    var nameKey: String { self == .hanafi ? "madhab_hanafi" : "madhab_standard" }
}

/// Астрономический расчёт времени намаза (алгоритм PrayTimes, тот же, что в Android-версии).
enum PrayerCalculator {

    private static let FAJR = 0, SUNRISE = 1, DHUHR = 2, ASR = 3, SUNSET = 4, MAGHRIB = 5, ISHA = 6
    private static let riseSetAngle = 0.833

    static func calculate(day: LocalDay, latitude: Double, longitude: Double, method: CalcMethod, madhab: AsrMadhab) -> [Prayer: Date] {
        let jDate = julian(day.year, day.month, day.day) - longitude / (15.0 * 24.0)

        var t: [Double] = [5, 6, 12, 13, 18, 18, 18]
        for _ in 0..<2 {
            t = compute(t.map { ($0.isNaN ? 0 : $0) / 24.0 }, jDate: jDate, lat: latitude, method: method, madhab: madhab)
        }

        var utc = t.map { $0 - longitude / 15.0 }
        adjustHighLatitudes(&utc, method: method)
        utc[MAGHRIB] = utc[SUNSET]
        if let minutes = method.ishaMinutes { utc[ISHA] = utc[MAGHRIB] + Double(minutes) / 60.0 }

        let midnight = day.start(in: TimeZone(identifier: "UTC")!)
        func at(_ hours: Double) -> Date {
            let minutes = (hours * 60.0 + 0.5).rounded(.down)
            return midnight.addingTimeInterval(minutes * 60)
        }
        return [
            .fajr: at(utc[FAJR]),
            .sunrise: at(utc[SUNRISE]),
            .dhuhr: at(utc[DHUHR]),
            .asr: at(utc[ASR]),
            .maghrib: at(utc[MAGHRIB]),
            .isha: at(utc[ISHA]),
        ]
    }

    private static func compute(_ p: [Double], jDate: Double, lat: Double, method: CalcMethod, madhab: AsrMadhab) -> [Double] {
        let fajr = sunAngleTime(method.fajrAngle, p[FAJR], jDate, lat, ccw: true)
        let sunrise = sunAngleTime(riseSetAngle, p[SUNRISE], jDate, lat, ccw: true)
        let dhuhr = midDay(p[DHUHR], jDate)
        let asr = asrTime(madhab.shadowFactor, p[ASR], jDate, lat)
        let sunset = sunAngleTime(riseSetAngle, p[SUNSET], jDate, lat, ccw: false)
        let isha = method.ishaAngle.map { sunAngleTime($0, p[ISHA], jDate, lat, ccw: false) } ?? Double.nan
        return [fajr, sunrise, dhuhr, asr, sunset, sunset, isha]
    }

    /// Высокие широты: фаджр не раньше, а иша не позже, чем (угол/60) ночи от восхода/заката.
    private static func adjustHighLatitudes(_ t: inout [Double], method: CalcMethod) {
        let night = timeDiff(t[SUNSET], t[SUNRISE])
        let fajrPortion = method.fajrAngle / 60.0 * night
        if t[FAJR].isNaN || timeDiff(t[FAJR], t[SUNRISE]) > fajrPortion {
            t[FAJR] = t[SUNRISE] - fajrPortion
        }
        guard let ishaAngle = method.ishaAngle else { return }
        let ishaPortion = ishaAngle / 60.0 * night
        if t[ISHA].isNaN || timeDiff(t[SUNSET], t[ISHA]) > ishaPortion {
            t[ISHA] = t[SUNSET] + ishaPortion
        }
    }

    // MARK: Астрономия

    private static func sunPosition(_ jd: Double) -> (declination: Double, equation: Double) {
        let d = jd - 2451545.0
        let g = fixAngle(357.529 + 0.98560028 * d)
        let q = fixAngle(280.459 + 0.98564736 * d)
        let l = fixAngle(q + 1.915 * dsin(g) + 0.020 * dsin(2 * g))
        let e = 23.439 - 0.00000036 * d
        let ra = darctan2(dcos(e) * dsin(l), dcos(l)) / 15.0
        let eqt = q / 15.0 - fixHour(ra)
        let decl = darcsin(dsin(e) * dsin(l))
        return (decl, eqt)
    }

    private static func midDay(_ time: Double, _ jDate: Double) -> Double {
        fixHour(12.0 - sunPosition(jDate + time).equation)
    }

    private static func sunAngleTime(_ angle: Double, _ time: Double, _ jDate: Double, _ lat: Double, ccw: Bool) -> Double {
        let decl = sunPosition(jDate + time).declination
        let noon = midDay(time, jDate)
        let cosArg = (-dsin(angle) - dsin(decl) * dsin(lat)) / (dcos(decl) * dcos(lat))
        if cosArg < -1 || cosArg > 1 { return Double.nan }
        let t = darccos(cosArg) / 15.0
        return noon + (ccw ? -t : t)
    }

    private static func asrTime(_ factor: Double, _ time: Double, _ jDate: Double, _ lat: Double) -> Double {
        let decl = sunPosition(jDate + time).declination
        let angle = -darccot(factor + dtan(abs(lat - decl)))
        return sunAngleTime(angle, time, jDate, lat, ccw: false)
    }

    private static func julian(_ yearIn: Int, _ monthIn: Int, _ day: Int) -> Double {
        var year = Double(yearIn)
        var month = Double(monthIn)
        if month <= 2 {
            year -= 1
            month += 12
        }
        let a = (year / 100.0).rounded(.down)
        let b = 2 - a + (a / 4.0).rounded(.down)
        return (365.25 * (year + 4716)).rounded(.down) + (30.6001 * (month + 1)).rounded(.down) + Double(day) + b - 1524.5
    }

    private static func dtr(_ d: Double) -> Double { d * .pi / 180 }
    private static func rtd(_ r: Double) -> Double { r * 180 / .pi }
    private static func dsin(_ d: Double) -> Double { sin(dtr(d)) }
    private static func dcos(_ d: Double) -> Double { cos(dtr(d)) }
    private static func dtan(_ d: Double) -> Double { tan(dtr(d)) }
    private static func darcsin(_ x: Double) -> Double { rtd(asin(x)) }
    private static func darccos(_ x: Double) -> Double { rtd(acos(x)) }
    private static func darctan2(_ y: Double, _ x: Double) -> Double { rtd(atan2(y, x)) }
    private static func darccot(_ x: Double) -> Double { rtd(atan(1 / x)) }
    private static func fixAngle(_ a: Double) -> Double { fix(a, 360) }
    private static func fixHour(_ a: Double) -> Double { fix(a, 24) }
    private static func fix(_ a: Double, _ b: Double) -> Double {
        let r = a - b * (a / b).rounded(.down)
        return r < 0 ? r + b : r
    }
    private static func timeDiff(_ t1: Double, _ t2: Double) -> Double { fixHour(t2 - t1) }
}

/// Направление на Каабу и расстояние до неё.
enum Qibla {
    static let kaabaLat = 21.422487
    static let kaabaLon = 39.826206

    /// Азимут от истинного севера, градусы 0…360.
    static func bearing(lat: Double, lon: Double) -> Double {
        let p1 = lat * .pi / 180, p2 = kaabaLat * .pi / 180
        let dl = (kaabaLon - lon) * .pi / 180
        let y = sin(dl) * cos(p2)
        let x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        let deg = atan2(y, x) * 180 / .pi
        return (deg + 360).truncatingRemainder(dividingBy: 360)
    }

    static func distanceKm(lat: Double, lon: Double) -> Double {
        let r = 6371.0
        let p1 = lat * .pi / 180, p2 = kaabaLat * .pi / 180
        let dp = p2 - p1, dl = (kaabaLon - lon) * .pi / 180
        let a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }
}

/// Серии дней подряд.
enum Streaks {
    /// Сколько дней подряд до сегодняшнего (если сегодня ещё не отмечено — считаем от вчера).
    static func current(_ days: Set<LocalDay>, today: LocalDay) -> Int {
        var day = days.contains(today) ? today : today.adding(days: -1)
        var count = 0
        while days.contains(day) {
            count += 1
            day = day.adding(days: -1)
        }
        return count
    }

    static func best(_ days: Set<LocalDay>) -> Int {
        var best = 0
        for day in days where !days.contains(day.adding(days: -1)) {
            var length = 0
            var d = day
            while days.contains(d) {
                length += 1
                d = d.adding(days: 1)
            }
            best = max(best, length)
        }
        return best
    }
}
