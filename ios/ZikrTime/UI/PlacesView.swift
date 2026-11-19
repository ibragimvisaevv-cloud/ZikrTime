import SwiftUI
import YandexMapsMobile
import UserNotifications

final class MapAppDelegate: NSObject, UIApplicationDelegate {
    static private(set) var configured = false
    func application(_ application: UIApplication, didFinishLaunchingWithOptions options: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        UNUserNotificationCenter.current().delegate = ReadingDelivery.shared
        let key = Bundle.main.object(forInfoDictionaryKey: "YandexMapKitKey") as? String ?? ""
        if !key.isEmpty && !key.hasPrefix("$(") {
            YMKMapKit.setApiKey(key)
            _ = YMKMapKit.sharedInstance()
            Self.configured = true
        }
        return true
    }
}

struct PlacesView: View {
    @EnvironmentObject private var store: Store
    @ObservedObject private var document = Community.places
    @StateObject private var location = LocationFinder()
    @State private var nearby: City?
    @State private var kind = "all"
    @State private var query = ""
    @State private var showMap = true
    @State private var detent: PresentationDetent = .height(210)
    @State private var selected: PrayerPlace?

    private var origin: City { nearby ?? store.city }
    private var places: [PrayerPlace] {
        let term = query.trimmingCharacters(in: .whitespacesAndNewlines)
        return (document.value?.places ?? []).filter {
            $0.valid && (kind == "all" || (kind == "mosque" ? $0.kind == "mosque" : ["room", "mall"].contains($0.kind))) &&
            (term.isEmpty || ($0.name + " " + ($0.address ?? "")).localizedCaseInsensitiveContains(term))
        }.map { ($0, $0.distanceKm(lat: origin.latitude, lon: origin.longitude)) }
         .sorted { $0.1 < $1.1 }.map { $0.0 }
    }

    var body: some View {
        let visible = places
        VStack(spacing: 0) {
            if MapAppDelegate.configured && showMap {
                PrayerPlacesMap(places: Array(visible.prefix(300)), latitude: origin.latitude, longitude: origin.longitude) { selected = $0 }
                    .frame(height: 190).clipShape(RoundedRectangle(cornerRadius: 20))
                    .padding(.horizontal, 16)
            }
            Page {
                TextField(L("places_search_label"), text: $query)
                    .textFieldStyle(.roundedBorder).submitLabel(.search)
                    .accessibilityIdentifier("places_search")
                Picker(L("places_title"), selection: $kind) {
                    Text(L("places_all")).tag("all")
                    Text(L("places_mosques")).tag("mosque")
                    Text(L("places_rooms")).tag("room")
                }.pickerStyle(.segmented)
                HStack(alignment: .top) {
                    Text(L(nearby == nil ? "places_near_city" : "places_near_you")).font(.caption).foregroundStyle(Brand.secondaryText)
                    Spacer()
                    Button { location.locate { nearby = $0 } } label: {
                        Image(systemName: "location.fill").frame(minWidth: 44, minHeight: 44)
                    }.accessibilityLabel(L("places_where_am_i"))
                }
                if location.status == .denied || location.status == .failed {
                    Text(L(location.status == .denied ? "city_locate_denied" : "city_locate_failed")).font(.caption).foregroundStyle(Brand.secondaryText)
                }
                HStack {
                    if MapAppDelegate.configured {
                        Button(L(showMap ? "place_hide_map" : "place_show_map")) { showMap.toggle() }
                    }
                    Spacer()
                    Button(L("places_yandex_search")) { searchMap() }
                }
                if document.loading && visible.isEmpty { ProgressView(L("places_loading")) }
                if document.failed { Text(L("shop_offline")).font(.footnote).foregroundStyle(Brand.secondaryText) }
                if visible.isEmpty && !document.loading { Text(L(query.isEmpty ? "places_empty" : "places_search_empty")).font(.subheadline) }
                LazyVStack(spacing: 12) {
                    ForEach(visible.prefix(100)) { place in
                        Button { selected = place } label: {
                            Card(padding: 16) {
                                HStack(spacing: 12) {
                                    IconBadge(systemName: place.kind == "mosque" ? "building.columns" : "mappin.and.ellipse")
                                    VStack(alignment: .leading, spacing: 4) {
                                        Text(place.name).font(.headline).foregroundStyle(.primary)
                                        Text(distance(place)).font(.caption).foregroundStyle(Brand.primary)
                                        if let address = place.address, !address.isEmpty {
                                            Text(address).font(.caption).foregroundStyle(Brand.secondaryText)
                                        }
                                    }
                                    Spacer(minLength: 0)
                                    Image(systemName: "chevron.right").foregroundStyle(Brand.secondaryText)
                                }
                            }
                        }.buttonStyle(.plain)
                    }
                }
                Card {
                    Text(L("places_suggest_title")).font(.headline)
                    Text(L("places_suggest_text")).font(.subheadline).padding(.vertical, 8)
                    let bot = document.value?.seller_bot ?? ShopStore.shared.catalog.sellerBot
                    if !bot.isEmpty {
                        Button(L("places_suggest")) { openURL("https://t.me/" + bot.replacingOccurrences(of: "@", with: "") + "?start=place") }
                    }
                }
                Text(L("places_osm_credit")).font(.caption).foregroundStyle(Brand.secondaryText)
            }
        }
        .navigationTitle(L("places_title"))
        .navigationBarTitleDisplayMode(.inline)
        .task { await document.refresh() }
        .refreshable { await document.refresh(force: true) }
        .sheet(item: $selected, onDismiss: { detent = .height(210) }) { place in
            Group {
                if detent == .large {
                    NavigationStack { PlaceDetailView(place: place) }
                } else {
                    VStack(alignment: .leading, spacing: 10) {
                        Text(place.name).font(.headline).lineLimit(2)
                        Text([distance(place), place.address ?? ""].filter { !$0.isEmpty }.joined(separator: " · "))
                            .font(.subheadline).foregroundStyle(Brand.secondaryText).lineLimit(2)
                        HStack {
                            Button(L("places_details")) { detent = .large }
                            Spacer()
                            Button(L("places_route")) { openURL("https://yandex.ru/maps/?rtext=~\(place.lat),\(place.lon)&rtt=pd") }
                        }.buttonStyle(.bordered)
                    }.frame(maxWidth: .infinity, alignment: .leading).padding(20)
                }
            }
            .presentationDetents([.height(210), .large], selection: $detent)
            .presentationDragIndicator(.visible)
        }
    }

    private func searchMap() {
        let term = query.trimmingCharacters(in: .whitespacesAndNewlines)
        var components = URLComponents(string: "https://yandex.ru/maps/")
        components?.queryItems = [URLQueryItem(name: "ll", value: "\(origin.longitude),\(origin.latitude)"),
                                  URLQueryItem(name: "z", value: "13"),
                                  URLQueryItem(name: "text", value: term.isEmpty ? L("place_kind_mosque") : term)]
        if let url = components?.url { openURL(url.absoluteString) }
    }

    private func distance(_ place: PrayerPlace) -> String {
        let km = place.distanceKm(lat: origin.latitude, lon: origin.longitude)
        return km < 1 ? L("places_distance_m", Int(km * 1000)) : L("places_distance_km", String(format: "%.1f", km))
    }
}

private struct PlaceDetailView: View {
    let place: PrayerPlace
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        Page {
            if let photo = place.photo, let url = ShopCatalog.imageURL(photo) {
                AsyncImage(url: url) { image in image.resizable().scaledToFit() } placeholder: { ProgressView() }
                    .clipShape(RoundedRectangle(cornerRadius: 24))
            }
            Card {
                Text(place.name).font(.title2.weight(.semibold))
                Text(L("place_kind_" + (["mosque", "room", "mall"].contains(place.kind) ? place.kind : "other")))
                    .font(.subheadline).foregroundStyle(Brand.primary).padding(.top, 4)
                ForEach([place.address, place.hours, place.note].compactMap { $0 }.filter { !$0.isEmpty }, id: \.self) { text in
                    Text(text).font(.subheadline).padding(.top, 8)
                }
                if let who = place.who, ["all", "men", "women", "both"].contains(who) {
                    Text(L("places_who_" + who)).font(.subheadline).padding(.top, 8)
                }
                if let wudu = place.wudu { Text(L(wudu ? "places_wudu_yes" : "places_wudu_no")).font(.subheadline).padding(.top, 8) }
                Button(L("places_route")) {
                    openURL("https://yandex.ru/maps/?rtext=~\(place.lat),\(place.lon)&rtt=pd")
                }.padding(.top, 16)
            }
            ReviewsView(target: "place:" + place.id)
            if place.src == "osm" { Text(L("places_osm_credit")).font(.caption).foregroundStyle(Brand.secondaryText) }
        }
        .navigationTitle(L("places_title"))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .confirmationAction) { Button { dismiss() } label: { Image(systemName: "xmark") } }
        }
    }
}

private struct PrayerPlacesMap: UIViewRepresentable {
    let places: [PrayerPlace]
    let latitude: Double
    let longitude: Double
    let select: (PrayerPlace) -> Void
    func makeCoordinator() -> Coordinator { Coordinator(select: select) }
    func makeUIView(context: Context) -> YMKMapView {
        let view = YMKMapView(frame: .zero, vulkanPreferred: true, transparencySupport: false)!
        return view
    }
    func updateUIView(_ view: YMKMapView, context: Context) {
        let map: YMKMap = view.mapWindow.map
        let center = "\(latitude),\(longitude)"
        if context.coordinator.center != center {
            #if DEBUG
            // CI must observe loaded tiles, not just markers on an empty map.
            if QA.active { map.setMapLoadedListenerWith(context.coordinator) }
            #endif
            map.move(with: YMKCameraPosition(target: YMKPoint(latitude: latitude, longitude: longitude), zoom: 11, azimuth: 0, tilt: 0))
            context.coordinator.center = center
        }
        let ids = places.map(\.id)
        guard context.coordinator.ids != ids else { return }
        context.coordinator.ids = ids
        context.coordinator.places = Dictionary(places.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
        map.mapObjects.clear()
        let icon = UIImage(systemName: "mappin.circle.fill")!.withTintColor(UIColor(red: 0.06, green: 0.42, blue: 0.31, alpha: 1), renderingMode: .alwaysOriginal)
        for place in places {
            let mark = map.mapObjects.addPlacemark()
            mark.geometry = YMKPoint(latitude: place.lat, longitude: place.lon)
            mark.setIconWith(icon)
            mark.userData = place.id
            mark.addTapListener(with: context.coordinator)
        }
    }
    final class Coordinator: NSObject, YMKMapObjectTapListener, YMKMapLoadedListener {
        let select: (PrayerPlace) -> Void
        var center = ""
        var ids: [String] = []
        var places: [String: PrayerPlace] = [:]
        init(select: @escaping (PrayerPlace) -> Void) { self.select = select }
        func onMapLoaded(with statistics: YMKMapLoadStatistics) {
            #if DEBUG
            DispatchQueue.main.async {
            guard QA.active else { return }
            let file = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
                .appendingPathComponent("qa-map-loaded.txt")
            do {
                try Data("visible tiles loaded\n".utf8).write(to: file, options: .atomic)
            } catch {
                print("Could not record map load: \(error)")
            }
            }
            #endif
        }
        func onMapObjectTap(with mapObject: YMKMapObject, point: YMKPoint) -> Bool {
            guard let id = mapObject.userData as? String, let place = places[id] else { return false }
            select(place)
            return true
        }
    }
}
