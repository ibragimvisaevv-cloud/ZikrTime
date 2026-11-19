import Foundation
import WidgetKit

@MainActor
enum WidgetPrayerPublisher {
    static func refresh() {
        let store = Store.shared
        guard store.onboarded, let url = WidgetPrayerSchedule.url else { return }
        let zone = PrayerSchedule.zone
        let today = LocalDay.today(in: zone)
        // Cache two months, matching the app's current/next-month timetable downloads.
        let times = (0...62).flatMap { offset -> [WidgetPrayerSchedule.PrayerTime] in
            let day = PrayerSchedule.times(for: today.adding(days: offset))
            return Prayer.obligatory.compactMap { prayer in
                day[prayer].map { WidgetPrayerSchedule.PrayerTime(title: L(prayer.nameKey), time: $0) }
            }
        }.sorted { $0.time < $1.time }
        let schedule = WidgetPrayerSchedule(
            city: CityCatalog.shared.displayName(store.city, lang: L10n.code),
            heading: L("widget_next_name"), language: L10n.code, timeZone: zone.identifier, times: times
        )
        guard !times.isEmpty, schedule != WidgetPrayerSchedule.load(),
              let data = try? JSONEncoder().encode(schedule) else { return }
        do {
            try data.write(to: url, options: .atomic)
            WidgetCenter.shared.reloadTimelines(ofKind: WidgetPrayerSchedule.kind)
        } catch {
            // Keep the previous complete timetable if the container is temporarily unavailable.
        }
    }
}
