import Foundation

struct ShopFilters {
    var category = ""
    var city = ""
    var minimum = ""
    var maximum = ""

    func count(products: Bool) -> Int {
        [category, city, products ? minimum : "", products ? maximum : ""].filter { !$0.isEmpty }.count
    }

    var validPriceRange: Bool {
        guard minimum.isEmpty || Int(minimum) != nil, maximum.isEmpty || Int(maximum) != nil else { return false }
        if let min = Int(minimum), let max = Int(maximum) { return min <= max }
        return true
    }
}

struct ShopSearch {
    private struct Entry<Item> {
        let item: Item
        let city: String
        let text: String
    }

    private let sellers: [Entry<ShopCatalog.Seller>]
    private let products: [Entry<ShopCatalog.Product>]
    let cities: [String]
    let partners: [ShopCatalog.Seller]
    let otherCompanies: [ShopCatalog.Seller]

    init(catalog: ShopCatalog, seed: String, language: String) {
        let ranked = catalog.rankedSellers(seed: seed)
        let byID = Dictionary(ranked.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
        let categories = Dictionary(catalog.categories.map { ($0.id, $0.name.get(language)) }, uniquingKeysWith: { first, _ in first })
        let pinned = ["sfb443720", "s24a178b0"]
        partners = ranked.filter(\.partner).sorted {
            (pinned.firstIndex(of: $0.id) ?? Int.max) < (pinned.firstIndex(of: $1.id) ?? Int.max)
        }
        otherCompanies = ranked.filter { !$0.partner }
        var cityKeys = Set<String>()
        cities = ranked.map { $0.city.get(language).trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty && cityKeys.insert(Self.normalize($0)).inserted }
            .sorted { $0.localizedCaseInsensitiveCompare($1) == .orderedAscending }
        sellers = ranked.map { seller in
            let city = seller.city.get(language)
            let fields = [seller.name, seller.description.get(language), city] + seller.categories.compactMap { categories[$0] }
            return Entry(item: seller, city: Self.normalize(city), text: Self.normalize(fields.joined(separator: " ")))
        }
        products = catalog.rankedProducts(seed: seed).map { product in
            let seller = byID[product.seller]
            let city = seller?.city.get(language) ?? ""
            let fields = [product.title.get(language), product.description.get(language), seller?.name ?? "", city, categories[product.category] ?? ""]
            return Entry(item: product, city: Self.normalize(city), text: Self.normalize(fields.joined(separator: " ")))
        }
    }

    private static func normalize(_ text: String) -> String {
        text.lowercased().replacingOccurrences(of: "ё", with: "е")
            .split { !$0.isLetter && !$0.isNumber }.joined(separator: " ")
    }

    func companies(_ query: String, filters: ShopFilters) -> [ShopCatalog.Seller] {
        let terms = Self.normalize(query).split(separator: " ").map(String.init)
        let city = Self.normalize(filters.city)
        guard !terms.isEmpty || filters.count(products: false) > 0 else { return [] }
        return sellers.filter { entry in
            (filters.category.isEmpty || entry.item.categories.contains(filters.category))
                && (city.isEmpty || city == entry.city) && terms.allSatisfy { entry.text.contains($0) }
        }.map(\.item)
    }

    func items(_ query: String, filters: ShopFilters) -> [ShopCatalog.Product] {
        let terms = Self.normalize(query).split(separator: " ").map(String.init)
        let city = Self.normalize(filters.city)
        let min = Int(filters.minimum), max = Int(filters.maximum)
        guard !terms.isEmpty || filters.count(products: true) > 0, filters.validPriceRange else { return [] }
        return products.filter { entry in
            let p = entry.item
            if min != nil || max != nil {
                guard p.currency.uppercased() == "RUB", let price = p.price,
                      price >= (min ?? 0), price <= (max ?? Int.max) else { return false }
            }
            return (filters.category.isEmpty || p.category == filters.category)
                && (city.isEmpty || city == entry.city) && terms.allSatisfy { entry.text.contains($0) }
        }.map(\.item)
    }
}
