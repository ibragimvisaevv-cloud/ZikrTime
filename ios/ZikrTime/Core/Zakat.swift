import Foundation
#if canImport(FoundationXML)
import FoundationXML
#endif

enum Zakat {
    static func number(_ text: String) -> Double {
        let value = Double(text.replacingOccurrences(of: " ", with: "").replacingOccurrences(of: ",", with: ".")) ?? 0
        return value.isFinite ? max(0, value) : 0
    }
    struct Result {
        let total: Double
        let nisab: Double
        let due: Double
    }
    static func calculate(_ values: [String: String], silver: Bool) -> Result {
        func n(_ key: String) -> Double { number(values[key] ?? "") }
        let total = max(0, n("cash") + n("bank") + n("goods") + n("owed") + n("gold_g") * n("gold_price") + n("silver_g") * n("silver_price") - n("debts"))
        let nisab = silver ? 595 * n("silver_price") : 85 * n("gold_price")
        return Result(total: total, nisab: nisab, due: nisab > 0 && total >= nisab ? total / 40 : 0)
    }

    struct Prices {
        let gold: Double
        let silver: Double
        let date: String
    }
    /// XMLParser respects the Windows-1251 declaration in the Bank of Russia response.
    static func prices(_ data: Data) -> Prices? {
        let delegate = MetalParser()
        let parser = XMLParser(data: data)
        parser.delegate = delegate
        guard parser.parse(), let gold = delegate.values["1"], let silver = delegate.values["2"] else { return nil }
        return Prices(gold: gold, silver: silver, date: delegate.date)
    }
    private final class MetalParser: NSObject, XMLParserDelegate {
        var values: [String: Double] = [:]
        var date = ""
        private var code = ""
        private var buy = false
        private var text = ""
        func parser(_ parser: XMLParser, didStartElement element: String, namespaceURI: String?, qualifiedName: String?, attributes: [String: String]) {
            if element == "Record" { code = attributes["Code"] ?? ""; date = attributes["Date"] ?? "" }
            if element == "Buy" { buy = true; text = "" }
        }
        func parser(_ parser: XMLParser, foundCharacters string: String) { if buy { text += string } }
        func parser(_ parser: XMLParser, didEndElement element: String, namespaceURI: String?, qualifiedName: String?) {
            if element == "Buy" {
                let value = Zakat.number(text.trimmingCharacters(in: .whitespacesAndNewlines))
                if value > 0 { values[code] = value }
                buy = false
            }
        }
    }
}
