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
    @State private var category: String?
    @State private var query = ""

    var body: some View {
        let c = shop.catalog
        let lang = L10n.code
        let q = query.trimmingCharacters(in: .whitespaces)
        let products = c.rankedProducts(seed: shop.orderSeed).filter { p in
            (category == nil || p.category == category) &&
                (q.isEmpty || p.title.get(lang).localizedCaseInsensitiveContains(q) ||
                    (c.seller(p.seller)?.name.localizedCaseInsensitiveContains(q) ?? false))
        }
        let sellers = c.rankedSellers(seed: shop.orderSeed).filter { category == nil || $0.categories.contains(category!) }

        Page {
            VStack(alignment: .leading, spacing: 2) {
                Text(L("shop_title")).font(.largeTitle.weight(.bold))
                Text(L("shop_subtitle")).font(.subheadline).foregroundStyle(Brand.secondaryText)
            }
            if c.demo {
                Card(padding: 16, background: Brand.goldSoft) {
                    Text(L("shop_demo_note")).font(.subheadline)
                }
            }
            if shop.offline {
                Text(L("shop_offline")).font(.caption).foregroundStyle(Brand.secondaryText)
            }

            HStack(spacing: 10) {
                Image(systemName: "magnifyingglass").foregroundStyle(Brand.primary)
                TextField(L("shop_search"), text: $query)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .glass(cornerRadius: 20, shadow: 6)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    CategoryPill(title: L("shop_all"), symbol: "square.grid.2x2.fill", selected: category == nil) { category = nil }
                    ForEach(c.categories) { cat in
                        CategoryPill(title: cat.name.get(lang), symbol: shopSymbol(cat.icon), selected: category == cat.id) {
                            category = category == cat.id ? nil : cat.id
                        }
                    }
                }
                .padding(.vertical, 8)
                .padding(.horizontal, 2)
            }

            if !c.sellerBot.isEmpty && q.isEmpty {
                Button { openURL("https://t.me/\(c.sellerBot)?start=add") } label: {
                    Card(padding: 14) {
                        HStack(spacing: 14) {
                            IconBadge(systemName: "storefront")
                            VStack(alignment: .leading, spacing: 2) {
                                Text(L("shop_add_company")).font(.subheadline.weight(.semibold)).foregroundStyle(.primary)
                                Text(L("shop_add_company_hint")).font(.caption).foregroundStyle(Brand.secondaryText)
                            }
                            Spacer()
                            Image(systemName: "paperplane.fill").foregroundStyle(Brand.primary)
                        }
                    }
                }
                .buttonStyle(PressScaleStyle(scale: 0.98))
            }

            if !sellers.isEmpty && q.isEmpty {
                SectionLabel(text: L("shop_sellers"))
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 12) {
                        ForEach(sellers) { s in
                            NavigationLink(value: Route.seller(s.id)) { SellerCard(seller: s, catalog: c) }
                                .buttonStyle(PressScaleStyle())
                        }
                    }
                    .padding(.vertical, 12)
                    .padding(.horizontal, 2)
                }
            }

            SectionLabel(text: L("shop_products"))
            if products.isEmpty {
                Text(L("shop_empty")).font(.subheadline).foregroundStyle(Brand.secondaryText)
            }
            ProductGrid(products: products, catalog: c)

            GlassHero {
                VStack(alignment: .leading, spacing: 8) {
                    Text(L("shop_become_seller")).font(.title2.weight(.semibold)).foregroundStyle(Brand.cream)
                    Text(L("shop_become_seller_text")).font(.subheadline).foregroundStyle(Brand.cream.opacity(0.85))
                    if !c.sellerBot.isEmpty {
                        GoldButton(title: L("shop_add_company")) { openURL("https://t.me/\(c.sellerBot)?start=add") }
                            .padding(.top, 8)
                    } else if !c.contactTelegram.isEmpty {
                        GoldButton(title: L("shop_contact_telegram")) { openURL("https://t.me/" + c.contactTelegram) }
                            .padding(.top, 8)
                    }
                }
                .padding(22)
            }
        }
        .toolbar(.hidden, for: .navigationBar)
        .task { await shop.refresh() }
    }
}

private struct CategoryPill: View {
    let title: String
    let symbol: String
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                Image(systemName: symbol).font(.footnote.weight(.semibold)).foregroundStyle(selected ? Brand.gold : Brand.primary)
                Text(title).font(.subheadline.weight(.semibold)).foregroundStyle(selected ? .white : .primary).lineLimit(1)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .background {
                if selected {
                    Capsule().fill(LinearGradient(colors: [Color(hex: 0x1A7D63), Brand.emeraldDeep], startPoint: .topLeading, endPoint: .bottomTrailing))
                }
            }
            .modifier(PillGlass(enabled: !selected))
        }
        .buttonStyle(PressScaleStyle())
    }
}

private struct PillGlass: ViewModifier {
    let enabled: Bool
    func body(content: Content) -> some View {
        if enabled { content.glass(cornerRadius: 22, shadow: 4) } else { content.clipShape(Capsule()) }
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
            ZStack(alignment: .topLeading) {
                SellerCover(seller: seller, catalog: catalog).frame(height: 110)
                if seller.partner { CategoryChip(text: L("shop_partner")).padding(10) }
            }
            HStack(spacing: 12) {
                SellerLogo(seller: seller, size: 44)
                VStack(alignment: .leading, spacing: 2) {
                    Text(seller.name).font(.subheadline.weight(.semibold)).foregroundStyle(.primary).lineLimit(1)
                    Text(seller.city.get(L10n.code)).font(.caption).foregroundStyle(Brand.secondaryText).lineLimit(1)
                }
                Spacer(minLength: 0)
            }
            .padding(14)
        }
        .frame(width: 250)
        .clipShape(RoundedRectangle(cornerRadius: 26, style: .continuous))
        .glass(cornerRadius: 26)
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
                    Text("−\(100 - price * 100 / old)%")
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
                let products = c.products.filter { $0.seller == s.id }
                if !products.isEmpty {
                    SectionLabel(text: L("shop_seller_products"))
                    ProductGrid(products: products, catalog: c)
                }
            }
        }
        .navigationTitle(c.seller(id)?.name ?? "")
        .navigationBarTitleDisplayMode(.inline)
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
