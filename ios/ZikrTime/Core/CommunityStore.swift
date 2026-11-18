import Foundation
import Combine

/// Keep the last valid document offline. Commit its ETag only after decoding succeeds.
@MainActor
final class RemoteDocument<Value: Decodable>: ObservableObject {
    @Published private(set) var value: Value?
    @Published private(set) var loading = false
    @Published private(set) var failed = false
    private let name: String
    private var updated = Date.distantPast
    private let defaults = UserDefaults.standard
    private var key: String { "community_" + name }

    init(_ name: String) {
        self.name = name
        #if DEBUG
        if QA.active {
            let fixture = name == "places" ? Self.placesFixture : Self.reviewsFixture
            value = try? JSONDecoder().decode(Value.self, from: Data(fixture.utf8))
            return
        }
        #endif
        if let data = defaults.data(forKey: "community_" + name) {
            value = try? JSONDecoder().decode(Value.self, from: data)
        }
    }

    #if DEBUG
    private static var placesFixture: String {
        #"{"places":[{"id":"qa_mosque","kind":"mosque","name":"Московская Соборная мечеть","lat":55.7794,"lon":37.6267,"address":"Выползов переулок, 7","who":"both","wudu":true,"src":"osm"}],"seller_bot":""}"#
    }
    private static var reviewsFixture: String {
        #"{"reviews":{"place:qa_mosque":{"avg":5.0,"n":1,"items":[{"r":5,"t":"Пример отзыва для проверки интерфейса","n":"Тест","d":1792886400}]}}}"#
    }
    #endif

    func refresh(force: Bool = false) async {
        guard !loading, force || Date().timeIntervalSince(updated) >= 60 else { return }
        if QA.active { return }
        loading = true
        defer { loading = false }
        var request = URLRequest(url: URL(string: ShopCatalog.contentBase + name + ".json")!)
        request.timeoutInterval = 15
        if value != nil, let tag = defaults.string(forKey: key + "_etag") {
            request.setValue(tag, forHTTPHeaderField: "If-None-Match")
        }
        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let http = response as? HTTPURLResponse else { throw URLError(.badServerResponse) }
            if http.statusCode == 304, value != nil {
                failed = false
                updated = Date()
                return
            }
            guard http.statusCode == 200 else { throw URLError(.badServerResponse) }
            let parsed = try JSONDecoder().decode(Value.self, from: data)
            defaults.set(data, forKey: key)
            defaults.set(http.value(forHTTPHeaderField: "ETag"), forKey: key + "_etag")
            value = parsed
            updated = Date()
            failed = false
        } catch is CancellationError {
            return
        } catch {
            failed = true
        }
    }
}

@MainActor
enum Community {
    static let places = RemoteDocument<PlacesDocument>("places")
    static let reviews = RemoteDocument<ReviewsDocument>("reviews")
    static func alreadyReviewed(_ target: String) -> Bool {
        (UserDefaults.standard.stringArray(forKey: "reviews_sent") ?? []).contains(target)
    }

    /// Same moderated inbox and JSON protocol as Android. No review is published by the client.
    static func sendReview(target: String, rating: Int, text: String, name: String) async throws {
        if (reviews.value?.upload?.expires ?? 0) < Date().timeIntervalSince1970 + 60 {
            await reviews.refresh(force: true)
        }
        guard !alreadyReviewed(target), (1...5).contains(rating),
              let form = reviews.value?.upload, let url = form.destination,
              form.expires > Date().timeIntervalSince1970 + 10 else { throw URLError(.badServerResponse) }
        let defaults = UserDefaults.standard
        let device = defaults.string(forKey: "reviews_device") ?? UUID().uuidString.replacingOccurrences(of: "-", with: "").lowercased()
        defaults.set(device, forKey: "reviews_device")
        let payload = try JSONSerialization.data(withJSONObject: [
            "target": target, "rating": rating, "text": String(text.trimmingCharacters(in: .whitespacesAndNewlines).prefix(600)),
            "name": String(name.trimmingCharacters(in: .whitespacesAndNewlines).prefix(40)), "device": device,
            "ts": Int(Date().timeIntervalSince1970)
        ])
        guard payload.count <= form.max else { throw URLError(.dataLengthExceedsMaximum) }
        let boundary = "zikr-" + UUID().uuidString
        var body = Data()
        func append(_ text: String) { body.append(Data(text.utf8)) }
        func field(_ name: String, _ value: String) {
            append("--\(boundary)\r\nContent-Disposition: form-data; name=\"\(name)\"\r\n\r\n\(value)\r\n")
        }
        field("key", form.prefix + UUID().uuidString.lowercased() + ".json")
        for (key, value) in form.fields.sorted(by: { $0.key < $1.key }) {
            guard !key.contains("\""), !key.contains("\r"), !key.contains("\n") else { throw URLError(.badServerResponse) }
            field(key, value)
        }
        append("--\(boundary)\r\nContent-Disposition: form-data; name=\"file\"; filename=\"review.json\"\r\nContent-Type: application/json\r\n\r\n")
        body.append(payload)
        append("\r\n--\(boundary)--\r\n")
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.timeoutInterval = 20
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        let (_, response) = try await URLSession.shared.upload(for: request, from: body)
        guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) else { throw URLError(.badServerResponse) }
        let sent = defaults.stringArray(forKey: "reviews_sent") ?? []
        defaults.set(Array((sent + [target]).suffix(500)), forKey: "reviews_sent")
    }
}
