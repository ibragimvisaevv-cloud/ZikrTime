import Combine
import Foundation

/// Все настройки и отметки пользователя. Хранятся только на телефоне (UserDefaults).
@MainActor
final class Store: ObservableObject {
    static let shared = Store()

    private let d: UserDefaults

    private enum K {
        static let onboarded = "onboarded", language = "language", city = "city", method = "method", madhab = "madhab"
        static let online = "online_times", adjustments = "adjustments", notify = "notify", notifyAdhan = "notify_adhan"
        static let notifyVoice = "notify_voice", alarmVoice = "alarm_voice", friday = "friday_reminders"
        static let alarmOn = "alarm_on", alarmOffset = "alarm_offset", alarmDays = "alarm_days"
        static let prayed = "prayed", quizDays = "quiz_days", totalCorrect = "total_correct", totalAnswered = "total_answered"
        static let catCorrect = "cat_correct", recent = "recent_questions"
        static let tasbihCount = "tasbih_count", tasbihTarget = "tasbih_target", tasbihPhrase = "tasbih_phrase", tasbihDays = "tasbih_days"
    }

    // MARK: Общие
    @Published var onboarded: Bool { didSet { d.set(onboarded, forKey: K.onboarded) } }
    /// Выбранный язык ("" — как в телефоне).
    @Published var language: String {
        didSet {
            d.set(language, forKey: K.language)
            L10n.apply(language)
        }
    }

    // MARK: Город и расчёт
    @Published var city: City { didSet { d.set(try? JSONEncoder().encode(city), forKey: K.city) } }
    @Published var method: CalcMethod { didSet { d.set(method.rawValue, forKey: K.method) } }
    @Published var madhab: AsrMadhab { didSet { d.set(madhab.rawValue, forKey: K.madhab) } }
    @Published var onlineTimes: Bool { didSet { d.set(onlineTimes, forKey: K.online) } }
    /// Ручные поправки в минутах для каждого времени (индекс = Prayer.rawValue).
    @Published var adjustments: [Int] { didSet { d.set(adjustments, forKey: K.adjustments) } }

    // MARK: Уведомления
    @Published var notify: Bool { didSet { d.set(notify, forKey: K.notify) } }
    @Published var notifyAdhan: Bool { didSet { d.set(notifyAdhan, forKey: K.notifyAdhan) } }
    /// Голос азана в уведомлениях и в будильнике ("adhan", "adhan_2", "adhan_3").
    @Published var notifyVoice: String { didSet { d.set(notifyVoice, forKey: K.notifyVoice) } }
    @Published var alarmVoice: String { didSet { d.set(alarmVoice, forKey: K.alarmVoice) } }
    /// Пятничные напоминания (Аль-Кахф, джума, последний час).
    @Published var fridayReminders: Bool { didSet { d.set(fridayReminders, forKey: K.friday) } }

    // MARK: Будильник на фаджр
    @Published var alarmEnabled: Bool { didSet { d.set(alarmEnabled, forKey: K.alarmOn) } }
    /// За сколько минут до фаджра будить (0…120).
    @Published var alarmOffset: Int { didSet { d.set(alarmOffset, forKey: K.alarmOffset) } }
    /// Дни недели: 1 — понедельник … 7 — воскресенье.
    @Published var alarmDays: Set<Int> { didSet { d.set(Array(alarmDays).sorted(), forKey: K.alarmDays) } }

    // MARK: Отметки и статистика
    /// "2026-10-24" → совершённые намазы (Prayer.rawValue).
    @Published private(set) var prayed: [String: [Int]] { didSet { d.set(prayed, forKey: K.prayed) } }
    @Published private(set) var quizDays: [String] { didSet { d.set(quizDays, forKey: K.quizDays) } }
    @Published private(set) var totalCorrect: Int { didSet { d.set(totalCorrect, forKey: K.totalCorrect) } }
    @Published private(set) var totalAnswered: Int { didSet { d.set(totalAnswered, forKey: K.totalAnswered) } }
    @Published private(set) var categoryCorrect: [String: Int] { didSet { d.set(categoryCorrect, forKey: K.catCorrect) } }
    @Published private(set) var recentQuestions: [String] { didSet { d.set(recentQuestions, forKey: K.recent) } }

    // MARK: Тасбих
    @Published var tasbihCount: Int { didSet { d.set(tasbihCount, forKey: K.tasbihCount) } }
    @Published var tasbihTarget: Int { didSet { d.set(tasbihTarget, forKey: K.tasbihTarget) } }
    @Published var tasbihPhrase: Int { didSet { d.set(tasbihPhrase, forKey: K.tasbihPhrase) } }
    @Published private(set) var tasbihDays: [String: Int] { didSet { d.set(tasbihDays, forKey: K.tasbihDays) } }

    init(defaults: UserDefaults = .standard) {
        d = defaults
        onboarded = defaults.bool(forKey: K.onboarded)
        language = defaults.string(forKey: K.language) ?? ""
        if let data = defaults.data(forKey: K.city), let saved = try? JSONDecoder().decode(City.self, from: data) {
            city = saved
        } else {
            city = .moscow
        }
        method = CalcMethod(rawValue: defaults.string(forKey: K.method) ?? "") ?? .russia
        madhab = AsrMadhab(rawValue: defaults.string(forKey: K.madhab) ?? "") ?? .standard
        onlineTimes = defaults.object(forKey: K.online) as? Bool ?? true
        let adj = defaults.array(forKey: K.adjustments) as? [Int] ?? []
        adjustments = adj.count == Prayer.allCases.count ? adj : Array(repeating: 0, count: Prayer.allCases.count)
        notify = defaults.object(forKey: K.notify) as? Bool ?? true
        notifyAdhan = defaults.object(forKey: K.notifyAdhan) as? Bool ?? false
        notifyVoice = AdhanVoice.byId(defaults.string(forKey: K.notifyVoice) ?? "").id
        alarmVoice = AdhanVoice.byId(defaults.string(forKey: K.alarmVoice) ?? "").id
        fridayReminders = defaults.object(forKey: K.friday) as? Bool ?? true
        alarmEnabled = defaults.bool(forKey: K.alarmOn)
        alarmOffset = defaults.object(forKey: K.alarmOffset) as? Int ?? 30
        alarmDays = Set(defaults.array(forKey: K.alarmDays) as? [Int] ?? Array(1...7))
        prayed = defaults.dictionary(forKey: K.prayed) as? [String: [Int]] ?? [:]
        quizDays = defaults.stringArray(forKey: K.quizDays) ?? []
        totalCorrect = defaults.integer(forKey: K.totalCorrect)
        totalAnswered = defaults.integer(forKey: K.totalAnswered)
        categoryCorrect = defaults.dictionary(forKey: K.catCorrect) as? [String: Int] ?? [:]
        recentQuestions = defaults.stringArray(forKey: K.recent) ?? []
        tasbihCount = defaults.integer(forKey: K.tasbihCount)
        tasbihTarget = defaults.object(forKey: K.tasbihTarget) as? Int ?? 33
        tasbihPhrase = defaults.integer(forKey: K.tasbihPhrase)
        tasbihDays = defaults.dictionary(forKey: K.tasbihDays) as? [String: Int] ?? [:]
        L10n.apply(language)
    }

    // MARK: Намазы

    func adjustment(_ p: Prayer) -> Int { adjustments[p.rawValue] }

    func setAdjustment(_ p: Prayer, _ minutes: Int) {
        var a = adjustments
        a[p.rawValue] = max(-30, min(30, minutes))
        adjustments = a
    }

    func prayedOn(_ day: LocalDay) -> Set<Prayer> {
        Set((prayed[day.key] ?? []).compactMap { Prayer(rawValue: $0) })
    }

    func setPrayed(_ day: LocalDay, _ p: Prayer, _ done: Bool) {
        var set = prayedOn(day)
        if done { set.insert(p) } else { set.remove(p) }
        var all = prayed
        all[day.key] = set.map(\.rawValue).sorted()
        prayed = Self.trimmed(all)
    }

    /// Дни, когда отмечены все пять намазов.
    var prayerDays: Set<LocalDay> {
        Set(prayed.compactMap { key, list in list.count >= Prayer.obligatory.count ? LocalDay(key: key) : nil })
    }

    // MARK: Викторина

    var quizDaySet: Set<LocalDay> { Set(quizDays.compactMap { LocalDay(key: $0) }) }

    func recordAnswer(id: String, category: String, correct: Bool) {
        totalAnswered += 1
        if correct {
            totalCorrect += 1
            categoryCorrect[category, default: 0] += 1
        }
        var recent = recentQuestions.filter { $0 != id }
        recent.append(id)
        if recent.count > 60 { recent.removeFirst(recent.count - 60) }
        recentQuestions = recent
    }

    func recordRound() {
        let key = LocalDay.today().key
        if !quizDays.contains(key) {
            quizDays = Array((quizDays + [key]).suffix(400))
        }
    }

    // MARK: Тасбих

    func addTasbih() {
        tasbihCount += 1
        let key = LocalDay.today().key
        var days = tasbihDays
        days[key, default: 0] += 1
        tasbihDays = Self.trimmed(days)
    }

    func tasbihOn(_ day: LocalDay) -> Int { tasbihDays[day.key] ?? 0 }

    /// Храним не больше 400 последних дней.
    private static func trimmed<V>(_ dict: [String: V]) -> [String: V] {
        guard dict.count > 400 else { return dict }
        let keep = Set(dict.keys.sorted().suffix(400))
        return dict.filter { keep.contains($0.key) }
    }

    // MARK: Для проверки в CI

    /// Демо-данные для скриншотов (запуск с параметром -qa_demo 1).
    func seedDemo() {
        onboarded = true
        city = CityCatalog.shared.cities.first { $0.id == "moscow" }?.city(L10n.code) ?? .moscow
        alarmEnabled = true
        setPrayed(.today(), .fajr, true)
        if tasbihCount == 0 {
            for _ in 0..<21 { addTasbih() }
        }
    }
}
