import Foundation

enum Ramadan {
    struct Period {
        let year: Int
        let start: LocalDay
        let days: Int
        var end: LocalDay { start.adding(days: days - 1) }
        func dayOf(_ date: LocalDay) -> Int? {
            guard date >= start, date <= end else { return nil }
            return Int(date.start(in: utc).timeIntervalSince(start.start(in: utc)) / 86400) + 1
        }
    }
    private static let utc = TimeZone(secondsFromGMT: 0)!

    static func currentOrNext(today: LocalDay, shift: Int) -> Period? {
        var calendar = Calendar(identifier: .islamicUmmAlQura)
        calendar.timeZone = utc
        let year = calendar.component(.year, from: today.start(in: utc))
        for y in (year - 1)...(year + 1) {
            guard let date = calendar.date(from: DateComponents(year: y, month: 9, day: 1)),
                  let range = calendar.range(of: .day, in: .month, for: date) else { continue }
            let period = Period(year: y, start: LocalDay(date, in: utc).adding(days: max(-1, min(1, shift))), days: range.count)
            if today <= period.end { return period }
        }
        return nil
    }
}
