import Foundation

enum QuranResourceError: Error { case missing, invalid }

struct QuranChapter: Identifiable, Decodable {
    let id: Int
    let nameRu: String
    let nameLatin: String
    let nameArabic: String
    let ayahCount: Int
}

struct QuranVerse: Identifiable {
    let id: Int
    let arabic: String
    let translation: String
    let footnotes: String
}

struct QuranLibrary {
    let surahs: [QuranChapter]
    private let chapters: [Int: [QuranVerse]]

    static func resourceRoot() throws -> URL {
        guard let url = Bundle.main.url(forResource: "QuranData", withExtension: nil) else {
            throw QuranResourceError.missing
        }
        return url
    }

    private struct Manifest: Decodable { let surahs: [QuranChapter] }
    private struct Translation: Decodable {
        let sura: Int, aya: Int
        let translation: String
        let footnotes: String?

        enum CodingKeys: String, CodingKey { case sura, aya, translation, footnotes }
        init(from decoder: Decoder) throws {
            let fields = try decoder.container(keyedBy: CodingKeys.self)
            func integer(_ key: CodingKeys) throws -> Int {
                if let number = try? fields.decode(Int.self, forKey: key) { return number }
                guard let number = Int(try fields.decode(String.self, forKey: key)) else { throw QuranResourceError.invalid }
                return number
            }
            sura = try integer(.sura)
            aya = try integer(.aya)
            translation = try fields.decode(String.self, forKey: .translation)
            footnotes = try fields.decodeIfPresent(String.self, forKey: .footnotes)
        }
    }
    private struct TranslationFile: Decodable { let result: [Translation] }

    init() throws { try self.init(root: Self.resourceRoot()) }
    init(root: URL) throws {
        let dir = root.appendingPathComponent("quran")
        let json = JSONDecoder()
        let manifest = try json.decode(Manifest.self, from: Data(contentsOf: dir.appendingPathComponent("manifest.json")))
        let source = try String(contentsOf: dir.appendingPathComponent("sources/quran-uthmani.txt"), encoding: .utf8)
        var arabic = [Int: [Int: String]]()
        for line in source.split(whereSeparator: \.isNewline) where !line.hasPrefix("#") {
            let fields = line.split(separator: "|", maxSplits: 2)
            guard fields.count == 3, let surah = Int(fields[0]), let ayah = Int(fields[1]),
                  arabic[surah]?[ayah] == nil else { throw QuranResourceError.invalid }
            arabic[surah, default: [:]][ayah] = String(fields[2])
        }
        var verses = [Int: [QuranVerse]]()
        guard manifest.surahs.map(\.id) == Array(1...114) else { throw QuranResourceError.invalid }
        for chapter in manifest.surahs {
            let path = "sources/russian_rwwad/\(chapter.id).json"
            let translated = try json.decode(TranslationFile.self, from: Data(contentsOf: dir.appendingPathComponent(path)))
            let rows = translated.result.sorted { $0.aya < $1.aya }
            guard rows.count == chapter.ayahCount else { throw QuranResourceError.invalid }
            verses[chapter.id] = try rows.enumerated().map { index, row in
                guard row.sura == chapter.id, row.aya == index + 1, let original = arabic[row.sura]?[row.aya],
                      !row.translation.isEmpty else { throw QuranResourceError.invalid }
                return QuranVerse(id: row.aya, arabic: original, translation: row.translation, footnotes: row.footnotes ?? "")
            }
        }
        surahs = manifest.surahs
        chapters = verses
    }
    func verses(_ id: Int) -> [QuranVerse] { chapters[id] ?? [] }
}
