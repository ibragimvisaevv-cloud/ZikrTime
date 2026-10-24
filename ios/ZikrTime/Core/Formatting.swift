import Foundation

/// Форматирование времени и дат на языке приложения.
enum Fmt {
    /// 24- или 12-часовой формат — как в настройках телефона.
    static var uses24h: Bool {
        let format = DateFormatter.dateFormat(fromTemplate: "j", options: 0, locale: Locale.current) ?? "HH"
        return !format.contains("a")
    }

    private static func formatter(_ format: String, zone: TimeZone = .current) -> DateFormatter {
        let f = DateFormatter()
        f.locale = L10n.locale
        f.timeZone = zone
        f.dateFormat = format
        return f
    }

    /// «05:29» или «5:29 AM».
    static func clock(_ date: Date) -> String {
        formatter(uses24h ? "HH:mm" : "h:mm a").string(from: date)
    }

    /// Только цифры («5:29») — для крупных часов, где AM/PM пишется отдельно.
    static func clockDigits(_ date: Date) -> String {
        formatter(uses24h ? "HH:mm" : "h:mm").string(from: date)
    }

    static func amPm(_ date: Date) -> String? {
        uses24h ? nil : formatter("a").string(from: date)
    }

    /// «Суббота, 24 октября»
    static func gregorian(_ day: LocalDay) -> String {
        let text = formatter("EEEE, d MMMM").string(from: day.start().addingTimeInterval(12 * 3600))
        return text.prefix(1).uppercased() + text.dropFirst()
    }

    /// «12 джумада аль-уля 1448 г. х.» (календарь Умм аль-Кура).
    static func hijri(_ day: LocalDay) -> String {
        var cal = Calendar(identifier: .islamicUmmAlQura)
        cal.timeZone = .current
        let c = cal.dateComponents([.year, .month, .day], from: day.start().addingTimeInterval(12 * 3600))
        guard let d = c.day, let m = c.month, let y = c.year, (1...12).contains(m) else { return "" }
        let months = La("hijri_months", 12)
        return L("hijri_date_format", d, months[m - 1], y)
    }

    /// «1 ч 15 мин», «45 мин», «2 ч».
    static func duration(minutes total: Int) -> String {
        let h = total / 60, m = total % 60
        if h == 0 { return L("duration_m", m) }
        if m == 0 { return L("duration_h", h) }
        return L("duration_h_m", h, m)
    }

    static func days(_ n: Int) -> String { Lp("days", n) }
}
