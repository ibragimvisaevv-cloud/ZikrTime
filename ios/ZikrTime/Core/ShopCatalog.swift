import Foundation

/// Каталог магазина — тот же JSON, что и в Android-версии (встроенный assets/shop/catalog.json; свежий собирает бот продавцов в Yandex Object Storage).
struct ShopCatalog {
    static let contentBase = "https://storage.yandexcloud.net/zikrtime-shop/"

    /// Текст на нескольких языках: {"ru": "...", "en": "..."}.
    struct Text {
        let values: [String: String]
        func get(_ lang: String) -> String { values[lang] ?? values["ru"] ?? values["en"] ?? values.values.first ?? "" }
        var isEmpty: Bool { values.values.allSatisfy { $0.trimmingCharacters(in: .whitespaces).isEmpty } }
    }

    struct Category: Identifiable {
        let id: String
        let name: Text
        let icon: String
    }

    struct Seller: Identifiable {
        let id: String
        let name: String
        let partner: Bool
        let city: Text
        let description: Text
        let address: String
        let phone: String
        let telegram: String
        let whatsapp: String
        let instagram: String
        let website: String
        let lat: Double?
        let lon: Double?
        let logo: String
        let cover: String
        let categories: [String]
        let accent: UInt32?
        var promoUntil: Double = 0
        func promoted(at date: Date) -> Bool { promoUntil > date.timeIntervalSince1970 }
    }

    struct Product: Identifiable {
        let id: String
        let seller: String
        let category: String
        let title: Text
        let description: Text
        let price: Int?
        let oldPrice: Int?
        let unit: Text
        let currency: String
        let images: [String]
    }

    let demo: Bool
    let contactTelegram: String
    /// Telegram-бот, через который продавцы добавляют свои компании.
    var sellerBot: String = ""
    let categories: [Category]
    let sellers: [Seller]
    let products: [Product]

    func seller(_ id: String?) -> Seller? { sellers.first { $0.id == id } }
    func product(_ id: String?) -> Product? { products.first { $0.id == id } }
    func category(_ id: String) -> Category? { categories.first { $0.id == id } }

    struct ParseError: Error {}

    static func parse(_ data: Data) throws -> ShopCatalog {
        guard let root = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              root["sellers"] is [[String: Any]], root["products"] is [[String: Any]] else { throw ParseError() }
        func text(_ o: [String: Any], _ key: String) -> Text {
            if let dict = o[key] as? [String: String] { return Text(values: dict) }
            if let s = o[key] as? String { return Text(values: ["ru": s]) }
            return Text(values: [:])
        }
        func string(_ o: [String: Any], _ key: String) -> String { (o[key] as? String) ?? "" }
        func objects(_ key: String) -> [[String: Any]] { root[key] as? [[String: Any]] ?? [] }
        func double(_ o: [String: Any], _ key: String) -> Double? {
            if let d = o[key] as? Double { return d }
            if let i = o[key] as? Int { return Double(i) }
            return nil
        }
        func int(_ o: [String: Any], _ key: String) -> Int? {
            let v = (o[key] as? Int) ?? double(o, key).flatMap { $0.isFinite && $0 >= 0 && $0 < Double(Int.max) ? Int($0) : nil }
            return v.flatMap { $0 >= 0 ? $0 : nil }
        }
        func handle(_ s: String) -> String { s.hasPrefix("@") ? String(s.dropFirst()) : s }

        let categories = objects("categories").compactMap { o -> Category? in
            guard let id = o["id"] as? String else { return nil }
            return Category(id: id, name: text(o, "names"), icon: (o["icon"] as? String) ?? "store")
        }
        let sellers = objects("sellers").compactMap { o -> Seller? in
            guard let id = o["id"] as? String, let name = o["name"] as? String else { return nil }
            var accent: UInt32?
            let hex = string(o, "accent").replacingOccurrences(of: "#", with: "")
            if hex.count == 6 { accent = UInt32(hex, radix: 16) }
            return Seller(
                id: id, name: name, partner: (o["partner"] as? Bool) ?? false,
                city: text(o, "city"), description: text(o, "description"),
                address: string(o, "address"), phone: string(o, "phone"),
                telegram: handle(string(o, "telegram")), whatsapp: string(o, "whatsapp"),
                instagram: handle(string(o, "instagram")), website: string(o, "website"),
                lat: double(o, "lat"), lon: double(o, "lon"),
                logo: string(o, "logo"), cover: string(o, "cover"),
                categories: o["categories"] as? [String] ?? [], accent: accent,
                promoUntil: double(o, "promo_until") ?? 0
            )
        }
        let sellerIds = Set(sellers.map(\.id))
        let products = objects("products").compactMap { o -> Product? in
            guard let id = o["id"] as? String, let seller = o["seller"] as? String, sellerIds.contains(seller) else { return nil }
            return Product(
                id: id, seller: seller, category: string(o, "category"),
                title: text(o, "title"), description: text(o, "description"),
                price: int(o, "price"), oldPrice: int(o, "old_price"), unit: text(o, "unit"),
                currency: (o["currency"] as? String) ?? "RUB", images: o["images"] as? [String] ?? []
            )
        }
        return ShopCatalog(
            demo: (root["demo"] as? Bool) ?? false,
            contactTelegram: handle(string(root, "contact_telegram")),
            sellerBot: handle(string(root, "seller_bot")),
            categories: categories, sellers: sellers, products: products
        )
    }

    /// Stable daily rotation, with active promotions first, as on Android.
    func rankedSellers(seed: String, at date: Date = Date()) -> [Seller] {
        sellers.sorted {
            if $0.promoted(at: date) != $1.promoted(at: date) { return $0.promoted(at: date) }
            let a = Self.rank(seed + $0.id), b = Self.rank(seed + $1.id)
            return a == b ? $0.id < $1.id : a < b
        }
    }
    func rankedProducts(seed: String, at date: Date = Date()) -> [Product] {
        let order = Dictionary(rankedSellers(seed: seed, at: date).enumerated().map { ($1.id, $0) }, uniquingKeysWith: { a, _ in a })
        return products.sorted {
            let a = seller($0.seller)?.promoted(at: date) == true
            let b = seller($1.seller)?.promoted(at: date) == true
            if a != b { return a }
            if a && $0.seller != $1.seller { return (order[$0.seller] ?? 0) < (order[$1.seller] ?? 0) }
            let x = Self.rank(seed + $0.id), y = Self.rank(seed + $1.id)
            return x == y ? $0.id < $1.id : x < y
        }
    }
    private static func rank(_ text: String) -> UInt64 {
        text.utf8.reduce(UInt64(14695981039346656037)) { ($0 ^ UInt64($1)) &* 1099511628211 }
    }

    /// Адрес картинки: полный URL или путь внутри репозитория каталога.
    static func imageURL(_ path: String) -> URL? {
        if path.isEmpty { return nil }
        if path.hasPrefix("http://") || path.hasPrefix("https://") { return URL(string: path) }
        if path.hasPrefix("asset:") { return nil }
        return URL(string: contentBase + path)
    }

    /// «2 500 ₽»
    static func price(_ value: Int, currency: String) -> String {
        let digits = String(value)
        var grouped = ""
        for (i, ch) in digits.reversed().enumerated() {
            if i > 0 && i % 3 == 0 { grouped.append(" ") }
            grouped.append(ch)
        }
        let sign: String
        switch currency {
        case "RUB": sign = "₽"
        case "USD": sign = "$"
        case "EUR": sign = "€"
        case "KZT": sign = "₸"
        case "UZS": sign = "сўм"
        case "TJS": sign = "сом."
        case "KGS": sign = "сом"
        case "AZN": sign = "₼"
        case "TRY": sign = "₺"
        default: sign = currency
        }
        return String(grouped.reversed()) + " " + sign
    }
}
