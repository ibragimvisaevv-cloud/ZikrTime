import SwiftUI

@main
struct ZikrTimeApp: App {
    @UIApplicationDelegateAdaptor(MapAppDelegate.self) private var appDelegate
    @StateObject private var store = Store.shared
    @StateObject private var online = OnlineTimes.shared
    @StateObject private var shop = ShopStore.shared
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
                .environmentObject(shop)
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
        await FridayReminders.reschedule()
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
    private static var screen: String? {
        #if DEBUG
        return UserDefaults.standard.string(forKey: "qa_screen")
        #else
        return nil
        #endif
    }
    static var active: Bool { screen != nil }
    /// Раунд викторины сразу с карточек подготовки (для скриншота).
    static var quizCards: Bool { screen == "quiz_cards" }

    /// С какого шага открыть знакомство (для скриншотов).
    static var onboardingStep: Int {
        switch screen {
        case "onboarding_welcome": return 1
        case "onboarding_city": return 2
        default: return 0
        }
    }

    static func prepare() {
        guard active else { return }
        let d = UserDefaults.standard
        if let lang = d.string(forKey: "qa_lang"), Store.shared.language != lang {
            Store.shared.language = lang
        }
        if d.bool(forKey: "qa_demo") { Store.shared.seedDemo() }
        if screen == "zakat", d.bool(forKey: "qa_demo") {
            // Show both entered amounts and empty prompts in screenshot checks.
            d.set(["cash": "150000", "gold_g": "12.5", "gold_price": "8000", "silver_price": "100"],
                  forKey: "zakat_inputs")
        }
        if let s = screen, s.hasPrefix("onboarding") { Store.shared.onboarded = false }
    }

    static func route(_ router: Router) {
        guard let s = screen else { return }
        switch s {
        case "onboarding", "onboarding_welcome", "onboarding_city": break
        case "knowledge": router.tab = .knowledge
        case "quiz":
            router.tab = .knowledge
            router.knowledgePath = [.quiz(category: nil)]
        case "qibla": router.tab = .qibla
        case "shop": router.tab = .shop
        case "shop_seller":
            router.tab = .shop
            router.shopPath = [.seller("demo_perfume")]
        case "shop_product":
            router.tab = .shop
            router.shopPath = [.product("p1")]
        case "quiz_cards":
            router.tab = .knowledge
            router.knowledgePath = [.quiz(category: nil)]
        case "more": router.tab = .more
        case "tasbih": router.open(.tasbih, in: .more)
        case "alarm": router.open(.alarm, in: .more)
        case "prayer_settings": router.open(.prayerSettings, in: .more)
        case "language": router.open(.language, in: .more)
        case "city": router.open(.city, in: .more)
        case "about": router.open(.about, in: .more)
        case "focus": router.open(.focus, in: .more)
        case "places": router.open(.places, in: .more)
        case "ramadan": router.open(.ramadan, in: .more)
        case "zakat": router.open(.zakat, in: .more)
        default: router.tab = .prayer
        }
    }
}
