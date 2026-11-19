import Foundation

struct QuizLearningEntry: Codable {
    var version: Int
    var correct: Bool?
    var lastAnswer: Date?
    var review: Date?
    var explanation: Date?
    var stage = 0
}

struct QuizLearning {
    private(set) var entries: [String: QuizLearningEntry]

    init(data: Data) {
        entries = (try? JSONDecoder().decode([String: QuizLearningEntry].self, from: data)) ?? [:]
    }
    func encode() -> Data? { try? JSONEncoder().encode(entries) }
    func entry(_ q: Question) -> QuizLearningEntry {
        if let old = entries[q.id], old.version == q.contentVersion { return old }
        return QuizLearningEntry(version: q.contentVersion)
    }
    mutating func explain(_ q: Question, now: Date = Date()) {
        var row = entry(q)
        row.explanation = max(now, row.lastAnswer ?? .distantPast, row.explanation ?? .distantPast)
        entries[q.id] = row
    }
    mutating func answer(_ q: Question, correct: Bool, now: Date = Date()) {
        var row = entry(q)
        let instant = max(now, row.lastAnswer ?? .distantPast, row.explanation ?? .distantPast)
        let day = 86400.0
        let eligible = correct && (row.review ?? .distantPast) <= now &&
            now.timeIntervalSince(row.explanation ?? .distantPast) >= day
        let intervals = [1, 3, 7, 14, 30]
        if !correct { row.stage = 0 }
        else if eligible { row.stage = min(intervals.count, row.stage + 1) }
        let delay = eligible ? intervals[max(0, row.stage - 1)] : 1
        if !correct || eligible || (row.review ?? .distantPast) <= instant {
            row.review = instant.addingTimeInterval(Double(delay) * day)
        }
        row.correct = correct
        row.lastAnswer = instant
        entries[q.id] = row
    }
    func review(_ pool: [Question], dueOnly: Bool, now: Date = Date()) -> [Question] {
        let known = pool.filter { q in
            let row = entry(q)
            guard let last = row.lastAnswer, last <= now, row.correct != nil else { return false }
            return !dueOnly || (row.review ?? .distantFuture) <= now
        }
        func rank(_ q: Question) -> Int {
            let row = entry(q)
            if row.correct == false { return 0 }
            if row.stage < 2 { return 1 }
            return (row.review ?? .distantFuture) <= now ? 2 : 3
        }
        return Array(known.sorted { a, b in
            if dueOnly, entry(a).review != entry(b).review {
                return (entry(a).review ?? .distantFuture) < (entry(b).review ?? .distantFuture)
            }
            if rank(a) != rank(b) { return rank(a) < rank(b) }
            let first = entry(a).lastAnswer ?? .distantPast, second = entry(b).lastAnswer ?? .distantPast
            return first == second ? a.id < b.id : first < second
        }.prefix(5))
    }
}
