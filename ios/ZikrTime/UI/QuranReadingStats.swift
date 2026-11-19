import Foundation
import SwiftUI

@MainActor
final class QuranReadingStats: ObservableObject {
    struct Day: Codable {
        var seconds = 0
        var verses: Set<String> = []
    }
    static let shared = QuranReadingStats()
    private let key = "quran.reading.days"
    @Published private(set) var days: [String: Day]
    @Published var enabled: Bool {
        didSet { UserDefaults.standard.set(enabled, forKey: "quran.reading.enabled") }
    }
    private init() {
        let defaults = UserDefaults.standard
        enabled = defaults.object(forKey: "quran.reading.enabled") as? Bool ?? true
        days = defaults.data(forKey: key).flatMap { try? JSONDecoder().decode([String: Day].self, from: $0) } ?? [:]
    }
    func record(chapter: Int, finished: Set<Int>) {
        let ymd = Calendar.current.dateComponents([.year, .month, .day], from: Date())
        let today = String(format: "%04d-%02d-%02d", ymd.year ?? 0, ymd.month ?? 0, ymd.day ?? 0)
        var day = days[today] ?? Day()
        day.seconds += 1
        day.verses.formUnion(finished.map { "\(chapter):\($0)" })
        days[today] = day
        if day.seconds.isMultiple(of: 10) || !finished.isEmpty { save() }
    }
    func save() {
        if let data = try? JSONEncoder().encode(days) { UserDefaults.standard.set(data, forKey: key) }
    }
}

/// Only text present in consecutive, stationary viewport samples contributes to completion.
final class QuranReadingSession {
    private var lastTime: TimeInterval?
    private var previous: [Int: Range<Int>] = [:]
    private var coverage: [Int: IndexSet] = [:]
    private var seconds: [Int: Double] = [:]
    private var completed: Set<Int> = []
    func pause() { previous = [:]; lastTime = nil }
    @MainActor func sample(chapter: Int, verses: [QuranVerse]) -> Int? {
        let stats = QuranReadingStats.shared
        guard stats.enabled else { pause(); return nil }
        let visible = QuranArabicText.Coordinator.visibleText(chapter: chapter)
        let now = ProcessInfo.processInfo.systemUptime
        defer { lastTime = now; previous = visible }
        guard let lastTime, now - lastTime < 1.6, !visible.isEmpty, !previous.isEmpty else { return nil }
        var budget = min(1.5, now - lastTime)
        var finished: Set<Int> = []
        var first: Int?
        for id in visible.keys.sorted() {
            guard let seen = visible[id], let old = previous[id], let verse = verses[safeQuranIndex: id - 1] else { continue }
            let a = max(old.lowerBound, seen.lowerBound), b = min(old.upperBound, seen.upperBound)
            guard a < b else { continue }
            if first == nil { first = id }
            if completed.contains(id) { continue }
            coverage[id, default: IndexSet()].insert(integersIn: a..<b)
            let required = min(15, max(1.5, Double(verse.arabic.split(separator: " ").count) * 0.3))
            let delta = min(budget, max(0, required - seconds[id, default: 0]))
            seconds[id, default: 0] += delta
            budget -= delta
            if seconds[id, default: 0] >= required && coverage[id, default: IndexSet()].count >= verse.arabic.utf16.count {
                completed.insert(id)
                finished.insert(id)
            }
        }
        if first != nil { stats.record(chapter: chapter, finished: finished) }
        return first
    }
}

private extension Array {
    subscript(safeQuranIndex index: Int) -> Element? { indices.contains(index) ? self[index] : nil }
}

struct QuranReadingStatsView: View {
    @ObservedObject private var stats = QuranReadingStats.shared
    var body: some View {
        let values = stats.days.values
        let unique = values.reduce(into: Set<String>()) { $0.formUnion($1.verses) }
        List {
            Toggle(L("qs_enable_progress"), isOn: $stats.enabled)
            Section(L("qs_all_time")) {
                let duration = values.reduce(0) { $0 + $1.seconds }
                LabeledContent(L("qs_reading_time"), value: duration > 0 && duration < 60 ? L("qs_less_minute") : L("qs_duration_minutes", duration / 60))
                LabeledContent(L("qs_observed_ayahs"), value: "\(unique.count)")
                LabeledContent(L("qs_active_days"), value: "\(values.count)")
            }
        }.navigationTitle(L("qf_statistics"))
    }
}
