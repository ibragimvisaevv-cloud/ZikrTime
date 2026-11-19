import Foundation

@MainActor
final class RegionalPrayer {
    static let shared = RegionalPrayer()
    private var lastCity: City?
    private var matched: RegionalPrayerSource?
    private var bundledCity = ""
    private var bundled = RegionalPrayerSource.Month()
    private let defaults = UserDefaults.standard
    private let files = ["regional", "published-2026"].compactMap {
        Bundle.main.url(forResource: $0, withExtension: "tsv", subdirectory: "prayer")
    }
    private var months: [String: RegionalPrayerSource.Month] = [:]

    var source: RegionalPrayerSource? {
        let city = Store.shared.city
        if lastCity != city {
            lastCity = city
            matched = RegionalPrayerSource.match(city, entries: CityCatalog.shared.cities)
        }
        return matched
    }

    private func key(_ source: RegionalPrayerSource, _ day: LocalDay) -> String {
        "regional2_\(source.city)_" + String(format: "%04d-%02d", day.year, day.month)
    }

    private func values(_ source: RegionalPrayerSource, _ day: LocalDay) -> [Int]? {
        let k = key(source, day)
        if months[k] == nil {
            let data = defaults.data(forKey: k)
            months[k] = data.flatMap { try? JSONDecoder().decode(RegionalPrayerSource.Month.self, from: $0) } ?? [:]
        }
        if let values = months[k]?[day.key], values.count == 6 { return values }
        if bundledCity != source.city {
            bundledCity = source.city
            bundled = source.bundledTable(files: files)
        }
        return bundled[day.key]
    }

    func day(_ day: LocalDay) -> OnlineTimes.Day? {
        guard Store.shared.preferRegional, let source, let minutes = values(source, day) else { return nil }
        let times = Dictionary(uniqueKeysWithValues: zip(Prayer.allCases, minutes).map { ($0.0, (hour: $0.1 / 60, minute: $0.1 % 60)) })
        return OnlineTimes.Day(zone: source.zone, times: times)
    }

    func refresh(source: RegionalPrayerSource, force: Bool, isCurrent: () -> Bool) async -> Bool {
        let today = LocalDay.today(in: source.zone)
        let first = LocalDay(year: today.year, month: today.month, day: 1)
        let dates = [first, first.adding(days: first.daysInMonth)]
        for d in dates {
            let k = key(source, d)
            if !force, let updated = defaults.object(forKey: k + "_at") as? Date,
               (0..<86400).contains(Date().timeIntervalSince(updated)), defaults.data(forKey: k) != nil { continue }
            guard let url = source.url(for: d) else { continue }
            var request = URLRequest(url: url)
            request.timeoutInterval = 8
            guard let (data, response) = try? await URLSession.shared.data(for: request), isCurrent(),
                  (response as? HTTPURLResponse)?.statusCode == 200, let month = source.parse(data, month: d),
                  let encoded = try? JSONEncoder().encode(month) else {
                if !isCurrent() { return false }
                continue
            }
            months[k] = month
            defaults.set(encoded, forKey: k)
            defaults.set(Date(), forKey: k + "_at")
        }
        guard isCurrent() else { return false }
        let kept = ([first.adding(days: -1)] + dates).map { key(source, $0) }
        let keep = Set(kept + kept.map { $0 + "_at" })
        for k in defaults.dictionaryRepresentation().keys where k.hasPrefix("regional2_\(source.city)_") && !keep.contains(k) {
            defaults.removeObject(forKey: k)
            months.removeValue(forKey: k)
        }
        return day(today) != nil
    }
}
