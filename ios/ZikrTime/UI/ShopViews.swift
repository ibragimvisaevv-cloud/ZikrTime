import SwiftUI
import UIKit

/// Значок категории магазина (системные символы).
func shopSymbol(_ icon: String) -> String {
    switch icon {
    case "perfume": return "drop.fill"
    case "car": return "car.fill"
    case "book": return "book.closed.fill"
    case "clothes": return "tshirt.fill"
    case "food": return "fork.knife"
    case "gift": return "gift.fill"
    default: return "bag.fill"
    }
}

private func accent(_ seller: ShopCatalog.Seller?) -> Color { seller?.accent.map { Color(hex: $0) } ?? Brand.emerald }

// MARK: - Витрина

struct ShopView: View {
    @EnvironmentObject private var shop: ShopStore
    @State private var showTerms = false
    @State private var refreshing = true

    var body: some View {
        let c = shop.catalog
        let search = shop.search(language: L10n.code)
        Page {
            Text(L("shop_title")).font(.largeTitle.bold())
            NavigationLink(value: Route.shopSearch) {
                Label(L("shop_search"), systemImage: "magnifyingglass")
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding().glass(cornerRadius: 18, shadow: 4)
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("shop_open_search")

            if c.demo { Text(L("shop_demo_note")).font(.caption) }
            if shop.offline && !c.sellers.isEmpty { Text(L("shop_offline")).font(.caption) }
            if !search.partners.isEmpty {
                SectionLabel(text: L("shop_partners_title"))
                SellerGrid(sellers: search.partners, catalog: c)
            }
            if !search.otherCompanies.isEmpty {
                SectionLabel(text: L("store_companies"))
                SellerGrid(sellers: search.otherCompanies, catalog: c)
            }
            if c.sellers.isEmpty {
                if refreshing { ProgressView() }
                else if shop.offline {
                    Text(L("store_unavailable"))
                    Button(L("store_retry")) { Task { await refresh(force: true) } }
                } else { Text(L("store_empty_catalog")) }
            }
            if !c.sellerBot.isEmpty || !c.contactTelegram.isEmpty || c.sellerPortal != nil {
                Button { showTerms = true } label: {
                    Label(L("shop_add_company"), systemImage: "storefront")
                        .frame(maxWidth: .infinity).padding().glass(cornerRadius: 18)
                }
                .buttonStyle(.plain)
            }
        }
        .toolbar(.hidden, for: .navigationBar)
        .task { await refresh(force: false) }
        .sheet(isPresented: $showTerms) {
            VStack(alignment: .leading, spacing: 18) {
                Text(L("store_terms_title")).font(.title2.bold())
                Text(L("store_listing_terms"))
                Text(L("store_promotion_terms")).font(.subheadline)
                if !c.sellerBot.isEmpty || !c.contactTelegram.isEmpty {
                    GoldButton(title: L("store_apply")) {
                        openURL("https://t.me/" + (c.sellerBot.isEmpty ? c.contactTelegram : "\(c.sellerBot)?start=add"))
                    }
                }
                if let portal = c.sellerPortal { Button(L("store_seller_portal")) { openURL(portal) } }
            }
            .padding(24).presentationDragIndicator(.visible).presentationDetents([.medium, .large])
        }
    }

    private func refresh(force: Bool) async {
        refreshing = true
        await shop.refresh(force: force)
        refreshing = false
    }
}

private struct SellerGrid: View {
    let sellers: [ShopCatalog.Seller]
    let catalog: ShopCatalog

    var body: some View {
        LazyVGrid(columns: [GridItem(.flexible(minimum: 0)), GridItem(.flexible(minimum: 0))], alignment: .leading, spacing: 12) {
            ForEach(sellers) { s in
                NavigationLink(value: Route.seller(s.id)) { SellerCard(seller: s, catalog: catalog) }
                    .buttonStyle(PressScaleStyle())
                    .accessibilityIdentifier("shop_seller_" + s.id)
            }
        }
    }
}

struct ShopSearchView: View {
    @EnvironmentObject private var shop: ShopStore
    @State private var query = ""
    @State private var products = true
    @State private var filters = ShopFilters()
    @State private var editingFilters = false
    @FocusState private var focused: Bool

    var body: some View {
        let index = shop.search(language: L10n.code)
        let count = filters.count(products: products)
        Page {
            HStack {
                Image(systemName: "magnifyingglass")
                TextField(L(products ? "store_search_products" : "store_search_companies"), text: $query)
                    .focused($focused).submitLabel(.search).onSubmit { focused = false }
                    .textInputAutocapitalization(.never).autocorrectionDisabled()
                if !query.isEmpty {
                    Button { query = "" } label: { Image(systemName: "xmark.circle.fill") }
                        .accessibilityLabel(L("store_clear"))
                }
            }.padding().glass(cornerRadius: 18)
            Picker(L("store_search_title"), selection: $products) {
                Text(L("shop_products")).tag(true)
                Text(L("store_companies")).tag(false)
            }.pickerStyle(.segmented)
            if query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && count == 0 {
                Text(L("store_search_prompt")).foregroundStyle(Brand.secondaryText)
            } else if products {
                let found = index.items(query, filters: filters)
                if found.isEmpty { emptyState }
                else { ProductGrid(products: found, catalog: shop.catalog) }
            } else {
                let found = index.companies(query, filters: filters)
                if found.isEmpty { emptyState }
                else { SellerGrid(sellers: found, catalog: shop.catalog) }
            }
        }
        .navigationTitle(L("store_search_title")).navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button(count == 0 ? L("store_filters") : "\(L("store_filters")) · \(count)") {
                    focused = false
                    editingFilters = true
                }
            }
        }
        .task { await shop.refresh() }
        .sheet(isPresented: $editingFilters) {
            ShopFilterForm(filters: filters, products: products, cities: index.cities,
                           categories: shop.catalog.categories) { filters = $0; editingFilters = false }
        }
    }

    private var emptyState: some View {
        VStack(spacing: 10) {
            Text(L("shop_empty"))
            Button(L("action_reset")) { query = ""; filters = ShopFilters() }
        }.frame(maxWidth: .infinity)
    }
}

private struct ShopFilterForm: View {
    @State private var draft: ShopFilters
    let products: Bool
    let cities: [String]
    let categories: [ShopCatalog.Category]
    let apply: (ShopFilters) -> Void

    init(filters: ShopFilters, products: Bool, cities: [String], categories: [ShopCatalog.Category],
         apply: @escaping (ShopFilters) -> Void) {
        _draft = State(initialValue: filters)
        self.products = products
        self.cities = cities
        self.categories = categories
        self.apply = apply
    }

    var body: some View {
        NavigationStack {
            Form {
                Picker(L("store_category"), selection: $draft.category) {
                    Text(L("shop_all")).tag("")
                    ForEach(categories) { c in Text(c.name.get(L10n.code)).tag(c.id) }
                }
                if !cities.isEmpty {
                    Picker(L("settings_city"), selection: $draft.city) {
                        Text(L("store_any_city")).tag("")
                        ForEach(cities, id: \.self) { city in Text(city).tag(city) }
                    }
                }
                if products {
                    TextField(L("store_price_from"), text: $draft.minimum).keyboardType(.numberPad)
                    TextField(L("store_price_to"), text: $draft.maximum).keyboardType(.numberPad)
                    if !draft.validPriceRange { Text(L("store_price_range_error")).foregroundStyle(.red) }
                }
                Button(L("store_apply_filters")) { apply(draft) }.disabled(products && !draft.validPriceRange)
                Button(L("action_reset")) { apply(ShopFilters()) }
            }.navigationTitle(L("store_filters")).navigationBarTitleDisplayMode(.inline)
        }.presentationDragIndicator(.visible)
    }
}

private struct SellerLogo: View {
    let seller: ShopCatalog.Seller
    var size: CGFloat

    var body: some View {
        ZStack {
            Circle().fill(LinearGradient(colors: [accent(seller), Brand.emeraldDeep], startPoint: .topLeading, endPoint: .bottomTrailing))
            if let url = ShopCatalog.imageURL(seller.logo) {
                AsyncImage(url: url) { image in image.resizable().scaledToFill() } placeholder: { Color.clear }
                    .clipShape(Circle())
            } else {
                Text(String(seller.name.prefix(1)).uppercased())
                    .font(.system(size: size * 0.42, weight: .bold))
                    .foregroundStyle(Brand.cream)
            }
        }
        .frame(width: size, height: size)
        .overlay(Circle().strokeBorder(Brand.gold.opacity(0.7), lineWidth: 2))
    }
}

/// Обложка продавца: картинка или градиент его цвета со звёздным узором.
private struct SellerCover: View {
    let seller: ShopCatalog.Seller
    let catalog: ShopCatalog

    var body: some View {
        ZStack {
            LinearGradient(colors: [accent(seller), accent(seller).opacity(0.75), Brand.emeraldDeep], startPoint: .topLeading, endPoint: .bottomTrailing)
            if let url = ShopCatalog.imageURL(seller.cover) {
                AsyncImage(url: url) { image in image.resizable().scaledToFill() } placeholder: { Color.clear }
            } else {
                StarPattern(color: Brand.gold.opacity(0.18), cell: 48)
                HStack {
                    Spacer()
                    Image(systemName: shopSymbol(catalog.category(seller.categories.first ?? "")?.icon ?? "store"))
                        .font(.system(size: 48))
                        .foregroundStyle(Brand.cream.opacity(0.35))
                        .padding(.trailing, 18)
                }
            }
        }
        .clipped()
    }
}

private struct SellerCard: View {
    let seller: ShopCatalog.Seller
    let catalog: ShopCatalog

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Rectangle().fill(Brand.emeraldDeep).frame(height: 64)
                .overlay { SellerCover(seller: seller, catalog: catalog) }.clipped()
                .overlay(alignment: .leading) { SellerLogo(seller: seller, size: 40).padding(.leading, 10) }
                .overlay(alignment: .topTrailing) {
                    if seller.promoted(at: Date()) { CategoryChip(text: L("shop_ad")).padding(4) }
                }
            VStack(alignment: .leading, spacing: 4) {
                Text(seller.name).font(.subheadline.weight(.semibold)).foregroundStyle(.primary)
                    .lineLimit(2, reservesSpace: true)
                let caption = seller.categories.first.flatMap { catalog.category($0)?.name.get(L10n.code) }
                Text(caption ?? seller.city.get(L10n.code)).font(.caption).foregroundStyle(Brand.secondaryText).lineLimit(1)
            }
            .frame(maxWidth: .infinity, alignment: .leading).padding(10)
        }
        .multilineTextAlignment(.leading)
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .glass(cornerRadius: 16)
    }
}

private struct ProductImage: View {
    let product: ShopCatalog.Product
    let catalog: ShopCatalog
    var iconSize: CGFloat = 44
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let a = accent(catalog.seller(product.seller))
        ZStack {
            LinearGradient(colors: scheme == .dark ? [a.opacity(0.55), Brand.emeraldDeep] : [a.opacity(0.16), Brand.gold.opacity(0.18)],
                           startPoint: .topLeading, endPoint: .bottomTrailing)
            if let path = product.images.first, let url = ShopCatalog.imageURL(path) {
                AsyncImage(url: url) { image in image.resizable().scaledToFill() } placeholder: { ProgressView() }
            } else {
                Image(systemName: shopSymbol(catalog.category(product.category)?.icon ?? "store"))
                    .font(.system(size: iconSize))
                    .foregroundStyle(scheme == .dark ? Brand.gold : a)
            }
        }
        .clipped()
    }
}

private struct PriceLine: View {
    let product: ShopCatalog.Product
    var big = false

    var body: some View {
        HStack(alignment: .lastTextBaseline, spacing: 6) {
            Text(product.price.map { ShopCatalog.price($0, currency: product.currency) } ?? L("shop_price_on_request"))
                .font(big ? .title2.weight(.semibold) : .headline)
                .foregroundStyle(Brand.primary)
            if !product.unit.isEmpty {
                Text("/ " + product.unit.get(L10n.code)).font(.caption).foregroundStyle(Brand.secondaryText)
            }
            if let old = product.oldPrice, let price = product.price, old > price {
                Text(ShopCatalog.price(old, currency: product.currency))
                    .font(.caption)
                    .strikethrough()
                    .foregroundStyle(Brand.secondaryText)
            }
        }
        .lineLimit(1)
        .minimumScaleFactor(0.7)
    }
}

private struct ProductGrid: View {
    let products: [ShopCatalog.Product]
    let catalog: ShopCatalog

    var body: some View {
        LazyVGrid(columns: [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)], spacing: 12) {
            ForEach(products) { p in
                NavigationLink(value: Route.product(p.id)) { ProductCard(product: p, catalog: catalog) }
                    .buttonStyle(PressScaleStyle())
            }
        }
    }
}

private struct ProductCard: View {
    let product: ShopCatalog.Product
    let catalog: ShopCatalog

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ZStack(alignment: .topTrailing) {
                ProductImage(product: product, catalog: catalog)
                    .aspectRatio(1, contentMode: .fit)
                if let old = product.oldPrice, let price = product.price, old > price {
                    Text("−\(100 - Int(Double(price) * 100 / Double(old)))%")
                        .font(.caption2.weight(.bold))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(Color.red, in: Capsule())
                        .padding(8)
                }
            }
            VStack(alignment: .leading, spacing: 4) {
                Text(product.title.get(L10n.code))
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(.primary)
                    .lineLimit(2, reservesSpace: true)
                    .multilineTextAlignment(.leading)
                PriceLine(product: product)
                Text(catalog.seller(product.seller)?.name ?? "").font(.caption).foregroundStyle(Brand.secondaryText).lineLimit(1)
                if catalog.seller(product.seller)?.promoted(at: Date()) == true {
                    Text(L("shop_ad")).font(.caption2).foregroundStyle(Brand.secondaryText)
                }
            }
            .padding(12)
        }
        .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
        .glass(cornerRadius: 24)
    }
}

// MARK: - Продавец

struct SellerView: View {
    let id: String
    @EnvironmentObject private var shop: ShopStore
    @State private var expandOffers = false
    @State private var expandDiscounts = false

    var body: some View {
        let c = shop.catalog
        Page {
            if let s = c.seller(id) {
                ZStack(alignment: .bottomLeading) {
                    SellerCover(seller: s, catalog: c)
                    Color.clear.sheen(opacity: 0.12, period: 5)
                    HStack(spacing: 14) {
                        SellerLogo(seller: s, size: 60)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(s.name).font(.title2.weight(.semibold)).foregroundStyle(Brand.cream)
                            Text(s.city.get(L10n.code)).font(.subheadline).foregroundStyle(Brand.cream.opacity(0.85))
                        }
                    }
                    .padding(16)
                }
                .frame(height: 160)
                .overlay(alignment: .topTrailing) {
                    if s.partner { CategoryChip(text: L("shop_partner")).padding(12) }
                }
                .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))

                if !s.description.isEmpty {
                    Card { Text(s.description.get(L10n.code)).font(.body) }
                }
                TimelineView(.periodic(from: .now, by: 60)) { clock in
                    let offers = s.offers.filter { $0.active(at: clock.date) }
                    if !offers.isEmpty {
                        VStack(alignment: .leading, spacing: 10) {
                            SectionLabel(text: L("store_offers"))
                            ForEach(expandOffers ? offers : Array(offers.prefix(2))) { offer in
                                Card {
                                    VStack(alignment: .leading, spacing: 5) {
                                        Text(offer.title).font(.headline)
                                        Text(offer.conditions)
                                        Text(L("store_offer_until", Self.dateLabel(offer.validUntil)))
                                            .font(.caption).foregroundStyle(Brand.secondaryText)
                                    }.frame(maxWidth: .infinity, alignment: .leading)
                                }
                            }
                            if offers.count > 2 && !expandOffers {
                                Button(L("store_more_offers")) { expandOffers = true }
                            }
                        }
                    }
                }
                let allProducts = c.products.filter { $0.seller == s.id }
                let discounts = allProducts.filter(\.discounted)
                if !discounts.isEmpty {
                    SectionLabel(text: L("store_discounts"))
                    ProductGrid(products: expandDiscounts ? discounts : Array(discounts.prefix(2)), catalog: c)
                    if discounts.count > 2 && !expandDiscounts {
                        Button(L("store_more_discounts")) { expandDiscounts = true }
                    }
                }
                ContactButtons(seller: s)
                ReviewsView(target: "seller:" + s.id)
                if !s.address.isEmpty {
                    Button { openMap(s) } label: {
                        Card {
                            HStack(spacing: 14) {
                                IconBadge(systemName: "mappin.and.ellipse")
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(L("shop_address")).font(.footnote.weight(.semibold)).foregroundStyle(Brand.primary)
                                    Text(s.address).font(.body).foregroundStyle(.primary).multilineTextAlignment(.leading)
                                }
                            }
                        }
                    }
                    .buttonStyle(PressScaleStyle(scale: 0.98))
                }
                let products = allProducts.filter { !$0.discounted }
                if !products.isEmpty {
                    SectionLabel(text: L("shop_seller_products"))
                    ProductGrid(products: products, catalog: c)
                }
            }
        }
        .navigationTitle(c.seller(id)?.name ?? "")
        .navigationBarTitleDisplayMode(.inline)
    }

    private static func dateLabel(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: L10n.code)
        formatter.timeZone = ShopCatalog.Offer.calendar.timeZone
        formatter.dateStyle = .medium
        return formatter.string(from: date)
    }
}

/// Кнопки связи — только те, что указал продавец.
private struct ContactButtons: View {
    let seller: ShopCatalog.Seller

    private struct Item: Identifiable {
        let id: String
        let symbol: String
        let action: () -> Void
    }

    var body: some View {
        let s = seller
        var items: [Item] = []
        if !s.whatsapp.isEmpty { items.append(Item(id: "shop_whatsapp", symbol: "message.fill") { openURL("https://wa.me/" + s.whatsapp.filter(\.isNumber)) }) }
        if !s.phone.isEmpty { items.append(Item(id: "shop_call", symbol: "phone.fill") { openURL("tel:" + s.phone.filter { $0.isNumber || $0 == "+" }) }) }
        if !s.instagram.isEmpty { items.append(Item(id: "shop_instagram", symbol: "camera.fill") { openURL("https://instagram.com/" + s.instagram) }) }
        if !s.address.isEmpty || s.lat != nil { items.append(Item(id: "shop_route", symbol: "location.fill") { openMap(s) }) }

        return VStack(spacing: 10) {
            if !s.telegram.isEmpty {
                ShimmerButton(title: L("shop_contact_telegram")) { openURL("https://t.me/" + s.telegram) }
            }
            LazyVGrid(columns: [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)], spacing: 10) {
                ForEach(items) { item in
                    Button(action: item.action) {
                        Label(L(item.id), systemImage: item.symbol)
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(Brand.primary)
                            .frame(maxWidth: .infinity, minHeight: 50)
                            .glass(cornerRadius: 18, shadow: 4)
                    }
                    .buttonStyle(PressScaleStyle())
                }
            }
            if s.telegram.isEmpty && items.isEmpty {
                Text(L("shop_contacts_soon")).font(.subheadline).foregroundStyle(Brand.secondaryText)
            }
        }
    }
}

// MARK: - Товар

struct ProductView: View {
    let id: String
    @EnvironmentObject private var shop: ShopStore
    @EnvironmentObject private var router: Router

    var body: some View {
        let c = shop.catalog
        Page {
            if let p = c.product(id) {
                ProductImage(product: p, catalog: c, iconSize: 90)
                    .frame(height: 300)
                    .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
                if p.images.count > 1 {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(p.images.dropFirst(), id: \.self) { img in
                                AsyncImage(url: ShopCatalog.imageURL(img)) { image in image.resizable().scaledToFill() } placeholder: { Brand.primarySoft }
                                    .frame(width: 96, height: 96)
                                    .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                            }
                        }
                    }
                }
                VStack(alignment: .leading, spacing: 6) {
                    Text(p.title.get(L10n.code)).font(.title2.weight(.semibold))
                    PriceLine(product: p, big: true)
                }
                if !p.description.isEmpty {
                    Card { Text(p.description.get(L10n.code)).font(.body) }
                }
                if let s = c.seller(p.seller) {
                    NavigationLink(value: Route.seller(s.id)) {
                        Card {
                            HStack(spacing: 14) {
                                SellerLogo(seller: s, size: 48)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(s.name).font(.headline).foregroundStyle(.primary)
                                    Text(s.city.get(L10n.code)).font(.caption).foregroundStyle(Brand.secondaryText)
                                }
                                Spacer()
                                if s.partner { CategoryChip(text: L("shop_partner")) }
                            }
                        }
                    }
                    .buttonStyle(PressScaleStyle(scale: 0.98))
                    ContactButtons(seller: s)
                }
            }
        }
        .navigationTitle(c.product(id)?.title.get(L10n.code) ?? "")
        .navigationBarTitleDisplayMode(.inline)
    }
}

// MARK: - Переходы

@MainActor
func openURL(_ string: String) {
    guard let url = URL(string: string) else { return }
    UIApplication.shared.open(url)
}

@MainActor
private func openMap(_ s: ShopCatalog.Seller) {
    let name = s.name.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
    if let lat = s.lat, let lon = s.lon {
        openURL("https://yandex.ru/maps/?rtext=~\(lat),\(lon)&rtt=pd")
    } else {
        openURL("https://yandex.ru/maps/?text=" + (s.address.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""))
    }
}
