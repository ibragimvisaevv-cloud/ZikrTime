import SwiftUI

enum AppTab: Hashable {
    case prayer, knowledge, shop, qibla, more
}

enum Route: Hashable {
    case language, city, prayerSettings, alarm, tasbih, about, focus
    case quiz(category: String?)
    case seller(String), product(String)
}

/// Вкладки и стопки экранов.
@MainActor
final class Router: ObservableObject {
    @Published var tab: AppTab = .prayer
    @Published var prayerPath: [Route] = []
    @Published var knowledgePath: [Route] = []
    @Published var shopPath: [Route] = []
    @Published var qiblaPath: [Route] = []
    @Published var morePath: [Route] = []
    @Published var onboardingStep = QA.onboardingStep

    func open(_ route: Route, in tab: AppTab) {
        self.tab = tab
        switch tab {
        case .prayer: prayerPath.append(route)
        case .knowledge: knowledgePath.append(route)
        case .shop: shopPath.append(route)
        case .qibla: qiblaPath.append(route)
        case .more: morePath.append(route)
        }
    }
}

struct RootView: View {
    @EnvironmentObject private var store: Store
    @EnvironmentObject private var router: Router

    var body: some View {
        if store.onboarded {
            TabView(selection: $router.tab) {
                NavigationStack(path: $router.prayerPath) {
                    HomeView().destinations()
                }
                .tabItem { Label(L("tab_prayer"), systemImage: "building.columns") }
                .tag(AppTab.prayer)

                NavigationStack(path: $router.knowledgePath) {
                    KnowledgeView().destinations()
                }
                .tabItem { Label(L("tab_knowledge"), systemImage: "book") }
                .tag(AppTab.knowledge)

                NavigationStack(path: $router.shopPath) {
                    ShopView().destinations()
                }
                .tabItem { Label(L("tab_shop"), systemImage: "bag") }
                .tag(AppTab.shop)

                NavigationStack(path: $router.qiblaPath) {
                    QiblaView().destinations()
                }
                .tabItem { Label(L("qibla_title"), systemImage: "location.north.circle") }
                .tag(AppTab.qibla)

                NavigationStack(path: $router.morePath) {
                    MoreView().destinations()
                }
                .tabItem { Label(L("tab_more"), systemImage: "square.grid.2x2") }
                .tag(AppTab.more)
            }
        } else {
            OnboardingView(startStep: router.onboardingStep)
        }
    }
}

extension View {
    /// Экраны второго уровня, общие для всех вкладок.
    func destinations() -> some View {
        navigationDestination(for: Route.self) { route in
            switch route {
            case .language: LanguageView()
            case .city: CityView()
            case .prayerSettings: PrayerSettingsView()
            case .alarm: AlarmSettingsView()
            case .tasbih: TasbihView()
            case .about: AboutView()
            case .focus: FocusInfoView()
            case .quiz(let category): QuizView(category: category)
            case .seller(let id): SellerView(id: id)
            case .product(let id): ProductView(id: id)
            }
        }
    }
}
