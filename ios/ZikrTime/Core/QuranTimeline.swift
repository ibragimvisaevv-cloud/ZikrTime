import Foundation

enum QuranVoice: String, CaseIterable, Identifiable {
    case alafasy = "mishary-alafasy"
    case dosari = "yasser-dosari"
    case husary = "mahmoud-husary"
    case minshawi = "muhammad-minshawi"
    var id: String { rawValue }
    var nameRu: String {
        switch self {
        case .alafasy: return "Мишари Рашид Аль-Афаси"
        case .dosari: return "Ясир ад-Досари"
        case .husary: return "Махмуд Халиль Аль-Хусари"
        case .minshawi: return "Мухаммад Сиддик Аль-Миншави"
        }
    }
    var nameEN: String {
        switch self {
        case .alafasy: return "Mishary Rashid Alafasy"
        case .dosari: return "Yasser Al-Dosari"
        case .husary: return "Mahmoud Khalil Al-Husary"
        case .minshawi: return "Muhammad Siddiq Al-Minshawi"
        }
    }
}

struct QuranTimeline {
    struct VerseSpan { let ayah, start, end: Int }
    struct WordSpan { let ayah, word, start, end: Int; let recovered: Bool }
    struct Track: Decodable {
        let surah_id: Int
        let url: URL
        let size_bytes: Int
        let sha256: String?
    }
    private struct Catalog: Decodable { let tracks: [Track] }
    private struct Times: Decodable {
        struct Chapter: Decodable { let surah: Int; let ayahs: [[Int]] }
        let surahs: [Chapter]
    }
    let track: Track
    let verses: [VerseSpan]
    let words: [WordSpan]
    private let starts: [Int: [Int: Int]]
    private let measured: [Int: Set<Int>]

    init(root: URL, voice: QuranVoice, chapter: Int) throws {
        var dir = root.appendingPathComponent("quran_audio")
        if voice != .alafasy { dir.appendPathComponent(voice.rawValue) }
        let json = JSONDecoder()
        let catalogue = try json.decode(Catalog.self, from: Data(contentsOf: dir.appendingPathComponent(voice == .alafasy ? "alafasy.json" : "catalog.json")))
        let boundaries = try json.decode(Times.self, from: Data(contentsOf: dir.appendingPathComponent(voice == .alafasy ? "alafasy_ayah_timings.json" : "ayah_timings.json")))
        guard let track = catalogue.tracks.first(where: { $0.surah_id == chapter }),
              track.url.absoluteString == String(format: "https://cdn.mp3quran.net/audio/%@/r1/%03d.mp3", voice.rawValue, chapter),
              let times = boundaries.surahs.first(where: { $0.surah == chapter }) else { throw QuranResourceError.invalid }
        self.track = track
        verses = try times.ayahs.map { row in
            guard row.count == 3, row[0] >= 0, row[1] >= 0, row[1] < row[2] else { throw QuranResourceError.invalid }
            return VerseSpan(ayah: row[0], start: row[1], end: row[2])
        }.sorted { $0.start < $1.start }
        func load(_ folder: String, optional: Bool) throws -> [WordSpan] {
            let file = dir.appendingPathComponent("\(folder)/\(chapter).bin")
            if optional && !FileManager.default.fileExists(atPath: file.path) { return [] }
            let data = [UInt8](try Data(contentsOf: file))
            let isStart = folder == "word_starts"
            let stride = isStart ? 8 : 12
            func u16(_ i: Int) -> Int { Int(data[i]) << 8 | Int(data[i+1]) }
            func u32(_ i: Int) -> Int { u16(i) << 16 | u16(i+2) }
            guard data.count >= 12, String(bytes: data.prefix(4), encoding: .ascii) == (isStart ? "ZQS1" : "ZQW1"),
                  u32(4) == chapter, data.count == 12 + u32(8) * stride else { throw QuranResourceError.invalid }
            return (0..<u32(8)).map { idx in
                let off = 12 + idx * stride
                return WordSpan(ayah: u16(off), word: u16(off+2), start: u32(off+4),
                                end: isStart ? u32(off+4) : u32(off+8), recovered: folder == "word_intervals")
            }
        }
        let base = try load("words", optional: true)
        let extra = try load("word_intervals", optional: true)
        words = (base + extra).sorted { $0.start < $1.start }
        var seekPoints = [Int: [Int: Int]]()
        for row in base where seekPoints[row.ayah]?[row.word] == nil {
            seekPoints[row.ayah, default: [:]][row.word] = row.start
        }
        for row in try load("word_starts", optional: true) {
            seekPoints[row.ayah, default: [:]][row.word] = row.start
        }
        starts = seekPoints
        measured = Dictionary(grouping: words, by: \.ayah).mapValues { Set($0.map(\.word)) }
    }

    func start(ayah: Int, word: Int?, fromBeginning: Bool = false) -> Int? {
        guard let verse = verses.first(where: { $0.ayah == ayah }) else { return nil }
        guard let word else { return fromBeginning && ayah == 1 ? 0 : verse.start }
        // Tanzil includes the four opening words in the text of the first ayah.
        // The recording times them separately under ayah zero.
        if ayah == 1, (1...4).contains(word), let opening = verses.first(where: { $0.ayah == 0 }),
           starts[1]?[word] == nil { return opening.start }
        let candidates = starts[ayah]?.filter { $0.key <= word && (verse.start..<verse.end).contains($0.value) }
        return candidates?.max(by: { $0.key < $1.key })?.value ?? verse.start
    }

    private func nextIndex<T>(_ values: [T], ms: Int, end: KeyPath<T, Int>) -> Int {
        var low = 0, high = values.count
        while low < high {
            let mid = (low + high) / 2
            if values[mid][keyPath: end] <= ms { low = mid + 1 } else { high = mid }
        }
        return low
    }
    func verse(at ms: Int) -> VerseSpan? {
        let i = nextIndex(verses, ms: ms, end: \.end)
        return verses.indices.contains(i) && verses[i].start <= ms ? verses[i] : nil
    }
    func cursor(at ms: Int) -> (word: WordSpan, gapNeighbor: Int?)? {
        let i = nextIndex(words, ms: ms, end: \.end)
        guard words.indices.contains(i) else { return nil }
        let next = words[i]
        if ms >= next.start { return (next, nil) }
        guard i > 0 else { return nil }
        let previous = words[i-1]
        guard previous.ayah == next.ayah, next.start - previous.end <= 300,
              !next.recovered, !previous.recovered else { return nil }
        return (next, previous.word)
    }

    private static let pattern = try! NSRegularExpression(pattern: #"\S+"#)
    static func tokens(_ arabic: String) -> [(index: Int, range: NSRange, spoken: Bool)] {
        let source = arabic as NSString
        return pattern.matches(in: arabic, range: NSRange(location: 0, length: source.length)).enumerated().map { i, match in
            let token = source.substring(with: match.range).decomposedStringWithCompatibilityMapping
            let spoken = token.unicodeScalars.contains {
                CharacterSet.letters.contains($0) && $0.value != 0x640 && !(0x6D6...0x6ED).contains($0.value)
            }
            return (i + 1, match.range, spoken)
        }
    }
    func pair(arabic: String, ayah: Int, word: Int, neighbor: Int?, selected: Bool) -> Set<Int> {
        let tokens = Self.tokens(arabic).filter(\.spoken)
        guard let index = tokens.firstIndex(where: { $0.index == word }) else { return [] }
        let start = index - (index % 2)
        let allowed = tokens[start..<min(start + 2, tokens.count)].map(\.index).filter {
            measured[ayah]?.contains($0) == true || (selected && starts[ayah]?[$0] != nil)
        }
        if selected && !allowed.contains(word) { return [word] }
        guard allowed.contains(word), neighbor.map({ $0 != word && allowed.contains($0) }) ?? true else { return [] }
        return Set(allowed)
    }
}
