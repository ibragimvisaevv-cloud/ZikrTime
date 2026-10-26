import SwiftUI

struct ReviewsView: View {
    let target: String
    @ObservedObject private var document = Community.reviews
    @State private var writing = false
    @State private var sent = false
    @State private var showAll = false

    var body: some View {
        Card {
            Text(L("reviews_title")).font(.headline)
            if let summary = document.value?.reviews[target], summary.n > 0 {
                Label(String(format: "%.1f", summary.avg) + " · \(summary.n)", systemImage: "star.fill")
                    .foregroundStyle(Brand.primary).padding(.vertical, 10)
                let items = showAll ? summary.items : Array(summary.items.prefix(3))
                ForEach(Array(items.enumerated()), id: \.offset) { _, item in
                    VStack(alignment: .leading, spacing: 6) {
                        Text(String(repeating: "★", count: max(0, min(5, item.r)))).foregroundStyle(Brand.gold)
                        if !item.t.isEmpty { Text(item.t).font(.subheadline) }
                        HStack {
                            Text(item.n)
                            Spacer()
                            Text(Date(timeIntervalSince1970: item.d), style: .date)
                        }.font(.caption).foregroundStyle(Brand.secondaryText)
                    }.padding(.vertical, 10)
                    Divider()
                }
                if !showAll && summary.items.count > 3 {
                    Button(L("reviews_show_all", summary.items.count)) { showAll = true }.padding(.top, 8)
                }
            } else {
                Text(L(document.failed ? "reviews_offline" : "reviews_none")).font(.subheadline)
                    .foregroundStyle(Brand.secondaryText).padding(.vertical, 12)
            }
            if sent || Community.alreadyReviewed(target) {
                Text(L("reviews_sent_already")).font(.footnote).foregroundStyle(Brand.primary).padding(.top, 8)
            } else {
                Button(L("reviews_write")) { writing = true }.padding(.top, 8)
            }
        }
        .task { await document.refresh() }
        .sheet(isPresented: $writing) {
            WriteReviewView(target: target) { sent = true }
        }
    }
}

private struct WriteReviewView: View {
    let target: String
    let onSent: () -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var rating = 5
    @State private var name = ""
    @State private var text = ""
    @State private var sending = false
    @State private var error = false

    var body: some View {
        NavigationStack {
            Form {
                Section(L("reviews_rate")) {
                    HStack {
                        ForEach(1...5, id: \.self) { value in
                            Button { rating = value } label: {
                                Image(systemName: value <= rating ? "star.fill" : "star")
                                    .font(.title2).foregroundStyle(Brand.gold)
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel("\(value) / 5")
                            .accessibilityAddTraits(rating == value ? [.isSelected] : [])
                            Spacer()
                        }
                    }
                }
                Section {
                    TextField(L("reviews_name_hint"), text: $name)
                    TextField(L("reviews_text_hint"), text: $text, axis: .vertical).lineLimit(3...8)
                    Text(L("reviews_rules")).font(.caption).foregroundStyle(Brand.secondaryText)
                }
                if error { Text(L("reviews_error")).foregroundStyle(.red) }
                Button(L(sending ? "zakat_loading" : "reviews_send")) {
                    sending = true
                    Task {
                        do {
                            try await Community.sendReview(target: target, rating: rating, text: text, name: name)
                            onSent()
                            dismiss()
                        } catch { self.error = true }
                        sending = false
                    }
                }.disabled(sending)
            }
            .navigationTitle(L("reviews_dialog_title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button { dismiss() } label: { Image(systemName: "xmark") }.disabled(sending)
                }
            }
        }
        .interactiveDismissDisabled(sending)
        .onChange(of: text) { _, value in text = String(value.prefix(600)) }
        .onChange(of: name) { _, value in name = String(value.prefix(40)) }
    }
}
