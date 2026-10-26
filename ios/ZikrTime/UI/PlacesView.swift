import SwiftUI
import YandexMapsMobile

final class MapAppDelegate: NSObject, UIApplicationDelegate {
    static private(set) var configured = false
    func application(_ application: UIApplication, didFinishLaunchingWithOptions options: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
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
    @State private var selected: PrayerPlace?

    private var origin: City { nearby ?? store.city }
    private var places: [PrayerPlace] {
        (document.value?.places ?? []).filter {
            $0.valid && (kind == "all" || (kind == "mosque" ? $0.kind == "mosque" : $0.kind != "mosque"))
        }.sorted { $0.distanceKm(lat: origin.latitude, lon: origin.longitude) < $1.distanceKm(lat: origin.latitude, lon: origin.longitude) }
    }

    var body: some View {
        Page {
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
            if MapAppDelegate.configured {
                PrayerPlacesMap(places: Array(places.prefix(300)), latitude: origin.latitude, longitude: origin.longitude) { selected = $0 }
                    .frame(height: 300).clipShape(RoundedRectangle(cornerRadius: 24))
            }
            if document.loading && places.isEmpty { ProgressView(L("places_loading")) }
            if document.failed { Text(L("shop_offline")).font(.footnote).foregroundStyle(Brand.secondaryText) }
            if places.isEmpty && !document.loading { Text(L("places_empty")).font(.subheadline) }
            LazyVStack(spacing: 12) {
                ForEach(places) { place in
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
        .navigationTitle(L("places_title"))
        .navigationBarTitleDisplayMode(.inline)
        .task { await document.refresh() }
        .refreshable { await document.refresh(force: true) }
        .sheet(item: $selected) { place in
            NavigationStack { PlaceDetailView(place: place) }
        }
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
    final class Coordinator: NSObject, YMKMapObjectTapListener {
        let select: (PrayerPlace) -> Void
        var center = ""
        var ids: [String] = []
        var places: [String: PrayerPlace] = [:]
        init(select: @escaping (PrayerPlace) -> Void) { self.select = select }
        func onMapObjectTap(with mapObject: YMKMapObject, point: YMKPoint) -> Bool {
            guard let id = mapObject.userData as? String, let place = places[id] else { return false }
            select(place)
            return true
        }
    }
}
