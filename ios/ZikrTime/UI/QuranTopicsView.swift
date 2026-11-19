import SwiftUI

/// Passage references are shared with Android build 56. Always read verse text from the Quran library.
private struct TopicPassage: Identifiable {
    let chapter: Int, first: Int, last: Int
    init(_ chapter: Int, _ first: Int, _ last: Int? = nil) {
        self.chapter = chapter
        self.first = first
        self.last = last ?? first
    }
    var id: String { "\(chapter):\(first)" + (first == last ? "" : "–\(last)") }
}

private struct QuranTopicEntry: Identifiable {
    let id: String, passages: [TopicPassage]
    var title: String { L("qti_" + id) }
    var details: String { L("qti_" + id + "_description") }
    static let all: [Self] = [
        .init(id: "patience", passages: [.init(2,153,157), .init(3,200), .init(16,126,128), .init(103,1,3)]),
        .init(id: "gratitude", passages: [.init(2,152), .init(14,7,8), .init(16,78), .init(31,12)]),
        .init(id: "parents", passages: [.init(17,23,24), .init(31,14,15), .init(46,15), .init(29,8)]),
        .init(id: "mercy", passages: [.init(7,156,157), .init(21,107), .init(39,53,54), .init(90,12,17)]),
        .init(id: "repentance", passages: [.init(4,17,18), .init(25,68,71), .init(39,53,54), .init(66,8)]),
        .init(id: "honesty", passages: [.init(9,119), .init(33,70,71), .init(22,30,31), .init(83,1,6)]),
        .init(id: "justice", passages: [.init(4,58), .init(4,135), .init(5,8), .init(16,90)]),
        .init(id: "charity", passages: [.init(2,261,264), .init(2,267), .init(2,271), .init(76,8,10)]),
        .init(id: "knowledge", passages: [.init(20,114), .init(39,9), .init(58,11), .init(96,1,5)]),
        .init(id: "remembrance", passages: [.init(13,27,28), .init(33,41,42), .init(2,152), .init(7,205,206)]),
        .init(id: "trust", passages: [.init(3,159,160), .init(14,11,12), .init(65,1,3), .init(11,123)]),
        .init(id: "supplication", passages: [.init(2,186), .init(2,201), .init(3,8,9), .init(14,40,41)]),
        .init(id: "forgiveness", passages: [.init(3,133,136), .init(24,22), .init(41,34,35), .init(42,40,43)]),
        .init(id: "humility", passages: [.init(25,63,66), .init(31,18,19), .init(17,37,38), .init(49,11,12)]),
        .init(id: "family", passages: [.init(4,36,38), .init(17,26,29), .init(30,21), .init(49,10)]),
        .init(id: "reflection", passages: [.init(3,190,194), .init(30,20,25), .init(67,3,4), .init(88,17,20)])
    ]
}

struct QuranTopicsView: View {
    let library: QuranLibrary
    @State private var term = ""
    private var matching: [QuranTopicEntry] {
        let q = term.trimmingCharacters(in: .whitespacesAndNewlines)
        let ref = q.split(whereSeparator: { $0 == ":" || $0 == "/" }).compactMap { Int($0) }
        return QuranTopicEntry.all.filter { topic in
            if ref.count == 2 {
                return topic.passages.contains { $0.chapter == ref[0] && ($0.first...$0.last).contains(ref[1]) }
            }
            let names = topic.passages.compactMap { passage in library.surahs.first { $0.id == passage.chapter } }
                .map { [$0.nameRu, $0.nameArabic, $0.nameLatin].joined(separator: " ") }
            return q.isEmpty || ([topic.title, topic.details] + names).contains { $0.localizedCaseInsensitiveContains(q) }
        }
    }
    var body: some View {
        List {
            ForEach(matching) { topic in
                NavigationLink {
                    List {
                        Section {
                            ForEach(topic.passages) { passage in
                                NavigationLink {
                                    QuranLibraryView(initialChapter: passage.chapter, initialAyah: passage.first)
                                } label: {
                                    VStack(alignment: .leading, spacing: 4) {
                                        Text(passage.id).font(.headline)
                                        if let first = library.verses(passage.chapter).first(where: { $0.id == passage.first }) {
                                            Text(first.translation).font(.footnote).foregroundStyle(.secondary).lineLimit(3)
                                        }
                                    }
                                }
                            }
                        } header: { Text(topic.details) }
                    }.navigationTitle(topic.title)
                } label: { Text(topic.title) }
            }
            if matching.isEmpty { Text(L("qti_no_results")).foregroundStyle(.secondary) }
        }
        .navigationTitle(L("qti_title"))
        .searchable(text: $term, prompt: Text(L("qti_search")))
    }
}
