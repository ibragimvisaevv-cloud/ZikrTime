import Foundation

/// Загрузка каталога магазина: сохранённая копия → встроенная → свежая из сети (не чаще раза в минуту).
@MainActor
final class ShopStore: ObservableObject {
    static let shared = ShopStore()

    @Published private(set) var catalog: ShopCatalog
    @Published private(set) var offline = false

    private var refreshing = false
    var orderSeed: String {
        let defaults = UserDefaults.standard
        let id = defaults.string(forKey: "shop_seed") ?? UUID().uuidString
        defaults.set(id, forKey: "shop_seed")
        return id + LocalDay.today().key
    }

    private let key = "shop_catalog"
    private let atKey = "shop_catalog_at"
    private let remote = URL(string: ShopCatalog.contentBase + "catalog.json")!

    private init() {
        if let saved = UserDefaults.standard.string(forKey: key), let data = saved.data(using: .utf8),
           let parsed = try? ShopCatalog.parse(data) {
            catalog = parsed
        } else if let url = Bundle.main.url(forResource: "catalog", withExtension: "json"),
                  let data = try? Data(contentsOf: url), let parsed = try? ShopCatalog.parse(data) {
            catalog = parsed
        } else {
            catalog = ShopCatalog(demo: true, contactTelegram: "", categories: [], sellers: [], products: [])
        }
    }

    func refresh(force: Bool = false) async {
        guard !refreshing else { return }
        refreshing = true
        defer { refreshing = false }
        let last = UserDefaults.standard.object(forKey: atKey) as? Date ?? .distantPast
        if !force && Date().timeIntervalSince(last) < 60 { return } // при каждом открытии магазина, не чаще раза в минуту
        if QA.active { return }
        var request = URLRequest(url: remote)
        request.timeoutInterval = 15
        guard let result = try? await URLSession.shared.data(for: request) else {
            offline = true // нет связи
            return
        }
        guard (result.1 as? HTTPURLResponse)?.statusCode == 200,
              let parsed = try? ShopCatalog.parse(result.0), let text = String(data: result.0, encoding: .utf8) else {
            offline = true
            return
        }
        offline = false
        UserDefaults.standard.set(text, forKey: key)
        UserDefaults.standard.set(Date(), forKey: atKey)
        catalog = parsed
    }
}
