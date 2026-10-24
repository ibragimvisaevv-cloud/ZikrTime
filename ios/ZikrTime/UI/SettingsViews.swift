import CoreLocation
import SwiftUI
import UIKit

struct MoreView: View {
    @EnvironmentObject private var store: Store

    var body: some View {
        Page {
            Text(L("more_title")).font(.largeTitle.weight(.bold))
            Card(padding: 12) {
                NavigationLink(value: Route.language) {
                    SettingRow(title: L("settings_language"), subtitle: languageName, icon: "globe")
                }
                NavigationLink(value: Route.city) {
                    SettingRow(title: L("settings_city"), subtitle: CityCatalog.shared.displayName(store.city, lang: L10n.code), icon: "mappin.and.ellipse")
                }
                NavigationLink(value: Route.prayerSettings) {
                    SettingRow(title: L("settings_prayer_times"), subtitle: L(store.method.nameKey), icon: "clock")
                }
                NavigationLink(value: Route.alarm) {
                    SettingRow(title: L("alarm_title"),
                               subtitle: store.alarmEnabled ? L("alarm_status_on", store.alarmOffset) : L("state_off"),
                               icon: "alarm")
                }
            }
            .buttonStyle(.plain)

            Card(padding: 12) {
                NavigationLink(value: Route.tasbih) {
                    SettingRow(title: L("tasbih_title"), subtitle: L("tasbih_subtitle"), icon: "circle.dotted")
                }
                NavigationLink(value: Route.focus) {
                    SettingRow(title: L("focus_title"), subtitle: L("focus_subtitle"), icon: "hourglass")
                }
            }
            .buttonStyle(.plain)

            Card(padding: 12) {
                NavigationLink(value: Route.about) {
                    SettingRow(title: L("about_title"), subtitle: L("about_version", appVersion), icon: "info.circle")
                }
            }
            .buttonStyle(.plain)
        }
        .toolbar(.hidden, for: .navigationBar)
    }

    private var languageName: String {
        if store.language.isEmpty { return L("language_system") }
        return L10n.supported.first { $0.code == store.language }?.nativeName ?? store.language
    }
}

var appVersion: String {
    Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "1.0"
}

// MARK: - Язык

struct LanguageList: View {
    @EnvironmentObject private var store: Store

    var body: some View {
        VStack(spacing: 0) {
            RadioLine(title: L("language_system"), selected: store.language.isEmpty) { store.language = "" }
            ForEach(L10n.supported, id: \.code) { lang in
                RadioLine(title: lang.nativeName, selected: store.language == lang.code) { store.language = lang.code }
            }
        }
    }
}

struct LanguageView: View {
    var body: some View {
        Page {
            Card(padding: 8) { LanguageList() }
            Text(L("language_note")).font(.caption).foregroundStyle(Brand.secondaryText)
        }
        .navigationTitle(L("settings_language"))
        .navigationBarTitleDisplayMode(.inline)
    }
}

struct RadioLine: View {
    let title: String
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 14) {
                Image(systemName: selected ? "largecircle.fill.circle" : "circle")
                    .font(.title3)
                    .foregroundStyle(selected ? Brand.primary : Brand.secondaryText)
                Text(title).font(.body).foregroundStyle(.primary)
                Spacer()
            }
            .padding(.vertical, 12)
            .padding(.horizontal, 12)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Город

/// Определение местоположения (один раз) и названия места.
@MainActor
final class LocationFinder: NSObject, ObservableObject, CLLocationManagerDelegate {
    enum Status { case idle, locating, failed, denied }

    @Published var status: Status = .idle
    private let manager = CLLocationManager()
    private var onFound: ((City) -> Void)?

    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyKilometer
    }

    func locate(_ completion: @escaping (City) -> Void) {
        onFound = completion
        status = .locating
        switch manager.authorizationStatus {
        case .notDetermined: manager.requestWhenInUseAuthorization()
        case .denied, .restricted: status = .denied
        default: manager.requestLocation()
        }
    }

    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        let status = manager.authorizationStatus
        Task { @MainActor in
            guard self.status == .locating else { return }
            switch status {
            case .authorizedWhenInUse, .authorizedAlways: self.manager.requestLocation()
            case .denied, .restricted: self.status = .denied
            default: break
            }
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let location = locations.last else { return }
        Task { @MainActor in await self.found(location) }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        Task { @MainActor in self.status = .failed }
    }

    private func found(_ location: CLLocation) async {
        var name = L("city_my_location")
        if let place = try? await CLGeocoder().reverseGeocodeLocation(location, preferredLocale: L10n.locale).first,
           let locality = place.locality ?? place.subAdministrativeArea ?? place.administrativeArea {
            name = locality
        }
        status = .idle
        onFound?(City(id: nil, name: name, latitude: location.coordinate.latitude, longitude: location.coordinate.longitude))
    }
}

struct CityPicker: View {
    let onPicked: (City) -> Void
    @EnvironmentObject private var store: Store
    @StateObject private var finder = LocationFinder()
    @State private var query = ""

    var body: some View {
        let catalog = CityCatalog.shared
        let lang = L10n.code
        let q = query.trimmingCharacters(in: .whitespaces).lowercased()
        VStack(alignment: .leading, spacing: 14) {
            Button {
                finder.locate { city in onPicked(city) }
            } label: {
                HStack {
                    if finder.status == .locating { ProgressView().tint(Brand.onGold) }
                    Text(finder.status == .locating ? L("city_locating") : L("city_locate"))
                }
                .font(.subheadline.weight(.semibold))
                .frame(maxWidth: .infinity, minHeight: 50)
                .foregroundStyle(Brand.onGold)
                .background(Brand.goldSoft, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            }
            .buttonStyle(.plain)

            if finder.status == .failed || finder.status == .denied {
                Text(L(finder.status == .denied ? "city_locate_denied" : "city_locate_failed"))
                    .font(.caption)
                    .foregroundStyle(.red)
            }

            HStack {
                Image(systemName: "magnifyingglass").foregroundStyle(Brand.secondaryText)
                TextField(L("city_search"), text: $query)
                    .textInputAutocapitalization(.words)
                    .autocorrectionDisabled()
            }
            .padding(14)
            .background(Brand.card, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).strokeBorder(Brand.cardBorder, lineWidth: 1))

            ForEach(catalog.groups, id: \.id) { group in
                let cities = catalog.cities.filter { c in
                    c.group == group.id && (q.isEmpty || c.names.values.contains { $0.lowercased().contains(q) })
                }
                if !cities.isEmpty {
                    SectionLabel(text: group.names[lang] ?? group.names["en"] ?? group.id)
                    Card(padding: 4) {
                        ForEach(cities, id: \.id) { entry in
                            Button {
                                onPicked(entry.city(lang))
                            } label: {
                                HStack {
                                    Text(entry.name(lang)).foregroundStyle(.primary)
                                    Spacer()
                                    if store.city.id == entry.id {
                                        Image(systemName: "checkmark").foregroundStyle(Brand.primary)
                                    }
                                }
                                .padding(.horizontal, 14)
                                .padding(.vertical, 13)
                                .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                            if entry.id != cities.last?.id {
                                Divider().padding(.leading, 14)
                            }
                        }
                    }
                }
            }
        }
    }
}

struct CityView: View {
    @EnvironmentObject private var store: Store
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        Page {
            CityPicker { city in
                store.city = city
                Lifecycle.locationChanged()
                dismiss()
            }
        }
        .navigationTitle(L("settings_city"))
        .navigationBarTitleDisplayMode(.inline)
    }
}

// MARK: - Время намаза

struct PrayerSettingsView: View {
    @EnvironmentObject private var store: Store
    @EnvironmentObject private var online: OnlineTimes
    @State private var notificationsAllowed = true

    var body: some View {
        Page {
            SectionLabel(text: L("prayer_method"))
            Card(padding: 8) {
                ForEach(CalcMethod.allCases, id: \.self) { m in
                    Button {
                        store.method = m
                        Lifecycle.locationChanged()
                    } label: {
                        HStack(alignment: .top, spacing: 14) {
                            Image(systemName: store.method == m ? "largecircle.fill.circle" : "circle")
                                .font(.title3)
                                .foregroundStyle(store.method == m ? Brand.primary : Brand.secondaryText)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(L(m.nameKey)).foregroundStyle(.primary)
                                Text(angles(m)).font(.caption).foregroundStyle(Brand.secondaryText)
                            }
                            Spacer()
                        }
                        .padding(10)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            }
            Text(L("prayer_method_hint")).font(.caption).foregroundStyle(Brand.secondaryText)

            SectionLabel(text: L("prayer_asr_section"))
            Card(padding: 8) {
                ForEach(AsrMadhab.allCases, id: \.self) { m in
                    RadioLine(title: L(m.nameKey), selected: store.madhab == m) {
                        store.madhab = m
                        Lifecycle.locationChanged()
                    }
                }
            }

            SectionLabel(text: L("prayer_source"))
            Card {
                ToggleRow(title: L("prayer_source_online"), subtitle: L("prayer_source_online_hint"), isOn: Binding(
                    get: { store.onlineTimes },
                    set: {
                        store.onlineTimes = $0
                        Lifecycle.locationChanged()
                    }
                ))
                if store.onlineTimes {
                    Text(sourceStatus).font(.caption).foregroundStyle(Brand.secondaryText).padding(.vertical, 4)
                    TonalButton(title: L("action_refresh")) {
                        Task { await online.refresh(force: true) }
                    }
                    .padding(.top, 6)
                }
            }

            SectionLabel(text: L("prayer_notifications"))
            Card {
                ToggleRow(title: L("prayer_notify"), isOn: Binding(
                    get: { store.notify },
                    set: { value in
                        store.notify = value
                        Task {
                            if value { await PrayerNotifications.requestPermission() }
                            notificationsAllowed = await PrayerNotifications.isAllowed()
                            await PrayerNotifications.reschedule()
                        }
                    }
                ))
                ToggleRow(title: L("prayer_notify_adhan"), subtitle: L("prayer_notify_adhan_hint"), isOn: Binding(
                    get: { store.notifyAdhan },
                    set: {
                        store.notifyAdhan = $0
                        Lifecycle.settingsChanged()
                    }
                ))
                if store.notify && !notificationsAllowed {
                    Text(L("notifications_denied")).font(.caption).foregroundStyle(.red)
                    TonalButton(title: L("action_open_settings")) { openSettings() }
                        .padding(.top, 6)
                }
            }

            SectionLabel(text: L("prayer_corrections"))
            Card {
                ForEach(Prayer.allCases, id: \.self) { p in
                    Stepper(value: Binding(
                        get: { store.adjustment(p) },
                        set: {
                            store.setAdjustment(p, $0)
                            Lifecycle.settingsChanged()
                        }
                    ), in: -30...30) {
                        HStack {
                            Text(L(p.nameKey))
                            Spacer()
                            Text(signed(store.adjustment(p))).font(.headline.monospacedDigit()).foregroundStyle(Brand.primary)
                        }
                    }
                    .padding(.vertical, 4)
                }
            }
            Text(L("prayer_corrections_hint")).font(.caption).foregroundStyle(Brand.secondaryText)
        }
        .navigationTitle(L("settings_prayer_times"))
        .navigationBarTitleDisplayMode(.inline)
        .task { notificationsAllowed = await PrayerNotifications.isAllowed() }
    }

    private var sourceStatus: String {
        if online.loading { return L("prayer_source_loading") }
        if let at = online.updatedAt, PrayerSchedule.isOnline() {
            let f = DateFormatter()
            f.locale = L10n.locale
            f.dateStyle = .medium
            f.timeStyle = .short
            return L("prayer_source_updated", f.string(from: at))
        }
        return L("prayer_source_failed")
    }

    private func angles(_ m: CalcMethod) -> String {
        let fajr = String(format: "%g", m.fajrAngle)
        if let minutes = m.ishaMinutes { return L("method_angles_minutes", fajr, minutes) }
        return L("method_angles", fajr, String(format: "%g", m.ishaAngle ?? 0))
    }

    private func signed(_ v: Int) -> String { v > 0 ? "+\(v)" : "\(v)" }
}

func openSettings() {
    if let url = URL(string: UIApplication.openSettingsURLString) {
        UIApplication.shared.open(url)
    }
}

// MARK: - Фокус (на iPhone — пока объяснение)

struct FocusInfoView: View {
    var body: some View {
        Page {
            Card(background: Brand.goldSoft) {
                IconBadge(systemName: "hourglass", tint: Brand.onGold, background: Brand.gold.opacity(0.5), size: 44)
                Text(L("focus_cta_title")).font(.title3.weight(.semibold)).padding(.top, 12)
                Text(L("focus_ios_text")).font(.subheadline).padding(.top, 6)
            }
            NavigationLink(value: Route.quiz(category: nil)) {
                Text(L("knowledge_start"))
                    .font(.headline)
                    .frame(maxWidth: .infinity, minHeight: 54)
                    .foregroundStyle(.white)
                    .background(Brand.emerald, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            }
            .buttonStyle(.plain)
        }
        .navigationTitle(L("focus_title"))
        .navigationBarTitleDisplayMode(.inline)
    }
}

// MARK: - О приложении

struct AboutView: View {
    var body: some View {
        Page {
            VStack(spacing: 6) {
                Image("Logo")
                    .resizable()
                    .frame(width: 96, height: 96)
                    .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
                Text(L("app_name")).font(.title.weight(.semibold))
                Text(L("slogan")).font(.subheadline).foregroundStyle(Brand.gold)
                Text(L("about_version", appVersion)).font(.caption).foregroundStyle(Brand.secondaryText)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 8)

            Card(background: Brand.goldSoft) {
                Text(L("ios_install_note")).font(.subheadline)
            }
            InfoBlock(title: L("about_privacy_title"), text: L("about_privacy_text"))
            InfoBlock(title: L("about_times_title"), text: L("about_times_text"))
            InfoBlock(title: L("about_quiz_title"), text: L("about_quiz_text"))
            InfoBlock(title: L("about_licenses_title"), text: L("about_licenses_text"))
        }
        .navigationTitle(L("about_title"))
        .navigationBarTitleDisplayMode(.inline)
    }
}

private struct InfoBlock: View {
    let title: String
    let text: String

    var body: some View {
        Card {
            Text(title).font(.headline)
            Text(text).font(.subheadline).foregroundStyle(Brand.secondaryText).padding(.top, 6)
        }
    }
}
