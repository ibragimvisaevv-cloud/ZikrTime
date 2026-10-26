import Foundation

struct PrayerPlace: Decodable, Identifiable {
    let id: String
    let kind: String
    let name: String
    let lat: Double
    let lon: Double
    let address: String?
    let hours: String?
    let who: String?
    let wudu: Bool?
    let photo: String?
    let note: String?
    let src: String?

    var valid: Bool { !id.isEmpty && lat.isFinite && lon.isFinite && (-90...90).contains(lat) && (-180...180).contains(lon) }
    func distanceKm(lat latitude: Double, lon longitude: Double) -> Double {
        let radians = Double.pi / 180
        let a = pow(sin((lat - latitude) * radians / 2), 2)
            + cos(latitude * radians) * cos(lat * radians) * pow(sin((lon - longitude) * radians / 2), 2)
        return 6371 * 2 * asin(sqrt(min(1, max(0, a))))
    }
}

struct PlacesDocument: Decodable {
    let places: [PrayerPlace]
    let seller_bot: String?
    let donate_url: String?
}

struct ReviewItem: Decodable {
    let r: Int
    let t: String
    let n: String
    let d: Double
}

struct ReviewSummary: Decodable {
    let avg: Double
    let n: Int
    let items: [ReviewItem]
}

struct ReviewUpload: Decodable {
    let url: String
    let prefix: String
    let max: Int
    let expires: Double
    let fields: [String: String]

    /// The signed form must point at the review inbox, never an arbitrary endpoint.
    var destination: URL? {
        guard let destination = URL(string: url), destination.scheme == "https",
              destination.host == "storage.yandexcloud.net",
              destination.path == "/zikrtime-shop-data",
              prefix == "reviews/pending/", max > 0 else { return nil }
        return destination
    }
}

struct ReviewsDocument: Decodable {
    let reviews: [String: ReviewSummary]
    let upload: ReviewUpload?
}
