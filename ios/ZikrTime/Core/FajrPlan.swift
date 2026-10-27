import Foundation

enum FajrPlan {
    /// Search whole weeks so a single selected weekday is found even six days away.
    static func upcoming(count: Int, from now: Date, weekdays: Set<Int>, offsetMinutes: Int,
                         fajr: (LocalDay) -> Date?) -> [(trigger: Date, fajr: Date)] {
        guard count > 0, !weekdays.intersection(1...7).isEmpty else { return [] }
        let today = LocalDay(now)
        var result: [(trigger: Date, fajr: Date)] = []
        for offset in 0...(count * 7 + 1) {
            let day = today.adding(days: offset)
            guard weekdays.contains(day.weekday), let time = fajr(day) else { continue }
            let trigger = time.addingTimeInterval(-Double(offsetMinutes) * 60)
            guard trigger > now.addingTimeInterval(30) else { continue }
            result.append((trigger, time))
            if result.count == count { break }
        }
        return result
    }
}
