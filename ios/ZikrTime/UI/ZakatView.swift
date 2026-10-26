import SwiftUI

struct ZakatView: View {
    @State private var values = UserDefaults.standard.dictionary(forKey: "zakat_inputs") as? [String: String] ?? [:]
    @AppStorage("zakat_by_silver") private var silver = false
    @State private var loading = false
    @State private var priceError = false
    private let assets = ["cash", "bank", "goods", "owed", "gold_g", "silver_g", "debts"]
    private let prices = ["gold_price", "silver_price"]

    var body: some View {
        let result = Zakat.calculate(values, silver: silver)
        Page {
            GlassHero {
                VStack(alignment: .leading, spacing: 10) {
                    Text(L("zakat_due")).font(.subheadline)
                    Text(money(result.due) + " ₽").font(.system(size: 40, weight: .light)).foregroundStyle(Brand.gold)
                    Text(result.nisab <= 0 ? L("zakat_need_prices") : L(result.total >= result.nisab ? "zakat_above" : "zakat_below", money(result.total), money(result.nisab)))
                        .font(.subheadline)
                }.foregroundStyle(Brand.cream).padding(22)
            }
            Card {
                Text(L("zakat_assets")).font(.headline)
                fields(assets)
            }
            Card {
                Text(L("zakat_nisab")).font(.headline)
                Picker(L("zakat_nisab"), selection: $silver) {
                    Text(L("zakat_by_gold")).tag(false)
                    Text(L("zakat_by_silver")).tag(true)
                }.pickerStyle(.menu)
                fields(prices)
                Text(L("zakat_prices_cbr")).font(.caption).foregroundStyle(Brand.secondaryText).padding(.top, 12)
                Button(L(loading ? "zakat_loading" : "zakat_update_prices")) { Task { await loadPrices() } }
                    .disabled(loading).padding(.top, 8)
                if priceError { Text(L("shop_offline")).font(.caption).foregroundStyle(Brand.secondaryText) }
            }
            Text(L("zakat_note")).font(.footnote).foregroundStyle(Brand.secondaryText)
        }
        .navigationTitle(L("zakat_title"))
        .navigationBarTitleDisplayMode(.inline)
        .onChange(of: values) { _, value in UserDefaults.standard.set(value, forKey: "zakat_inputs") }
        .task { if Zakat.number(values["gold_price"] ?? "") == 0 { await loadPrices() } }
        .toolbar {
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                Button { UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil) } label: {
                    Image(systemName: "keyboard.chevron.compact.down")
                }
            }
        }
    }

    private func fields(_ keys: [String]) -> some View {
        ForEach(keys, id: \.self) { key in
            VStack(alignment: .leading, spacing: 6) {
                Text(L("zakat_" + key)).font(.caption).foregroundStyle(Brand.secondaryText)
                TextField("0", text: Binding(get: { values[key] ?? "" }, set: { value in
                    values[key] = String(value.filter { $0.isNumber || $0 == "," || $0 == "." }.prefix(15))
                }))
                .keyboardType(.decimalPad)
                .textFieldStyle(.roundedBorder)
                .accessibilityLabel(L("zakat_" + key))
            }.padding(.top, 12)
        }
    }

    private func money(_ value: Double) -> String {
        let formatter = NumberFormatter()
        formatter.numberStyle = .decimal
        formatter.maximumFractionDigits = 0
        formatter.locale = Locale(identifier: L10n.code)
        return formatter.string(from: NSNumber(value: value)) ?? "0"
    }

    @MainActor private func loadPrices() async {
        guard !loading, !QA.active else { return }
        loading = true
        defer { loading = false }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "dd/MM/yyyy"
        let now = Date()
        let from = Calendar.current.date(byAdding: .day, value: -10, to: now)!
        var components = URLComponents(string: "https://www.cbr.ru/scripts/xml_metall.asp")!
        components.queryItems = [.init(name: "date_req1", value: formatter.string(from: from)), .init(name: "date_req2", value: formatter.string(from: now))]
        var request = URLRequest(url: components.url!)
        request.timeoutInterval = 15
        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            guard (response as? HTTPURLResponse)?.statusCode == 200, let parsed = Zakat.prices(data) else { throw URLError(.cannotParseResponse) }
            values["gold_price"] = String(format: "%.2f", locale: Locale(identifier: "en_US_POSIX"), parsed.gold)
            values["silver_price"] = String(format: "%.2f", locale: Locale(identifier: "en_US_POSIX"), parsed.silver)
            priceError = false
        } catch { priceError = true }
    }
}
