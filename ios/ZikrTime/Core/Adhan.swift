import AVFoundation
import Combine
import Foundation
import UserNotifications

/// Голоса азана (записи с Wikimedia Commons, см. «О приложении»). На iPhone звук уведомления — до 30 секунд.
struct AdhanVoice: Identifiable, Equatable {
    let id: String
    let number: Int

    /// Файл звука (29 с) для уведомлений и будильника.
    var fileName: String { id + "_short.caf" }

    static let all = [AdhanVoice(id: "adhan", number: 1), AdhanVoice(id: "adhan_2", number: 2), AdhanVoice(id: "adhan_3", number: 3)]

    static func byId(_ id: String) -> AdhanVoice { all.first { $0.id == id } ?? all[0] }
}

/// Прослушивание азана при выборе — тихо, примерно на 30% громкости.
@MainActor
final class AdhanPreview: NSObject, ObservableObject, AVAudioPlayerDelegate {
    static let shared = AdhanPreview()

    @Published private(set) var playing: String?
    private var player: AVAudioPlayer?

    func toggle(_ voice: AdhanVoice) {
        if playing == voice.id {
            stop()
            return
        }
        stop()
        let name = (voice.fileName as NSString).deletingPathExtension
        guard let url = Bundle.main.url(forResource: name, withExtension: "caf") else { return }
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .default, options: [.mixWithOthers])
        try? AVAudioSession.sharedInstance().setActive(true)
        guard let p = try? AVAudioPlayer(contentsOf: url) else { return }
        p.volume = 0.3
        p.delegate = self
        p.play()
        player = p
        playing = voice.id
    }

    func stop() {
        player?.stop()
        player = nil
        playing = nil
    }

    nonisolated func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        Task { @MainActor in self.stop() }
    }
}

/// Пятничные напоминания: утром — «Аль-Кахф» и салават, за час до зухра — джума, за час до магриба — время дуа.
/// Тексты — смыслы аята и достоверных хадисов с точными ссылками (см. Android FridayReminders.kt).
@MainActor
enum FridayReminders {
    static let prefix = "friday_"
    static let items = Array(1...7)

    static func text(_ n: Int) -> String { L("friday_\(n)_text") }
    static func source(_ n: Int) -> String { L("friday_\(n)_source") }

    private enum Slot: CaseIterable {
        case morning, jumuah, lastHour
        var title: String {
            switch self {
            case .morning: return L("friday_morning_title")
            case .jumuah: return L("friday_jumuah_title")
            case .lastHour: return L("friday_hour_title")
            }
        }
        var items: [Int] {
            switch self {
            case .morning: return [1, 2]
            case .jumuah: return [6, 5]
            case .lastHour: return [3, 4]
            }
        }
    }

    private static func time(_ slot: Slot, on day: LocalDay) -> Date? {
        let t = PrayerSchedule.times(for: day)
        switch slot {
        case .morning:
            guard let sunrise = t[.sunrise] else { return nil }
            let nine = day.at(hour: 9, minute: 0, in: .current)
            return max(nine, sunrise.addingTimeInterval(30 * 60))
        case .jumuah: return t[.dhuhr]?.addingTimeInterval(-60 * 60)
        case .lastHour: return t[.maghrib]?.addingTimeInterval(-60 * 60)
        }
    }

    /// Планирует напоминания на ближайшие 3 пятницы.
    static func reschedule() async {
        if QA.active { return }
        let center = UNUserNotificationCenter.current()
        let pending = await center.pendingNotificationRequests()
        center.removePendingNotificationRequests(withIdentifiers: pending.map(\.identifier).filter { $0.hasPrefix(prefix) })
        guard Store.shared.fridayReminders, await PrayerNotifications.isAllowed() else { return }
        let now = Date()
        var day = LocalDay(now)
        var fridays: [LocalDay] = []
        for _ in 0..<22 where fridays.count < 3 {
            if day.weekday == 5 { fridays.append(day) } // 1 — понедельник … 5 — пятница
            day = day.adding(days: 1)
        }
        for friday in fridays {
            for slot in Slot.allCases {
                guard let at = time(slot, on: friday), at > now else { continue }
                let content = UNMutableNotificationContent()
                content.title = slot.title
                content.body = slot.items.map { text($0) + " — " + source($0) }.joined(separator: "\n\n")
                content.sound = .default
                let trigger = UNCalendarNotificationTrigger(
                    dateMatching: Calendar.current.dateComponents([.year, .month, .day, .hour, .minute], from: at),
                    repeats: false
                )
                let id = "\(prefix)\(friday.key)_\(slot)"
                try? await center.add(UNNotificationRequest(identifier: id, content: content, trigger: trigger))
            }
        }
    }
}
