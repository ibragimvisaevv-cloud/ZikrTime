import Foundation
import Combine
import UserNotifications

enum ReadingKind: String, CaseIterable {
    case bedtime, friday
    var title: String { L("reading_" + rawValue) }
    var passage: String { L(self == .bedtime ? "reading_kursi" : "reading_kahf") }
    var chapter: Int { self == .bedtime ? 2 : 18 }
    var ayah: Int { self == .bedtime ? 255 : 1 }
    var defaultMinute: Int { self == .bedtime ? 1290 : 540 }
    var identifier: String { "quran-reading-" + rawValue }
}

@MainActor
final class ReadingReminders: ObservableObject {
    static let shared = ReadingReminders()
    private let center = UNUserNotificationCenter.current()
    private let updates = SerialUpdate()
    private let defaults = UserDefaults.standard
    @Published private(set) var enabled: Set<String>
    @Published private(set) var minutes: [String: Int]
    @Published private(set) var blocked = false
    @Published private(set) var failure: String?

    private init() {
        enabled = Set(UserDefaults.standard.stringArray(forKey: "reading_reminders_enabled") ?? [])
        minutes = UserDefaults.standard.dictionary(forKey: "reading_reminders_times") as? [String: Int] ?? [:]
    }

    func minute(_ kind: ReadingKind) -> Int { minutes[kind.rawValue] ?? kind.defaultMinute }

    func setEnabled(_ on: Bool, kind: ReadingKind) async {
        if on {
            do {
                guard try await center.requestAuthorization(options: [.alert]) else { blocked = true; return }
                blocked = false
            } catch { failure = error.localizedDescription; return }
            enabled.insert(kind.rawValue)
        } else { enabled.remove(kind.rawValue) }
        defaults.set(Array(enabled), forKey: "reading_reminders_enabled")
        await refresh()
    }

    func setMinute(_ value: Int, kind: ReadingKind) async {
        minutes[kind.rawValue] = min(1439, max(0, value))
        defaults.set(minutes, forKey: "reading_reminders_times")
        await refresh()
    }

    func refresh() async {
        await updates.submit { [self] in
            blocked = await center.notificationSettings().authorizationStatus == .denied
            failure = nil
            for kind in ReadingKind.allCases {
                guard enabled.contains(kind.rawValue), !blocked else {
                    center.removePendingNotificationRequests(withIdentifiers: [kind.identifier])
                    continue
                }
                let time = minute(kind)
                var components = DateComponents()
                components.hour = time / 60
                components.minute = time % 60
                if kind == .friday { components.weekday = 6 }
                let content = UNMutableNotificationContent()
                content.title = kind.title
                content.body = kind.passage
                let trigger = UNCalendarNotificationTrigger(dateMatching: components, repeats: true)
                do { try await center.add(UNNotificationRequest(identifier: kind.identifier, content: content, trigger: trigger)) }
                catch { failure = error.localizedDescription }
            }
        }.value
    }
}

@MainActor
final class ReadingDelivery: NSObject, UNUserNotificationCenterDelegate {
    static let shared = ReadingDelivery()
    private weak var router: Router?
    private var pending: ReadingKind?

    func attach(_ router: Router) {
        self.router = router
        if let pending { show(pending) }
    }

    private func show(_ kind: ReadingKind) {
        guard let router else { pending = kind; return }
        pending = nil
        router.open(.quranPassage(chapter: kind.chapter, ayah: kind.ayah), in: .prayer)
    }

    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse) async {
        guard response.actionIdentifier == UNNotificationDefaultActionIdentifier,
              let kind = ReadingKind.allCases.first(where: { $0.identifier == response.notification.request.identifier }) else { return }
        await show(kind)
    }
}
