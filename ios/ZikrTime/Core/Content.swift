import Foundation

/// Город (или своя точка по геолокации). id == nil — координаты из геолокации.
struct City: Codable, Equatable {
    var id: String?
    var name: String
    var latitude: Double
    var longitude: Double

    static let moscow = City(id: "moscow", name: "Moscow", latitude: 55.7558, longitude: 37.6173)
}

/// Список городов из общего файла cities.json (тот же, что в Android-версии).
struct CityCatalog: Decodable {
    struct Group: Decodable {
        let id: String
        let names: [String: String]
    }

    struct Entry: Decodable {
        let id: String
        let group: String
        let lat: Double
        let lon: Double
        let names: [String: String]

        func name(_ lang: String) -> String { names[lang] ?? names["en"] ?? id }
        func city(_ lang: String) -> City { City(id: id, name: name(lang), latitude: lat, longitude: lon) }
    }

    let groups: [Group]
    let cities: [Entry]

    static func parse(_ data: Data) throws -> CityCatalog {
        try JSONDecoder().decode(CityCatalog.self, from: data)
    }

    static let shared: CityCatalog = {
        guard let url = Bundle.main.url(forResource: "cities", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let catalog = try? parse(data) else {
            return CityCatalog(groups: [], cities: [])
        }
        return catalog
    }()

    /// Название города на нужном языке (для городов из списка).
    func displayName(_ city: City, lang: String) -> String {
        guard let id = city.id, let entry = cities.first(where: { $0.id == id }) else { return city.name }
        return entry.name(lang)
    }
}

/// Вопрос викторины. Правильный ответ в файле всегда первый — при показе варианты перемешиваются.
struct Question: Identifiable, Equatable {
    let id: String
    let category: String
    let text: String
    let arabic: String?
    let options: [String]
    let answerIndex: Int
    let info: String

    func shuffled() -> Question {
        let correct = options[answerIndex]
        let mixed = options.shuffled()
        return Question(id: id, category: category, text: text, arabic: arabic, options: mixed,
                        answerIndex: mixed.firstIndex(of: correct) ?? 0, info: info)
    }
}

struct QuizBank {
    struct Category: Identifiable {
        let id: String
        let name: String
    }

    let categories: [Category]
    let questions: [Question]

    func categoryName(_ id: String) -> String { categories.first { $0.id == id }?.name ?? id }

    private struct File: Decodable {
        struct Cat: Decodable {
            let id: String
            let name: String
        }

        struct Item: Decodable {
            let id: String
            let cat: String
            let q: String
            let options: [String]
            let answer: Int
            let info: String
            let arabic: String?
        }

        let categories: [Cat]
        let questions: [Item]
    }

    static func parse(_ data: Data) throws -> QuizBank {
        let file = try JSONDecoder().decode(File.self, from: data)
        return QuizBank(
            categories: file.categories.map { Category(id: $0.id, name: $0.name) },
            questions: file.questions.map {
                Question(id: $0.id, category: $0.cat, text: $0.q, arabic: $0.arabic, options: $0.options, answerIndex: $0.answer, info: $0.info)
            }
        )
    }

    private static var cache: [String: QuizBank] = [:]

    /// Вопросы на языке приложения; если файла нет — английские, затем русские.
    static func load(lang: String) -> QuizBank {
        if let cached = cache[lang] { return cached }
        for code in [lang, "en", "ru"] {
            if let url = Bundle.main.url(forResource: code, withExtension: "json", subdirectory: "quiz"),
               let data = try? Data(contentsOf: url),
               let bank = try? parse(data) {
                cache[lang] = bank
                return bank
            }
        }
        return QuizBank(categories: [], questions: [])
    }

    /// Порядок вопросов для раунда: сначала те, что давно (или никогда) не попадались.
    func roundOrder(category: String?, recent: [String]) -> [Question] {
        let pool = questions.filter { category == nil || $0.category == category }
        let recentSet = Set(recent)
        let fresh = pool.filter { !recentSet.contains($0.id) }.shuffled()
        let seen = pool.filter { recentSet.contains($0.id) }.sorted {
            (recent.firstIndex(of: $0.id) ?? 0) < (recent.firstIndex(of: $1.id) ?? 0)
        }
        return (fresh + seen).map { $0.shuffled() }
    }
}

extension String {
    /// Есть ли в строке арабские буквы (такие варианты показываем крупным арабским шрифтом).
    var hasArabic: Bool {
        unicodeScalars.contains { (0x0600...0x06FF).contains($0.value) || (0xFB50...0xFDF9).contains($0.value) || (0xFE70...0xFEFF).contains($0.value) }
    }
}
