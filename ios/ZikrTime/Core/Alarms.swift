import Foundation
import SwiftUI
import UserNotifications
#if canImport(AlarmKit)
import ActivityKit
import AlarmKit
#endif

/// Уведомления о времени намаза.
@MainActor
enum PrayerNotifications {
    static let prefix = "prayer_"
    private static let updates = SerialUpdate()
    private static var center: UNUserNotificationCenter { UNUserNotificationCenter.current() }

    @discardableResult
    static func requestPermission() async -> Bool {
        (try? await center.requestAuthorization(options: [.alert, .sound, .badge])) ?? false
    }

    static func isAllowed() async -> Bool {
        let status = await center.notificationSettings().authorizationStatus
        return status == .authorized || status == .provisional
    }

    /// Планирует уведомления на ближайшие дни (iOS хранит не больше 64 запланированных уведомлений на приложение:
    /// до 24 о намазах, до 9 пятничных и до 30 — запасной будильник на старых iOS).
    static func reschedule() async {
        if QA.active { return }
        await updates.submit { await replaceSchedule() }.value
    }

    private static func replaceSchedule() async {
        let pending = await center.pendingNotificationRequests()
        center.removePendingNotificationRequests(withIdentifiers: pending.map(\.identifier).filter { $0.hasPrefix(prefix) })
        let store = Store.shared
        guard store.notify, await isAllowed() else { return }
        let now = Date()
        let today = LocalDay(now)
        let place = CityCatalog.shared.displayName(store.city, lang: L10n.code)
        var count = 0
        for offset in 0..<7 {
            let day = today.adding(days: offset)
            let times = PrayerSchedule.times(for: day)
            for p in Prayer.obligatory {
                guard let time = times[p], time > now, count < 24 else { continue }
                let content = UNMutableNotificationContent()
                content.title = L("notify_prayer_title", L(p.nameKey))
                content.body = L("notify_prayer_text", Fmt.clock(time), place)
                content.sound = store.notifyAdhan ? UNNotificationSound(named: UNNotificationSoundName(AdhanVoice.byId(store.notifyVoice).fileName)) : .default
                content.interruptionLevel = .timeSensitive
                let trigger = UNCalendarNotificationTrigger(
                    dateMatching: Calendar.current.dateComponents([.year, .month, .day, .hour, .minute], from: time),
                    repeats: false
                )
                let request = UNNotificationRequest(identifier: "\(prefix)\(day.key)_\(p.rawValue)", content: content, trigger: trigger)
                try? await center.add(request)
                count += 1
            }
        }
    }
}

/// Будильник на фаджр: на iOS 26+ — системный будильник (AlarmKit), на старых версиях — серия громких уведомлений.
@MainActor
enum FajrAlarm {
    private static let fallbackPrefix = "fajr_"
    private static let updates = SerialUpdate()
    private static var center: UNUserNotificationCenter { UNUserNotificationCenter.current() }

    /// Есть ли системные будильники (iOS 26 и новее).
    static var usesAlarmKit: Bool {
        if #available(iOS 26.0, *) { return true }
        return false
    }

    /// Ближайшие сигналы: время будильника и время фаджра (с учётом выбранных дней недели).
    static func upcoming(days: Int, from now: Date = Date()) -> [(trigger: Date, fajr: Date)] {
        let store = Store.shared
        return FajrPlan.upcoming(count: days, from: now, weekdays: store.alarmDays, offsetMinutes: store.alarmOffset) { day in
            PrayerSchedule.times(for: day)[.fajr]
        }
    }

    static func next() -> (trigger: Date, fajr: Date)? {
        Store.shared.alarmEnabled ? upcoming(days: 1).first : nil
    }

    /// Переставляет будильники после любых изменений (время намаза, город, настройки).
    static func reschedule() async {
        if QA.active { return }
        await updates.submit { await replaceSchedule() }.value
    }

    private static func replaceSchedule() async {
        await cancelAll()
        guard Store.shared.alarmEnabled else { return }
        #if canImport(AlarmKit)
        if #available(iOS 26.0, *) {
            if await AlarmKitScheduler.authorize() {
                await AlarmKitScheduler.schedule(upcoming(days: 14))
                return
            }
        }
        #endif
        await scheduleFallback(upcoming(days: 3))
    }

    /// Проверка: сигнал через 10 секунд.
    static func scheduleTest() async {
        let at = Date().addingTimeInterval(10)
        #if canImport(AlarmKit)
        if #available(iOS 26.0, *) {
            if await AlarmKitScheduler.authorize() {
                await AlarmKitScheduler.add(trigger: at, fajr: next()?.fajr ?? at, test: true)
                return
            }
        }
        #endif
        await scheduleFallback([(at, next()?.fajr ?? at)], burst: 3)
    }

    /// Разрешены ли будильники (на iOS 26+) — для подсказки на экране настроек.
    static func isDenied() -> Bool {
        #if canImport(AlarmKit)
        if #available(iOS 26.0, *) {
            return AlarmManager.shared.authorizationState == .denied
        }
        #endif
        return false
    }

    private static func cancelAll() async {
        #if canImport(AlarmKit)
        if #available(iOS 26.0, *) { AlarmKitScheduler.cancelAll() }
        #endif
        let pending = await center.pendingNotificationRequests()
        center.removePendingNotificationRequests(withIdentifiers: pending.map(\.identifier).filter { $0.hasPrefix(fallbackPrefix) })
    }

    /// Старые версии iOS: 10 уведомлений с азаном, по одному в минуту.
    private static func scheduleFallback(_ items: [(trigger: Date, fajr: Date)], burst: Int = 10) async {
        var allowed = await PrayerNotifications.isAllowed()
        if !allowed { allowed = await PrayerNotifications.requestPermission() }
        guard allowed else { return }
        for item in items {
            for k in 0..<burst {
                let content = UNMutableNotificationContent()
                content.title = L("alarm_ring_title")
                content.body = L("alarm_fajr_at", Fmt.clock(item.fajr)) + " · " + L("alarm_better_than_sleep")
                content.sound = UNNotificationSound(named: UNNotificationSoundName(AdhanVoice.byId(Store.shared.alarmVoice).fileName))
                content.interruptionLevel = .timeSensitive
                let at = item.trigger.addingTimeInterval(Double(k) * 60)
                let trigger = UNCalendarNotificationTrigger(
                    dateMatching: Calendar.current.dateComponents([.year, .month, .day, .hour, .minute, .second], from: at),
                    repeats: false
                )
                let id = "\(fallbackPrefix)\(Int(item.trigger.timeIntervalSince1970))_\(k)"
                try? await center.add(UNNotificationRequest(identifier: id, content: content, trigger: trigger))
            }
        }
    }

    /// Пользователь открыл приложение — значит, проснулся: убираем оставшиеся уведомления текущего сигнала.
    static func stopRingingBurst() async {
        let now = Date()
        let pending = await center.pendingNotificationRequests()
        let ids = pending.compactMap { request -> String? in
            guard request.identifier.hasPrefix(fallbackPrefix),
                  let stamp = Double(request.identifier.dropFirst(fallbackPrefix.count).split(separator: "_").first ?? "") else { return nil }
            let start = Date(timeIntervalSince1970: stamp)
            return start <= now && now.timeIntervalSince(start) < 15 * 60 ? request.identifier : nil
        }
        center.removePendingNotificationRequests(withIdentifiers: ids)
        let delivered = await center.deliveredNotifications().map(\.request.identifier).filter { $0.hasPrefix(fallbackPrefix) }
        center.removeDeliveredNotifications(withIdentifiers: delivered)
    }
}

#if canImport(AlarmKit)
/// Данные будильника для системы (нам ничего дополнительно хранить не нужно).
@available(iOS 26.0, *)
struct FajrAlarmMetadata: AlarmMetadata {}

@available(iOS 26.0, *)
@MainActor
enum AlarmKitScheduler {
    private static let idsKey = "alarmkit_ids"

    static func authorize() async -> Bool {
        let manager = AlarmManager.shared
        switch manager.authorizationState {
        case .authorized:
            return true
        case .denied:
            return false
        default:
            let state = (try? await manager.requestAuthorization()) ?? .denied
            return state == .authorized
        }
    }

    static func schedule(_ items: [(trigger: Date, fajr: Date)]) async {
        for item in items {
            await add(trigger: item.trigger, fajr: item.fajr, test: false)
        }
    }

    static func add(trigger: Date, fajr: Date, test: Bool) async {
        let title = (test ? L("alarm_test_label") + " · " : "") + L("alarm_ring_title")
        let stop = AlarmButton(text: LocalizedStringResource(stringLiteral: L("alarm_awake")), textColor: .white, systemImageName: "sun.horizon.fill")
        let alert = AlarmPresentation.Alert(
            title: LocalizedStringResource(stringLiteral: title),
            stopButton: stop,
            secondaryButton: nil,
            secondaryButtonBehavior: nil
        )
        let attributes = AlarmAttributes<FajrAlarmMetadata>(
            presentation: AlarmPresentation(alert: alert),
            metadata: FajrAlarmMetadata(),
            tintColor: Color(red: 0.886, green: 0.714, blue: 0.349)
        )
        let configuration = AlarmManager.AlarmConfiguration<FajrAlarmMetadata>.alarm(
            schedule: .fixed(trigger),
            attributes: attributes,
            stopIntent: nil,
            secondaryIntent: nil,
            sound: .named(AdhanVoice.byId(Store.shared.alarmVoice).fileName)
        )
        let id = UUID()
        do {
            _ = try await AlarmManager.shared.schedule(id: id, configuration: configuration)
            var ids = UserDefaults.standard.stringArray(forKey: idsKey) ?? []
            ids.append(id.uuidString)
            UserDefaults.standard.set(ids, forKey: idsKey)
        } catch {
            // Не удалось — сработает при следующем открытии приложения (переставим заново).
        }
    }

    static func cancelAll() {
        let manager = AlarmManager.shared
        var ids = Set((UserDefaults.standard.stringArray(forKey: idsKey) ?? []).compactMap { UUID(uuidString: $0) })
        if let alarms = try? manager.alarms {
            for alarm in alarms { ids.insert(alarm.id) }
        }
        for id in ids {
            try? manager.cancel(id: id)
        }
        UserDefaults.standard.set([String](), forKey: idsKey)
    }
}
#endif
