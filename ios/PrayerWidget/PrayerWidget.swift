import SwiftUI
import WidgetKit

struct PrayerEntry: TimelineEntry {
    let date: Date
    let schedule: WidgetPrayerSchedule?
    var upcoming: [WidgetPrayerSchedule.PrayerTime] {
        Array((schedule?.times ?? []).lazy.filter { $0.time > date }.prefix(3))
    }
}

struct PrayerProvider: TimelineProvider {
    func placeholder(in context: Context) -> PrayerEntry {
        let now = Date()
        let example = WidgetPrayerSchedule(city: "ZikrTime", heading: String(localized: "widget_next_name"),
            language: Locale.current.identifier, timeZone: TimeZone.current.identifier, times: [
                .init(title: String(localized: "prayer_fajr"), time: now.addingTimeInterval(2_700)),
                .init(title: String(localized: "prayer_dhuhr"), time: now.addingTimeInterval(25_200)),
                .init(title: String(localized: "prayer_asr"), time: now.addingTimeInterval(39_600))
            ])
        return PrayerEntry(date: now, schedule: example)
    }

    func getSnapshot(in context: Context, completion: @escaping (PrayerEntry) -> Void) {
        let schedule = WidgetPrayerSchedule.load()
        completion(schedule == nil && context.isPreview ? placeholder(in: context) : PrayerEntry(date: Date(), schedule: schedule))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<PrayerEntry>) -> Void) {
        let now = Date()
        let schedule = WidgetPrayerSchedule.load()
        let nextDay = now.addingTimeInterval(24 * 3600)
        let boundaries = (schedule?.times ?? []).map(\.time).filter { $0 > now && $0 <= nextDay }
        let entries = ([now] + boundaries).map { PrayerEntry(date: $0, schedule: schedule) }
        completion(Timeline(entries: entries, policy: .after(nextDay)))
    }
}

struct PrayerWidgetView: View {
    @Environment(\.widgetFamily) private var family
    let entry: PrayerEntry
    let gold = Color(red: 0.90, green: 0.79, blue: 0.55)

    var body: some View {
        let prayers = entry.upcoming
        HStack(spacing: 14) {
            VStack(alignment: .leading, spacing: 4) {
                Text(verbatim: entry.schedule?.city ?? "Зикр-тайм").font(.caption).lineLimit(1).foregroundStyle(gold)
                if let next = prayers.first, let schedule = entry.schedule {
                    Text(verbatim: schedule.heading).font(.caption2).foregroundStyle(.white.opacity(0.82))
                    Text(verbatim: next.title).font(.headline).lineLimit(1)
                    Text(next.time, style: .time).font(.title.bold()).minimumScaleFactor(0.72).lineLimit(1)
                    Text(timerInterval: entry.date...next.time, countsDown: true)
                        .font(.caption.monospacedDigit()).foregroundStyle(gold)
                } else {
                    Text("widget_open_app").font(.headline).fixedSize(horizontal: false, vertical: true)
                }
            }
            if family == .systemMedium && prayers.count > 1 {
                Divider().overlay(gold.opacity(0.55))
                VStack(alignment: .leading, spacing: 7) {
                    ForEach(Array(prayers.dropFirst()), id: \.time) { prayer in
                        Text(verbatim: prayer.title).font(.caption).foregroundStyle(gold)
                        Text(prayer.time, style: .time).font(.headline)
                    }
                }
            }
            Spacer(minLength: 0)
        }
        .foregroundStyle(.white)
        .environment(\.locale, Locale(identifier: entry.schedule?.language ?? Locale.current.identifier))
        .environment(\.timeZone, TimeZone(identifier: entry.schedule?.timeZone ?? "") ?? .current)
        .containerBackground(for: .widget) {
            LinearGradient(colors: [Color(red: 0.06, green: 0.19, blue: 0.17),
                                    Color(red: 0.11, green: 0.13, blue: 0.15)],
                           startPoint: .topLeading, endPoint: .bottomTrailing)
        }
    }
}

@main
struct ZikrTimePrayerWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: WidgetPrayerSchedule.kind, provider: PrayerProvider()) { entry in
            PrayerWidgetView(entry: entry)
        }
        .configurationDisplayName("widget_next_name")
        .description("widget_description")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}
