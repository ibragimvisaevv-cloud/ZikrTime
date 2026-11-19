import SwiftUI

private struct DailyDhikr: Identifiable, Decodable {
    let id, arabic, titleRu, titleEn, meaningRu, meaningEn, sourceLabel: String
    let sourceUrl: URL
    let repetitions: Int
}

struct DailyAzkarView: View {
    let evening: Bool
    @State private var index = 0
    @AppStorage("dailyAzkar.v1") private var record = Data()
    private static let items: [String: [DailyDhikr]] = {
        guard let url = Bundle.main.url(forResource: "daily-azkar", withExtension: "json"),
              let bytes = try? Data(contentsOf: url) else { return [:] }
        return (try? JSONDecoder().decode([String: [DailyDhikr]].self, from: bytes)) ?? [:]
    }()

    var body: some View {
        let period = evening ? "evening" : "morning"
        let items = Self.items[period] ?? []
        let ru = L10n.code == "ru"
        Page {
            if items.indices.contains(index) {
                let entry = items[index]
                let date = LocalDay.today().key
                let key = "\(date).\(period).\(entry.id)"
                let counts = (try? JSONDecoder().decode([String: Int].self, from: record)) ?? [:]
                let progress = min(counts[key, default: 0], entry.repetitions)
                Text("\(index + 1) / \(items.count)").font(.caption).foregroundStyle(Brand.secondaryText)
                Card {
                    Text(ru ? entry.titleRu : entry.titleEn).font(.headline)
                    Text(entry.arabic).font(Brand.arabic(28)).frame(maxWidth: .infinity, alignment: .trailing).padding(.vertical)
                        .environment(\.layoutDirection, .rightToLeft)
                    Text(ru ? entry.meaningRu : entry.meaningEn)
                    Link(entry.sourceLabel, destination: entry.sourceUrl).font(.footnote).padding(.top, 12)
                }
                PrimaryButton(title: progress == entry.repetitions ? L("azkar_read_done") : L("azkar_mark_read") + " · \(progress)/\(entry.repetitions)") {
                    var fresh = counts.filter { $0.key.hasPrefix(date + ".") }
                    fresh[key] = min(progress + 1, entry.repetitions)
                    if let data = try? JSONEncoder().encode(fresh) { record = data }
                }.disabled(progress == entry.repetitions)
                HStack {
                    Button(L("azkar_previous")) { index -= 1 }.disabled(index == 0)
                    Spacer()
                    Button(L("azkar_next")) { index += 1 }.disabled(index + 1 == items.count)
                }.buttonStyle(.bordered)
            }
        }
        .navigationTitle(L("azkar_" + period))
        .navigationBarTitleDisplayMode(.inline)
    }
}
