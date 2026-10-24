import SwiftUI

@main
struct ZikrTimeApp: App {
    @StateObject private var store = Store.shared
    @StateObject private var online = OnlineTimes.shared
    @StateObject private var router = Router()
    @Environment(\.scenePhase) private var phase

    init() {
        QA.prepare()
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(store)
                .environmentObject(online)
                .environmentObject(router)
                .id(store.language) // смена языка — перестраиваем все экраны
                .tint(Brand.primary)
                .onAppear { QA.route(router) }
        }
        .onChange(of: phase) { _, newPhase in
            if newPhase == .active {
                Task { await Lifecycle.refresh() }
            }
        }
    }
}

/// Что делаем при каждом открытии приложения и после изменения настроек.
@MainActor
enum Lifecycle {
    static func refresh() async {
        await FajrAlarm.stopRingingBurst()
        await OnlineTimes.shared.refresh()
        await reschedule()
    }

    /// Переставить уведомления и будильник (после смены города, метода, поправок, настроек будильника).
    static func reschedule() async {
        if QA.active { return } // при автоматической проверке не показываем системные запросы разрешений
        await PrayerNotifications.reschedule()
        await FajrAlarm.reschedule()
    }

    static func settingsChanged() {
        Task { await reschedule() }
    }

    /// Город, метод или мазхаб изменились — загружаем новое время из интернета и всё переставляем.
    static func locationChanged() {
        Task {
            await OnlineTimes.shared.refresh(force: true)
            await reschedule()
        }
    }
}

/// Только для автоматической проверки в CI: параметры запуска -qa_lang, -qa_demo, -qa_screen.
@MainActor
enum QA {
    private static var screen: String? { UserDefaults.standard.string(forKey: "qa_screen") }
    static var active: Bool { screen != nil }

    static func prepare() {
        let d = UserDefaults.standard
        if let lang = d.string(forKey: "qa_lang"), Store.shared.language != lang {
            Store.shared.language = lang
        }
        if d.bool(forKey: "qa_demo") { Store.shared.seedDemo() }
        if let s = screen, s.hasPrefix("onboarding") { Store.shared.onboarded = false }
    }

    static func route(_ router: Router) {
        guard let s = screen else { return }
        switch s {
        case "onboarding_welcome": router.onboardingStep = 1
        case "onboarding_city": router.onboardingStep = 2
        case "knowledge": router.tab = .knowledge
        case "quiz":
            router.tab = .knowledge
            router.knowledgePath = [.quiz(category: nil)]
        case "qibla": router.tab = .qibla
        case "more": router.tab = .more
        case "tasbih": router.open(.tasbih, in: .more)
        case "alarm": router.open(.alarm, in: .more)
        case "prayer_settings": router.open(.prayerSettings, in: .more)
        case "language": router.open(.language, in: .more)
        case "city": router.open(.city, in: .more)
        case "about": router.open(.about, in: .more)
        case "focus": router.open(.focus, in: .more)
        default: router.tab = .prayer
        }
    }
}
