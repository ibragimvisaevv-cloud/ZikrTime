import Foundation

/// Only these display values cross the app-group boundary.
struct WidgetPrayerSchedule: Codable, Equatable {
    struct PrayerTime: Codable, Equatable {
        let title: String
        let time: Date
    }

    static let group = "group.com.zikrtime.ios"
    static let kind = "ZikrTimeNextPrayer"
    static let file = "prayer-widget-v1.json"
    let city: String
    let heading: String
    let language: String
    let timeZone: String
    let times: [PrayerTime]

    static var url: URL? {
        FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: group)?
            .appendingPathComponent(file)
    }

    static func load() -> Self? {
        guard let url, let data = try? Data(contentsOf: url) else { return nil }
        return try? JSONDecoder().decode(Self.self, from: data)
    }
}
