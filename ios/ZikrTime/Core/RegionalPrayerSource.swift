import Foundation

/// Regional authorities and city identities match the Android 0.8.28 catalog.
enum RegionalFeed: String { case grozny, kazan, dagestan, bashkortostan, kazakhstan, kyrgyzstan, bundled }

struct RegionalPrayerSource {
    let city: String
    let zone: TimeZone
    let publisher: String
    let feed: RegionalFeed
    let parameter: String

    static let sources: [String: Self] = {
        let rows = """
grozny	Europe/Moscow	ДУМ ЧР · таблица через Assolat	grozny
kazan	Europe/Moscow	ДУМ Республики Татарстан	kazan
makhachkala	Europe/Moscow	Муфтият Республики Дагестан	dagestan	mahashkala
khasavyurt	Europe/Moscow	Муфтият Республики Дагестан	dagestan	hasavyurt
derbent	Europe/Moscow	Муфтият Республики Дагестан	dagestan	derbent
ufa	Asia/Yekaterinburg	ДУМ Республики Башкортостан	bashkortostan	Уфа
agidel	Asia/Yekaterinburg	ДУМ Республики Башкортостан	bashkortostan	Агидель
baymak	Asia/Yekaterinburg	ДУМ Республики Башкортостан	bashkortostan	Баймак
belebey	Asia/Yekaterinburg	ДУМ Республики Башкортостан	bashkortostan	Белебей
beloretsk	Asia/Yekaterinburg	ДУМ Республики Башкортостан	bashkortostan	Белорецк
birsk	Asia/Yekaterinburg	ДУМ Республики Башкортостан	bashkortostan	Бирск
blagoveshchensk_rb	Asia/Yekaterinburg	ДУМ Республики Башкортостан	bashkortostan	Благовещенск
davlekanovo	Asia/Yekaterinburg	ДУМ Республики Башкортостан	bashkortostan	Давлеканово
ishimbay	Asia/Yekaterinburg	ДУМ Республики Башкортостан	bashkortostan	Ишимбай
kumertau	Asia/Yekaterinburg	ДУМ Республики Башкортостан	bashkortostan	Кумертау
mezhgorye	Asia/Yekaterinburg	ДУМ Республики Башкортостан	bashkortostan	Межгорье
meleuz	Asia/Yekaterinburg	ДУМ Республики Башкортостан	bashkortostan	Мелеуз
neftekamsk	Asia/Yekaterinburg	ДУМ Республики Башкортостан	bashkortostan	Нефтекамск
oktyabrsky_rb	Asia/Yekaterinburg	ДУМ Республики Башкортостан	bashkortostan	Октябрьский
salavat	Asia/Yekaterinburg	ДУМ Республики Башкортостан	bashkortostan	Салават
sibay	Asia/Yekaterinburg	ДУМ Республики Башкортостан	bashkortostan	Сибай
sterlitamak	Asia/Yekaterinburg	ДУМ Республики Башкортостан	bashkortostan	Стерлитамак
tuymazy	Asia/Yekaterinburg	ДУМ Республики Башкортостан	bashkortostan	Туймазы
uchaly	Asia/Yekaterinburg	ДУМ Республики Башкортостан	bashkortostan	Учалы
yanaul	Asia/Yekaterinburg	ДУМ Республики Башкортостан	bashkortostan	Янаул
aktobe	Asia/Aqtobe	ДУМ Казахстана	kazakhstan	Ақтөбе қаласы|50.300377/57.154555
almaty	Asia/Almaty	ДУМ Казахстана	kazakhstan	Алматы қаласы|43.238293/76.945465
astana	Asia/Almaty	ДУМ Казахстана	kazakhstan	Астана қаласы|51.133333/71.433333
atyrau	Asia/Atyrau	ДУМ Казахстана	kazakhstan	Атырау қаласы|47.116667/51.883333
karaganda	Asia/Almaty	ДУМ Казахстана	kazakhstan	Қарағанды қаласы|49.806406/73.085485
shymkent	Asia/Almaty	ДУМ Казахстана	kazakhstan	Шымкент қаласы|42.368009/69.612769
taraz	Asia/Almaty	ДУМ Казахстана	kazakhstan	Тараз қаласы|42.883333/71.366667
turkistan	Asia/Almaty	ДУМ Казахстана	kazakhstan	Түркістан қаласы|43.302025/68.268979
bishkek	Asia/Bishkek	ДУМ Кыргызстана	kyrgyzstan	1
osh	Asia/Bishkek	ДУМ Кыргызстана	kyrgyzstan	2
karakol	Asia/Bishkek	ДУМ Кыргызстана	kyrgyzstan	7
saint_petersburg	Europe/Moscow	ЦДУМ России	bundled
astrakhan	Europe/Astrakhan	ЦДУМ России	bundled
kirov	Europe/Kirov	ЦДУМ России	bundled
yekaterinburg	Asia/Yekaterinburg	ЦДУМ России	bundled
izhevsk	Europe/Samara	ЦДУМ России	bundled
yoshkar_ola	Europe/Moscow	ЦДУМ России	bundled
volgograd	Europe/Volgograd	ЦДУМ России	bundled
kurgan	Asia/Yekaterinburg	ЦДУМ России	bundled
orenburg	Asia/Yekaterinburg	ЦДУМ России	bundled
penza	Europe/Moscow	ЦДУМ России	bundled
perm	Asia/Yekaterinburg	ЦДУМ России	bundled
rostov_on_don	Europe/Moscow	ЦДУМ России	bundled
salekhard	Asia/Yekaterinburg	ЦДУМ России	bundled
samara	Europe/Samara	ЦДУМ России	bundled
ulyanovsk	Europe/Ulyanovsk	ЦДУМ России	bundled
khabarovsk	Asia/Vladivostok	ЦДУМ России	bundled
cheboksary	Europe/Moscow	ЦДУМ России	bundled
chelyabinsk	Asia/Yekaterinburg	ЦДУМ России	bundled
nizhny_novgorod	Europe/Moscow	ДУМ Нижегородской области	bundled
saratov	Europe/Saratov	ДУМ Саратовской области	bundled
"""
        return Dictionary(uniqueKeysWithValues: rows.split(separator: "\n").compactMap { row in
            let fields = row.components(separatedBy: "\t")
            guard fields.count >= 4, let zone = TimeZone(identifier: fields[1]), let feed = RegionalFeed(rawValue: fields[3]) else { return nil }
            return (fields[0], Self(city: fields[0], zone: zone, publisher: fields[2], feed: feed, parameter: fields.count == 5 ? fields[4] : ""))
        })
    }()

    static func cityZone(_ id: String?) -> TimeZone? {
        guard let id else { return nil }
        let name: String
        switch id {
        case "moscow", "saint_petersburg", "grozny", "gudermes", "makhachkala", "khasavyurt", "derbent", "nazran", "nalchik", "vladikavkaz", "cherkessk", "maykop", "kazan", "nizhny_novgorod": name = "Europe/Moscow"
        case "ufa", "yekaterinburg": name = "Asia/Yekaterinburg"
        case "astrakhan": name = "Europe/Astrakhan"
        case "samara": name = "Europe/Samara"
        case "novosibirsk": name = "Asia/Novosibirsk"
        case "tashkent", "namangan", "andijan", "fergana": name = "Asia/Tashkent"
        case "samarkand", "bukhara", "nukus", "qarshi", "urgench": name = "Asia/Samarkand"
        case "dushanbe", "khujand", "kulob", "bokhtar", "istaravshan", "khorog": name = "Asia/Dushanbe"
        case "almaty", "astana", "shymkent", "karaganda", "taraz", "turkistan": name = "Asia/Almaty"
        case "aktobe": name = "Asia/Aqtobe"
        case "atyrau": name = "Asia/Atyrau"
        case "bishkek", "osh", "jalal_abad", "karakol": name = "Asia/Bishkek"
        case "baku", "ganja", "sumgait": name = "Asia/Baku"
        case "istanbul", "ankara", "bursa": name = "Europe/Istanbul"
        case "mecca", "medina": name = "Asia/Riyadh"
        case "cairo": name = "Africa/Cairo"
        case "dubai": name = "Asia/Dubai"
        case "london": name = "Europe/London"
        case "berlin": name = "Europe/Berlin"
        case "amsterdam": name = "Europe/Amsterdam"
        case "paris": name = "Europe/Paris"
        case "new_york": name = "America/New_York"
        default: return sources[id]?.zone
        }
        return TimeZone(identifier: name)
    }

    static func match(_ city: City, entries: [CityCatalog.Entry]) -> Self? {
        let name = city.name.trimmingCharacters(in: .whitespacesAndNewlines)
        for entry in entries {
            guard let source = sources[entry.id] else { continue }
            let named = city.id.map { $0 == entry.id } ?? entry.names.values.contains {
                let short = $0.components(separatedBy: " (")[0].components(separatedBy: ",")[0]
                return $0.caseInsensitiveCompare(name) == .orderedSame || short.caseInsensitiveCompare(name) == .orderedSame
            }
            guard named else { continue }
            let rad = Double.pi / 180
            let dy = (entry.lat - city.latitude) * rad, dx = (entry.lon - city.longitude) * rad
            let hav = pow(sin(dy / 2), 2) + cos(entry.lat * rad) * cos(city.latitude * rad) * pow(sin(dx / 2), 2)
            if 12742 * asin(sqrt(min(1, max(0, hav)))) <= 20 { return source }
        }
        return nil
    }

    func url(for month: LocalDay) -> URL? {
        switch feed {
        case .grozny: return URL(string: "https://assolat.ru/api/namaz/\(month.year)/\(month.month)?city=grozny")
        case .kazan: return URL(string: "https://dumrt.ru/netcat_files/391/638/Kazan.csv")
        case .dagestan: return month.year == 2026 ? URL(string: "https://muftiyatrd.ru/json/namaz/\(parameter).json") : nil
        case .bashkortostan:
            var c = URLComponents(string: "https://api.dumrb.com/Prayer/GetByCity")
            c?.queryItems = ["cityName": parameter, "year": String(month.year), "month": String(month.month)].map { URLQueryItem(name: $0.key, value: $0.value) }
            return c?.url
        case .kazakhstan:
            let point = parameter.components(separatedBy: "|")[1]
            return URL(string: "https://api.muftyat.kz/prayer-times/\(month.year)/\(point)")
        case .kyrgyzstan:
            let pair = String(format: "%02d-%04d", month.month, month.year)
            return URL(string: "https://muftiyat.kg/ru/api/v1/calendar/\(parameter)/?start=01-\(pair)&end=\(month.daysInMonth)-\(pair)")
        case .bundled: return nil
        }
    }

    // Minutes since midnight in the authority's own time zone, ordered Fajr through Isha.
    typealias Month = [String: [Int]]

    static func parseRow(_ fields: [String]) -> [Int]? {
        let values = fields.compactMap { value -> Int? in
            let parts = value.trimmingCharacters(in: .whitespacesAndNewlines).split(separator: ":", omittingEmptySubsequences: false)
            guard parts.count == 2, let h = Int(parts[0]), let m = Int(parts[1]), (0..<24).contains(h), (0..<60).contains(m) else { return nil }
            return h * 60 + m
        }
        guard values.count == 6, fields.count == 6,
              values[0] < values[1] || (values[0] >= 1200 && values[1] < 720),
              values[1] < values[2], values[2] < values[3], values[3] < values[4],
              values[4] < values[5] || (values[5] < 360 && values[4] > 720) else { return nil }
        return values
    }

    func bundledTable(files: [URL]) -> Month {
        var result = Month()
        for file in files {
            guard let data = try? String(contentsOf: file, encoding: .utf8) else { continue }
            for row in data.split(whereSeparator: \.isNewline) where row.hasPrefix(city + "\t") {
                let f = row.split(separator: "\t").map(String.init)
                if f.count == 8, let times = Self.parseRow(Array(f.dropFirst(2))) { result[f[1]] = times }
            }
        }
        return result
    }

    func parse(_ data: Data, month: LocalDay) -> Month? {
        typealias JSON = [String: Any]
        var parsed = Month()
        let common = ["fajr", "sunrise", "dhuhr", "asr", "maghrib", "isha"]
        func add(_ date: String, values: [String]) -> Bool {
            guard let day = LocalDay(key: date), day.key == date else { return false }
            guard day.year == month.year, day.month == month.month else { return true }
            guard (1...month.daysInMonth).contains(day.day), parsed[date] == nil, let row = Self.parseRow(values) else { return false }
            parsed[date] = row
            return true
        }
        func date(_ d: Int) -> String { LocalDay(year: month.year, month: month.month, day: d).key }
        func reverse(_ value: String, sep: Character) -> String {
            value.split(separator: sep).reversed().joined(separator: "-")
        }
        func dated(_ rows: [JSON]?, key: String, reversed: Bool = false) -> Bool {
            guard let rows else { return false }
            return rows.allSatisfy { r in
                guard let raw = r[key] as? String else { return false }
                return add(reversed ? reverse(raw, sep: "-") : raw, values: common.compactMap { r[$0] as? String })
            }
        }
        let obj = try? JSONSerialization.jsonObject(with: data)
        let json = obj as? JSON
        switch feed {
        case .grozny:
            guard json?["city"] as? String == city, json?["year"] as? Int == month.year, json?["month"] as? Int == month.month,
                  dated(json?["data"] as? [JSON], key: "date") else { return nil }
        case .kazan:
            guard let csv = String(data: data, encoding: .utf8) else { return nil }
            for line in csv.split(whereSeparator: \.isNewline) where !line.trimmingCharacters(in: .whitespaces).isEmpty {
                let fields = line.trimmingCharacters(in: .whitespacesAndNewlines).trimmingCharacters(in: CharacterSet(charactersIn: "\u{feff}")).components(separatedBy: ";")
                guard fields.count >= 9, add(reverse(fields[0], sep: "."), values: [1,3,5,6,7,8].map { fields[$0] }) else { return nil }
            }
        case .dagestan:
            guard month.year == 2026, let rows = obj as? [JSON], rows.count == 365 else { return nil }
            let offset = [0,31,59,90,120,151,181,212,243,273,304,334][month.month - 1]
            for day in 1...month.daysInMonth {
                let row = rows[offset + day - 1]
                guard add(date(day), values: ["namaz_1", "voshod", "namaz_2", "namaz_3", "namaz_4", "namaz_5"].compactMap { row[$0] as? String }) else { return nil }
            }
        case .bashkortostan:
            guard json?["cityName"] as? String == parameter, json?["year"] as? Int == month.year, json?["month"] as? Int == month.month,
                  let rows = json?["prayerTimes"] as? [JSON], rows.count == month.daysInMonth else { return nil }
            for (i, row) in rows.enumerated() {
                guard add(date(i+1), values: common.compactMap { row[$0] as? String }) else { return nil }
            }
        case .kazakhstan:
            guard json?["year"] as? Int == month.year, json?["city"] as? String == parameter.components(separatedBy: "|")[0],
                  dated(json?["result"] as? [JSON], key: "Date") else { return nil }
        case .kyrgyzstan:
            guard json?["id"] as? Int == Int(parameter), dated(json?["prayertimes"] as? [JSON], key: "date", reversed: true) else { return nil }
        case .bundled: return nil
        }
        return parsed.count == month.daysInMonth ? parsed : nil
    }
}
