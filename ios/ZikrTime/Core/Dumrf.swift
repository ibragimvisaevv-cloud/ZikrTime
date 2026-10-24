import Foundation

/// Разбор расписания Московской Соборной мечети с главной страницы dumrf.ru
/// (блок «Время намазов на сегодня … (Москва)»). Та же логика, что в Android-версии.
enum DumrfParser {
    static let moscowZone = TimeZone(identifier: "Europe/Moscow")!

    /// Москва и ближнее Подмосковье (до 60 км от центра).
    static func isMoscow(latitude: Double, longitude: Double) -> Bool {
        let dLat = (latitude - 55.7558) * 111.0
        let dLon = (longitude - 37.6173) * 111.0 * cos(55.7558 * .pi / 180)
        return (dLat * dLat + dLon * dLon).squareRoot() < 60.0
    }

    private static let names: [(Prayer, String)] = [
        (.fajr, "Фаджр"), (.sunrise, "(?:Шурук|Восход)"), (.dhuhr, "Зухр"),
        (.asr, "Аср"), (.maghrib, "Магриб"), (.isha, "Иша"),
    ]

    private static func replace(_ text: String, _ pattern: String, with template: String, options: NSRegularExpression.Options = []) -> String {
        guard let re = try? NSRegularExpression(pattern: pattern, options: options) else { return text }
        return re.stringByReplacingMatches(in: text, range: NSRange(text.startIndex..., in: text), withTemplate: template)
    }

    /// Шесть времён «час, минута» по порядку или nil, если страница не похожа на расписание.
    static func parse(_ html: String) -> [Prayer: (hour: Int, minute: Int)]? {
        var text = replace(html, "<(script|style)[^>]*>.*?</\\1>", with: " ", options: [.caseInsensitive, .dotMatchesLineSeparators])
        text = replace(text, "<[^>]+>", with: " ")
        text = text.replacingOccurrences(of: "&nbsp;", with: " ")
        text = replace(text, "\\s+", with: " ")
        guard let start = text.range(of: "Время намаз") else { return nil }
        let block = String(text[start.lowerBound...].prefix(1500))
        var result: [Prayer: (hour: Int, minute: Int)] = [:]
        for (prayer, name) in names {
            guard let re = try? NSRegularExpression(pattern: name + "\\s*:?\\s*(\\d{1,2}):(\\d{2})"),
                  let m = re.firstMatch(in: block, range: NSRange(block.startIndex..., in: block)),
                  let hr = Range(m.range(at: 1), in: block), let mr = Range(m.range(at: 2), in: block),
                  let h = Int(block[hr]), let mm = Int(block[mr]), h <= 23, mm <= 59 else { return nil }
            result[prayer] = (h, mm)
        }
        // Проверка здравого смысла: время идёт по порядку.
        let minutes = Prayer.allCases.map { result[$0]!.hour * 60 + result[$0]!.minute }
        guard zip(minutes, minutes.dropFirst()).allSatisfy({ $0 < $1 }) else { return nil }
        return result
    }
}
