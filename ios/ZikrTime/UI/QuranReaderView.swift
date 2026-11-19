import SwiftUI
import UIKit

private actor ReaderLibraryCache {
    static let shared = ReaderLibraryCache()
    private var book: QuranLibrary?
    func load() throws -> QuranLibrary {
        if let book { return book }
        let loaded = try QuranLibrary()
        book = loaded
        return loaded
    }
}

private struct QuranPlace: Codable, Hashable, Identifiable {
    let chapter: Int
    let ayah: Int
    var id: String { "\(chapter):\(ayah)" }
}

@MainActor
private final class QuranMarks: ObservableObject {
    static let shared = QuranMarks()
    @Published var last: QuranPlace? { didSet { save(last, "last") } }
    @Published var bookmarks: Set<QuranPlace> { didSet { save(bookmarks, "bookmarks") } }
    @Published var notes: [String: String] { didSet { save(notes, "notes") } }

    private init() {
        last = Self.read("last")
        bookmarks = Self.read("bookmarks") ?? []
        notes = Self.read("notes") ?? [:]
    }
    private static func read<T: Decodable>(_ key: String) -> T? {
        guard let data = UserDefaults.standard.data(forKey: "quran." + key) else { return nil }
        return try? JSONDecoder().decode(T.self, from: data)
    }
    private func save<T: Encodable>(_ value: T, _ key: String) {
        if let data = try? JSONEncoder().encode(value) { UserDefaults.standard.set(data, forKey: "quran." + key) }
    }
    func toggle(_ place: QuranPlace) {
        if !bookmarks.insert(place).inserted { bookmarks.remove(place) }
    }
}

struct QuranLibraryView: View {
    var listening = false
    var initialChapter: Int? = nil
    var initialAyah = 1
    @ObservedObject private var marks = QuranMarks.shared
    @State private var library: QuranLibrary?
    @State private var failed = false
    @State private var query = ""
    @State private var filter = 0

    var body: some View {
        Group {
            if let library, let id = initialChapter, let chapter = library.surahs.first(where: { $0.id == id }) {
                reader(library, chapter, min(max(1, initialAyah), chapter.ayahCount))
            } else if let library {
                List {
                    if query.isEmpty, filter == 0, let last = marks.last,
                       let chapter = library.surahs.first(where: { $0.id == last.chapter }) {
                        Section {
                            NavigationLink {
                                reader(library, chapter, last.ayah)
                            } label: {
                                Label(L("quran_continue") + " · \(last.id)", systemImage: "book")
                            }
                        }
                    }
                    Section {
                        if query.isEmpty {
                            NavigationLink {
                                QuranReadingStatsView()
                            } label: { Label(L("qf_statistics"), systemImage: "chart.bar") }
                            NavigationLink {
                                QuranTopicsView(library: library)
                            } label: { Label(L("qti_title"), systemImage: "books.vertical") }
                        }
                        Picker("", selection: $filter) {
                            Text(L("quran_surahs")).tag(0)
                            Text(L("quran_bookmarks")).tag(1)
                            Text(L("qf_notes")).tag(2)
                        }.pickerStyle(.segmented)
                        if filter == 0 {
                            let chapters = library.surahs.filter { chapterMatches($0) }
                            if chapters.isEmpty { Text(L("quran_no_results")).foregroundStyle(.secondary) }
                            ForEach(chapters, id: \.id) { chapter in
                                NavigationLink {
                                    reader(library, chapter, 1)
                                } label: {
                                    HStack(spacing: 12) {
                                        Text("\(chapter.id)").font(.callout.monospacedDigit()).foregroundStyle(Brand.primary).frame(minWidth: 30)
                                        VStack(alignment: .leading) {
                                            Text(title(chapter)).font(.headline)
                                            Text(L("quran_ayah_count", chapter.ayahCount)).font(.caption).foregroundStyle(.secondary)
                                        }
                                        Spacer()
                                        Text(chapter.nameArabic).font(Brand.arabic(22))
                                    }.padding(.vertical, 4)
                                }.accessibilityIdentifier("quran_surah_\(chapter.id)")
                            }
                        } else {
                            let saved = savedPlaces(library)
                            if saved.isEmpty { Text(L(filter == 1 ? "quran_no_bookmarks" : "qf_no_notes")).foregroundStyle(.secondary) }
                            ForEach(saved) { place in
                                if let chapter = library.surahs.first(where: { $0.id == place.chapter }) {
                                    NavigationLink {
                                        reader(library, chapter, place.ayah)
                                    } label: {
                                        VStack(alignment: .leading) {
                                            Text(title(chapter) + " · " + place.id)
                                            if let note = marks.notes[place.id] {
                                                Text(note).font(.footnote).foregroundStyle(.secondary).lineLimit(2)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                .searchable(text: $query, prompt: Text(L("quran_search")))
                .scrollContentBackground(.hidden)
                .background(Brand.background)
            } else if failed {
                ContentUnavailableView {
                    Label(L("quran_title"), systemImage: "book.closed")
                } description: { Text(L("quran_load_error")) } actions: {
                    Button(L("quran_retry")) { Task { await load() } }
                }
            } else { ProgressView() }
        }
        .navigationTitle(initialChapter == nil ? L(listening ? "qaudio_title" : "quran_title") : "")
        .task { if library == nil && !failed { await load() } }
    }

    private func load() async {
        failed = false
        do { library = try await ReaderLibraryCache.shared.load() } catch { failed = true }
    }
    private func title(_ chapter: QuranChapter) -> String { L10n.code == "ru" ? chapter.nameRu : chapter.nameLatin }
    private func chapterMatches(_ chapter: QuranChapter) -> Bool {
        let term = query.trimmingCharacters(in: .whitespacesAndNewlines)
        return term.isEmpty || String(chapter.id) == term || [chapter.nameRu, chapter.nameLatin, chapter.nameArabic].contains { $0.localizedCaseInsensitiveContains(term) }
    }
    private func savedPlaces(_ library: QuranLibrary) -> [QuranPlace] {
        let places = filter == 1 ? Array(marks.bookmarks) : marks.notes.keys.compactMap { key -> QuranPlace? in
            let ids = key.split(separator: ":").compactMap { Int($0) }
            return ids.count == 2 ? QuranPlace(chapter: ids[0], ayah: ids[1]) : nil
        }
        return places.filter { place in library.surahs.contains { $0.id == place.chapter && (chapterMatches($0) || place.id == query) } }
            .sorted { ($0.chapter, $0.ayah) < ($1.chapter, $1.ayah) }
    }
    private func reader(_ library: QuranLibrary, _ chapter: QuranChapter, _ start: Int) -> some View {
        QuranChapterReader(chapter: chapter, verses: library.verses(chapter.id), start: start, autoPlay: listening)
    }
}

private struct QuranVerseBlock: Identifiable {
    let id: Int
    let verses: [QuranVerse]
}

private struct QuranChapterReader: View {
    let chapter: QuranChapter
    let verses: [QuranVerse]
    let start: Int
    let autoPlay: Bool
    @ObservedObject private var player = QuranPlayer.shared
    @ObservedObject private var marks = QuranMarks.shared
    @Environment(\.colorScheme) private var scheme
    @Environment(\.scenePhase) private var scenePhase
    @ScaledMetric(relativeTo: .title) private var arabicSize: CGFloat = 32
    @AppStorage("quran.translationMode") private var withTranslation = false
    @AppStorage("quran.readerScale") private var scale = 1.0
    @State private var opened = false
    @State private var options = false
    @State private var jump = false
    @State private var jumpText = ""
    @State private var jumpTarget: Int?
    @State private var actions: QuranPlace?
    @State private var note: QuranPlace?
    @State private var noteText = ""
    @State private var sources = false
    @State private var anchored = 1
    @State private var statsSession = QuranReadingSession()
    private let clock = Timer.publish(every: 1, on: .main, in: .common).autoconnect()

    private var currentChapter: Bool { player.chapter == chapter.id }
    private var current: Int { currentChapter ? max(1, player.ayah) : start }
    private var name: String { L10n.code == "ru" ? chapter.nameRu : chapter.nameLatin }
    private var background: Color { Color(hex: scheme == .dark ? 0x13181E : 0xF5EFE2) }
    private var paper: Color { Color(hex: scheme == .dark ? 0x1B242D : 0xFFFAED) }
    private var blocks: [QuranVerseBlock] {
        let anchor = min(max(anchored - 1, 0), verses.count)
        let spans = [0..<anchor, anchor..<verses.count]
        return spans.flatMap { span -> [QuranVerseBlock] in
            stride(from: span.lowerBound, to: span.upperBound, by: 8).map { i in
                QuranVerseBlock(id: verses[i].id, verses: Array(verses[i..<min(i + 8, span.upperBound)]))
            }
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 8) {
                modeButton("qp_reading_mode", false, "quran_mode_read")
                modeButton("qp_translation_mode", true, "quran_mode_translation")
            }.padding(.horizontal).padding(.vertical, 8)
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(spacing: 0) {
                        VStack(spacing: 3) {
                            Text(chapter.nameArabic).font(Brand.arabic(38))
                            Text(name).font(.title3.weight(.semibold))
                            Text(L("quran_ayah_count", chapter.ayahCount)).font(.caption).foregroundStyle(.secondary)
                        }.padding(.vertical, 16)
                        if withTranslation {
                            ForEach(verses, id: \.id) { verse in translationRow(verse).id(verse.id) }
                        } else {
                            ForEach(blocks) { block in
                                VStack(alignment: .trailing, spacing: 4) {
                                    savedIndicators(block.verses)
                                    arabic(block.verses)
                                }.padding(.horizontal, 14).padding(.bottom, 6).id(block.id)
                            }
                        }
                    }
                    .padding(.vertical, 6)
                    .background(paper, in: RoundedRectangle(cornerRadius: 7))
                    .overlay(RoundedRectangle(cornerRadius: 7).strokeBorder(Brand.gold.opacity(0.6)))
                    .padding(.horizontal, 10)
                }
                .onAppear {
                    guard !opened else { return }
                    opened = true
                    let first = currentChapter && player.playing && start == 1 ? current : start
                    if !currentChapter || !player.playing || start > 1 {
                        if autoPlay || !player.playing {
                            player.select(chapter: chapter.id, ayah: first, fromBeginning: first == 1)
                        }
                    }
                    if autoPlay && !player.playing { player.toggle() }
                    anchored = first
                    marks.last = QuranPlace(chapter: chapter.id, ayah: first)
                    if first > 1 { DispatchQueue.main.async { proxy.scrollTo(first, anchor: .top) } }
                }
                .onChange(of: player.ayah) { _, target in
                    guard currentChapter, player.playing, target > 0 else { return }
                    marks.last = QuranPlace(chapter: chapter.id, ayah: target)
                    if QuranArabicText.Coordinator.visibleText(chapter: chapter.id)[target] != nil { return }
                    let id = withTranslation ? target : (blocks.first { $0.verses.contains { $0.id == target } }?.id ?? target)
                    proxy.scrollTo(id, anchor: .top)
                }
                .onChange(of: jumpTarget) { _, target in
                    guard let target else { return }
                    anchored = target
                    DispatchQueue.main.async { proxy.scrollTo(target, anchor: .top); jumpTarget = nil }
                }
                .onChange(of: withTranslation) { _, _ in
                    anchored = current
                    DispatchQueue.main.async { proxy.scrollTo(current, anchor: .top) }
                }
            }
        }
        .background(background)
        .onReceive(clock) { _ in
            guard scenePhase == .active, !options, !sources, !jump, actions == nil, note == nil else {
                statsSession.pause(); return
            }
            if let first = statsSession.sample(chapter: chapter.id, verses: verses), !(currentChapter && player.playing) {
                let place = QuranPlace(chapter: chapter.id, ayah: first)
                if marks.last != place { marks.last = place }
            }
        }
        .onDisappear { statsSession.pause(); QuranReadingStats.shared.save() }
        .onChange(of: scenePhase) { _, phase in
            if phase != .active { statsSession.pause(); QuranReadingStats.shared.save() }
        }
        .safeAreaInset(edge: .bottom, spacing: 0) { playerBar }
        .navigationTitle(name)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar { ToolbarItem(placement: .topBarTrailing) { readerMenu } }
        .sheet(isPresented: $options) { audioOptions }
        .sheet(item: $note) { value in noteEditor(value) }
        .sheet(isPresented: $sources) {
            List {
                Text(L("quran_source_arabic"))
                Link("Tanzil", destination: URL(string: "https://tanzil.net/")!)
                Text(L("quran_source_translation"))
                Link("QuranEnc", destination: URL(string: "https://quranenc.com/")!)
                Text(L("qaudio_source"))
                Link("MP3Quran", destination: URL(string: "https://www.mp3quran.net/")!)
            }.presentationDetents([.medium]).presentationDragIndicator(.visible)
        }
        .alert(L("qf_jump"), isPresented: $jump) {
            TextField("1–\(chapter.ayahCount)", text: $jumpText).keyboardType(.numberPad)
            Button(L("quran_go")) {
                guard let n = Int(jumpText), (1...chapter.ayahCount).contains(n) else { return }
                tapped(n, nil)
                jumpTarget = n
            }.disabled(Int(jumpText).map { !(1...chapter.ayahCount).contains($0) } ?? true)
            Button(L("qaudio_cancel"), role: .cancel) {}
        }
        .confirmationDialog(actions?.id ?? "", isPresented: Binding(get: { actions != nil }, set: { if !$0 { actions = nil } }), titleVisibility: .visible, presenting: actions) { place in
            Button(L(marks.bookmarks.contains(place) ? "qt_remove_bookmark" : "quran_save")) { marks.toggle(place) }
            Button(L("qt_note")) { noteText = marks.notes[place.id] ?? ""; note = place }
            if let verse = verses.first(where: { $0.id == place.ayah }) {
                Button(L("qp_copy_arabic")) { UIPasteboard.general.string = verse.arabic }
                Button(L("qp_copy_translation")) { UIPasteboard.general.string = verse.translation }
            }
            Button(L("qaudio_cancel"), role: .cancel) {}
        }
    }

    private func modeButton(_ key: String, _ translation: Bool, _ tag: String) -> some View {
        Button { withTranslation = translation } label: {
            Text(L(key)).font(.subheadline.weight(.semibold)).frame(maxWidth: .infinity, minHeight: 36)
                .background(withTranslation == translation ? Brand.gold.opacity(0.32) : .clear, in: Capsule())
                .overlay(Capsule().stroke(Brand.gold.opacity(0.65)))
        }.buttonStyle(.plain).accessibilityIdentifier(tag)
    }
    private func tapped(_ ayah: Int, _ word: Int?) {
        player.select(chapter: chapter.id, ayah: ayah, word: word)
        marks.last = QuranPlace(chapter: chapter.id, ayah: ayah)
    }
    private func arabic(_ lines: [QuranVerse]) -> some View {
        let active = currentChapter ? player.ayah : nil
        let selected = lines.first { $0.id == active }
        let words: Set<Int> = selected.map { player.highlightWords(arabic: $0.arabic) } ?? []
        return QuranArabicText(chapter: chapter.id, verses: lines, activeAyah: active, highlightedWords: words,
                               followWord: player.playing && selected != nil ? player.word : nil,
                               fontSize: arabicSize * scale, select: tapped,
                               showActions: { actions = .init(chapter: chapter.id, ayah: $0) })
    }
    private func translationRow(_ verse: QuranVerse) -> some View {
        let place = QuranPlace(chapter: chapter.id, ayah: verse.id)
        return VStack(alignment: .leading, spacing: 10) {
            HStack {
                Button(place.id + "  ···") { actions = place }
                Spacer()
                Button { marks.toggle(place) } label: { Image(systemName: marks.bookmarks.contains(place) ? "bookmark.fill" : "bookmark") }
                    .accessibilityLabel(L("quran_bookmarks"))
            }.foregroundStyle(Brand.primary)
            arabic([verse])
            Text(L("quran_translation_label")).font(.caption.weight(.semibold)).foregroundStyle(Brand.primary)
            Text(verse.translation).lineSpacing(4).accessibilityIdentifier("quran_translation_\(verse.id)")
                .onTapGesture { tapped(verse.id, nil) }
            if !verse.footnotes.isEmpty {
                DisclosureGroup(L("quran_notes")) { Text(verse.footnotes).font(.footnote) }
            }
            if let text = marks.notes[place.id] {
                Label(text, systemImage: "note.text").font(.footnote).foregroundStyle(.secondary)
            }
            Divider()
        }.padding(.horizontal, 16).padding(.vertical, 8)
    }
    @ViewBuilder private func savedIndicators(_ verses: [QuranVerse]) -> some View {
        let saved = verses.map { QuranPlace(chapter: chapter.id, ayah: $0.id) }.filter { marks.bookmarks.contains($0) || marks.notes[$0.id] != nil }
        if !saved.isEmpty {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack {
                    ForEach(saved) { place in
                        Button { actions = place } label: {
                            Label(place.id, systemImage: marks.notes[place.id] == nil ? "bookmark.fill" : "note.text")
                        }.font(.caption)
                    }
                }
            }
        }
    }
    private var playerBar: some View {
        VStack(spacing: 4) {
            if let message = player.errorMessage, currentChapter { Text(message).font(.footnote).foregroundStyle(.secondary) }
            HStack(spacing: 14) {
                Button {
                    if !currentChapter { player.select(chapter: chapter.id, ayah: current); if !player.playing { player.toggle() } }
                    else { player.toggle() }
                } label: {
                    Image(systemName: currentChapter && player.playing ? "pause.fill" : "play.fill")
                        .frame(width: 42, height: 42).foregroundStyle(Brand.onGold).background(Brand.gold, in: Circle())
                }.accessibilityIdentifier("quran_inline_play_pause").accessibilityLabel(L(currentChapter && player.playing ? "qaudio_pause" : "qaudio_listen"))
                VStack(alignment: .leading) {
                    Text(L("quran_ayah") + " \(chapter.id):\(current)").font(.headline).accessibilityIdentifier("quran_location")
                    Text(L10n.code == "ru" ? player.reciter.nameRu : player.reciter.nameEN).font(.caption).lineLimit(1)
                }
                Spacer()
                Button { options = true } label: { Image(systemName: "slider.horizontal.3").frame(width: 44, height: 44) }
                    .accessibilityIdentifier("quran_inline_options").accessibilityLabel(L("qr_audio_options"))
            }
        }.padding(.horizontal, 16).padding(.vertical, 6).background(.regularMaterial)
    }
    private var readerMenu: some View {
        Menu {
            Button(L("qf_jump"), systemImage: "arrow.down.to.line") { jumpText = ""; jump = true }
            Button(L("qt_note"), systemImage: "square.and.pencil") { actions = .init(chapter: chapter.id, ayah: current) }
            Button(L("quran_save"), systemImage: "bookmark") { marks.toggle(.init(chapter: chapter.id, ayah: current)) }
            Button(L("qr_audio_options"), systemImage: "slider.horizontal.3") { options = true }
            Button(L("quran_sources"), systemImage: "info.circle") { sources = true }
        } label: { Image(systemName: "ellipsis.circle") }.accessibilityLabel(L("qf_tools"))
    }
    private var audioOptions: some View {
        NavigationStack {
            Form {
                Picker(L("qaudio_select_voice"), selection: Binding(get: { player.reciter }, set: { player.setVoice($0) })) {
                    ForEach(QuranVoice.allCases, id: \.self) { voice in Text(L10n.code == "ru" ? voice.nameRu : voice.nameEN).tag(voice) }
                }.accessibilityIdentifier("quran_options_reciter")
                Picker(L("ql_speed"), selection: Binding(get: { player.rate }, set: { player.setRate($0) })) {
                    ForEach([Float(0.5), 0.75, 1, 1.25, 1.5, 2], id: \.self) { speed in Text("\(speed.formatted())×").tag(speed) }
                }.accessibilityIdentifier("quran_options_speed")
                Picker(L("ql_repeat"), selection: Binding(get: { player.repeats }, set: { player.setRepeats($0) })) {
                    ForEach([1, 3, 5, 10, 0], id: \.self) { value in Text(value == 0 ? "∞" : "\(value)×").tag(value) }
                }.accessibilityIdentifier("quran_options_repeat")
                Toggle(L("ql_continue"), isOn: Binding(get: { player.continueAfter && player.repeats != 0 }, set: { player.setContinue($0) }))
                    .disabled(player.repeats == 0)
                if player.downloading.contains(chapter.id) {
                    Label(L("qaudio_downloading"), systemImage: "arrow.down.circle")
                } else if player.isDownloaded(chapter.id) {
                    Button(L("qaudio_delete"), role: .destructive) { player.removeDownload(chapter.id) }
                } else { Button(L("qaudio_download")) { Task { await player.download(chapter.id) } } }
                if let message = player.errorMessage { Text(message).font(.footnote) }
                Slider(value: $scale, in: 0.85...1.5, step: 0.05) {
                    Text(L("reader_text_size"))
                } minimumValueLabel: { Text("A").font(.caption) } maximumValueLabel: { Text("A").font(.title2) }
            }
            .navigationTitle(L("qr_audio_options")).navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button(L("action_done")) { options = false } } }
        }.presentationDetents([.medium, .large]).presentationDragIndicator(.visible)
    }
    private func noteEditor(_ place: QuranPlace) -> some View {
        NavigationStack {
            Form { TextField(L("qt_note"), text: $noteText, axis: .vertical).lineLimit(4...12) }
                .navigationTitle("\(L("qt_note")) · \(place.id)")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) { Button(L("qaudio_cancel")) { note = nil } }
                    ToolbarItem(placement: .confirmationAction) {
                        Button(L("qt_save_note")) {
                            let value = noteText.trimmingCharacters(in: .whitespacesAndNewlines)
                            marks.notes[place.id] = value.isEmpty ? nil : value
                            note = nil
                        }
                    }
                }
        }.presentationDetents([.medium, .large])
    }
}
