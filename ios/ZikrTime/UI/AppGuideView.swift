import SwiftUI

struct AppGuideView: View {
    var onFinish: (() -> Void)? = nil
    @Environment(\.dismiss) private var dismiss
    @State private var page = 0
    private let topics = ["prayer", "quran", "tools", "shop"]
    private let symbols = ["clock", "book", "location.north.circle", "bag"]

    var body: some View {
        VStack(spacing: 18) {
            TabView(selection: $page) {
                ForEach(topics.indices, id: \.self) { index in
                    let topic = topics[index]
                    ScrollView {
                        VStack(alignment: .leading, spacing: 22) {
                            Image(systemName: symbols[index]).font(.largeTitle)
                                .foregroundStyle(Brand.gold).accessibilityHidden(true)
                            Text(L("guide_\(topic)_title")).font(.title.bold())
                            ForEach(1...3, id: \.self) { step in
                                HStack(alignment: .top) {
                                    Text("\(step)").bold().foregroundStyle(Brand.primary).frame(width: 24)
                                    Text(L("guide_\(topic)_step\(step)")).frame(maxWidth: .infinity, alignment: .leading)
                                }
                            }
                        }.padding(24)
                    }.tag(index)
                }
            }.tabViewStyle(.page(indexDisplayMode: .never))
            Text("\(page + 1) / \(topics.count)").font(.caption)
            PrimaryButton(title: L(page < topics.count - 1 ? "guide_next" : "guide_finish")) {
                if page < topics.count - 1 { withAnimation { page += 1 } } else { finish() }
            }
            if onFinish != nil { Button(L("guide_skip")) { finish() } }
        }
        .padding()
        .navigationTitle(L("guide_title"))
        .background(Brand.background)
    }

    private func finish() {
        if let onFinish { onFinish() } else { dismiss() }
    }
}
