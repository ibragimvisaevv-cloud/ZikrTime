import Foundation

@main
struct RegionalCheck {
    static func main() throws {
        let assets = URL(fileURLWithPath: CommandLine.arguments[1]).appendingPathComponent("app/src/main/assets")
        let base = try CityCatalog.parse(Data(contentsOf: assets.appendingPathComponent("cities.json")))
        let catalog = try base.addingRegions(String(contentsOf: assets.appendingPathComponent("cities_ru_regions.tsv"), encoding: .utf8))
        precondition(Set(catalog.cities.map(\.id)).count == catalog.cities.count)
        precondition(Set(RegionalPrayerSource.sources.keys).isSubset(of: Set(catalog.cities.map(\.id))))
        let files = ["regional", "published-2026"].map { assets.appendingPathComponent("prayer/\($0).tsv") }
        let source = RegionalPrayerSource.sources["grozny"]!
        let rows = source.bundledTable(files: files)
        let date = LocalDay(year: 2026, month: 11, day: 19)
        precondition(rows[date.key] == [337,412,750,877,992,1082])
        precondition(rows["2027-11-19"] == nil)
        let gps = City(id: nil, name: "Грозный", latitude: 43.318, longitude: 45.696)
        precondition(RegionalPrayerSource.match(gps, entries: catalog.cities)?.city == "grozny")
        let distant = City(id: nil, name: "Грозный", latitude: 55.75, longitude: 37.62)
        precondition(RegionalPrayerSource.match(distant, entries: catalog.cities) == nil)
        let times = ["05:37","06:52","12:30","14:37","16:32","18:02"]
        let names = ["fajr","sunrise","dhuhr","asr","maghrib","isha"]
        let month = (1...30).map { day -> [String:String] in
            var v = Dictionary(uniqueKeysWithValues: zip(names, times))
            v["date"] = LocalDay(year: 2026, month: 11, day: day).key
            return v
        }
        func response(_ city: String, _ value: [[String:String]]) throws -> Data {
            try JSONSerialization.data(withJSONObject: ["city": city, "year": 2026, "month": 11, "data": value])
        }
        let full = try response("grozny", month), partial = try response("grozny", Array(month.dropLast())), wrongCity = try response("kazan", month)
        precondition(source.parse(full, month: date)?[date.key] == rows[date.key])
        precondition(source.parse(partial, month: date) == nil && source.parse(wrongCity, month: date) == nil)
        precondition(RegionalPrayerSource.parseRow(["23:00","01:30","12:00","16:00","23:05","00:35"]) == [1380,90,720,960,1385,35])
        precondition(RegionalPrayerSource.parseRow(["05:00","06:00","12:00","12:00","17:00","20:00"]) == nil)
        for month in [6, 12] {
            let polar = PrayerCalculator.calculation(day: LocalDay(year: 2026, month: month, day: 21),
                latitude: 69, longitude: 33, method: .russia, madhab: .standard, zone: source.zone)
            let times = Prayer.allCases.compactMap { polar.times[$0]?.timeIntervalSince1970 }
            precondition(polar.polar && times.count == 6 && times.allSatisfy(\.isFinite))
            precondition(zip(times, times.dropFirst()).allSatisfy { $0.0 < $0.1 })
        }
        print("Regional city matching, offline timetable, calendar boundaries and complete-month refresh passed.")
    }
}
