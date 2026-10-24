import Foundation

/// Язык приложения. Строки берутся из <язык>.lproj/Localizable.strings (собираются из tools/i18n),
/// поэтому язык можно сменить прямо в приложении, без перезапуска.
enum L10n {
    struct Language {
        let code: String
        let nativeName: String
    }

    static let supported: [Language] = [
        Language(code: "ru", nativeName: "Русский"),
        Language(code: "en", nativeName: "English"),
        Language(code: "uz", nativeName: "Oʻzbekcha"),
        Language(code: "tg", nativeName: "Тоҷикӣ"),
        Language(code: "kk", nativeName: "Қазақша"),
        Language(code: "ky", nativeName: "Кыргызча"),
        Language(code: "az", nativeName: "Azərbaycanca"),
        Language(code: "tr", nativeName: "Türkçe"),
    ]

    private(set) static var code: String = systemLanguage()
    private(set) static var bundle: Bundle = loadBundle(code)
    private static let fallback: Bundle = loadBundle("en")

    static var locale: Locale { Locale(identifier: code) }

    /// "" — язык телефона (если он поддерживается), иначе английский.
    static func apply(_ chosen: String) {
        code = chosen.isEmpty ? systemLanguage() : chosen
        bundle = loadBundle(code)
    }

    static func systemLanguage() -> String {
        let codes = Set(supported.map(\.code))
        // Сначала язык, выбранный для приложения в настройках iPhone, потом языки системы.
        for identifier in Bundle.main.preferredLocalizations + Locale.preferredLanguages {
            let lang = String(identifier.split(separator: "-").first ?? "")
            if codes.contains(lang) { return lang }
        }
        return "en"
    }

    private static func loadBundle(_ code: String) -> Bundle {
        if let path = Bundle.main.path(forResource: code, ofType: "lproj"), let b = Bundle(path: path) {
            return b
        }
        return Bundle.main
    }

    static func string(_ key: String) -> String {
        let missing = "\u{0}"
        let value = bundle.localizedString(forKey: key, value: missing, table: nil)
        if value != missing { return value }
        return fallback.localizedString(forKey: key, value: key, table: nil)
    }
}

/// Строка интерфейса по ключу (те же ключи, что в Android-версии).
func L(_ key: String) -> String { L10n.string(key) }

/// Строка с подстановками: L("home_in_time", "3 ч")
func L(_ key: String, _ args: CVarArg...) -> String {
    String(format: L10n.string(key), locale: L10n.locale, arguments: args)
}

/// Множественное число: Lp("days", 5) → «5 дней».
func Lp(_ key: String, _ n: Int) -> String {
    let category: String
    switch L10n.code {
    case "ru":
        let m10 = n % 10, m100 = n % 100
        if m10 == 1 && m100 != 11 {
            category = "one"
        } else if (2...4).contains(m10) && !(12...14).contains(m100) {
            category = "few"
        } else {
            category = "many"
        }
    default:
        category = n == 1 ? "one" : "other"
    }
    return String(format: L10n.string("\(key)_\(category)"), locale: L10n.locale, n)
}

/// Массив строк: La("hijri_months", 12).
func La(_ key: String, _ count: Int) -> [String] {
    (0..<count).map { L10n.string("\(key)_\($0)") }
}
