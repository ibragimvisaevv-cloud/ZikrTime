import CoreLocation
import SwiftUI
import UIKit

// MARK: - Кибла

/// Направление телефона по компасу (от истинного севера, если есть геолокация).
@MainActor
final class HeadingProvider: NSObject, ObservableObject, CLLocationManagerDelegate {
    @Published var heading: Double?
    @Published var lowAccuracy = false
    private let manager = CLLocationManager()

    override init() {
        super.init()
        manager.delegate = self
        manager.headingFilter = 1
    }

    var available: Bool { CLLocationManager.headingAvailable() }

    func start() {
        guard available else { return }
        if manager.authorizationStatus == .notDetermined && !QA.active {
            manager.requestWhenInUseAuthorization()
        }
        manager.startUpdatingHeading()
    }

    func stop() { manager.stopUpdatingHeading() }

    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateHeading newHeading: CLHeading) {
        let value = newHeading.trueHeading >= 0 ? newHeading.trueHeading : newHeading.magneticHeading
        let accuracy = newHeading.headingAccuracy
        Task { @MainActor in self.apply(value, accuracy: accuracy) }
    }

    private func apply(_ value: Double, accuracy: Double) {
        lowAccuracy = accuracy < 0 || accuracy > 25
        guard let old = heading else {
            heading = value
            return
        }
        // Сглаживание с учётом перехода 359° → 0°
        let delta = (value - old + 540).truncatingRemainder(dividingBy: 360) - 180
        heading = (old + delta * 0.25 + 360).truncatingRemainder(dividingBy: 360)
    }
}

struct QiblaView: View {
    @EnvironmentObject private var store: Store
    @StateObject private var compass = HeadingProvider()
    @State private var wasAligned = false

    var body: some View {
        let qibla = Qibla.bearing(lat: store.city.latitude, lon: store.city.longitude)
        let distance = Qibla.distanceKm(lat: store.city.latitude, lon: store.city.longitude)
        let relative = compass.heading.map { (qibla - $0 + 540).truncatingRemainder(dividingBy: 360) - 180 }
        let aligned = relative.map { abs($0) < 5 } ?? false

        Page {
            VStack(alignment: .leading, spacing: 2) {
                Text(L("qibla_title")).font(.largeTitle.weight(.bold))
                Text(L("qibla_subtitle")).font(.subheadline).foregroundStyle(Brand.secondaryText)
            }

            CompassDial(heading: compass.heading ?? 0, qibla: qibla, aligned: aligned)
                .aspectRatio(1, contentMode: .fit)
                .padding(8)

            Text(hint(relative: relative, aligned: aligned))
                .font(.headline)
                .foregroundStyle(aligned ? Brand.primary : Color.primary)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)

            Card {
                StatLine(label: L("qibla_bearing"), value: "\(Int(qibla.rounded()))°")
                StatLine(label: L("qibla_distance"), value: L("unit_km", Int(distance.rounded())))
                StatLine(label: L("settings_city"), value: CityCatalog.shared.displayName(store.city, lang: L10n.code))
            }

            Text(L(compass.lowAccuracy ? "qibla_calibrate" : "qibla_hint"))
                .font(.caption)
                .foregroundStyle(compass.lowAccuracy ? Color.red : Brand.secondaryText)
        }
        .toolbar(.hidden, for: .navigationBar)
        .onAppear { compass.start() }
        .onDisappear { compass.stop() }
        .onChange(of: aligned) { _, now in
            if now && !wasAligned { UINotificationFeedbackGenerator().notificationOccurred(.success) }
            wasAligned = now
        }
    }

    private func hint(relative: Double?, aligned: Bool) -> String {
        guard compass.available, let relative else { return L("qibla_no_sensor") }
        if aligned { return L("qibla_aligned") }
        let degrees = Int(abs(relative).rounded())
        return relative < 0 ? L("qibla_turn_left", degrees) : L("qibla_turn_right", degrees)
    }
}

/// Циферблат: поворачивается вместе с телефоном, стрелка указывает на Каабу.
private struct CompassDial: View {
    let heading: Double
    let qibla: Double
    let aligned: Bool

    var body: some View {
        GeometryReader { geo in
            let size = min(geo.size.width, geo.size.height)
            let r = size / 2
            let center = CGPoint(x: geo.size.width / 2, y: geo.size.height / 2)
            ZStack {
                Circle().fill(Brand.card)
                Circle().strokeBorder(aligned ? Brand.gold : Brand.cardBorder, lineWidth: 3)

                Canvas { context, canvasSize in
                    let c = CGPoint(x: canvasSize.width / 2, y: canvasSize.height / 2)
                    for deg in stride(from: 0, to: 360, by: 5) {
                        let major = deg % 30 == 0
                        let a = (Double(deg) - heading) * .pi / 180
                        let outer = r - 10, inner = r - (major ? 22 : 16)
                        var path = Path()
                        path.move(to: CGPoint(x: c.x + outer * CGFloat(sin(a)), y: c.y - outer * CGFloat(cos(a))))
                        path.addLine(to: CGPoint(x: c.x + inner * CGFloat(sin(a)), y: c.y - inner * CGFloat(cos(a))))
                        context.stroke(path, with: .color(major ? Brand.secondaryText : Brand.cardBorder), lineWidth: major ? 2 : 1)
                    }
                    // Золотая метка Каабы на кольце и стрелка от центра
                    let q = (qibla - heading) * .pi / 180
                    func point(_ dist: CGFloat, _ angle: Double) -> CGPoint {
                        CGPoint(x: c.x + dist * CGFloat(sin(angle)), y: c.y - dist * CGFloat(cos(angle)))
                    }
                    var mark = Path()
                    mark.move(to: point(r - 8, q))
                    mark.addLine(to: point(r - 24, q - 0.06))
                    mark.addLine(to: point(r - 24, q + 0.06))
                    mark.closeSubpath()
                    context.fill(mark, with: .color(Brand.gold))
                    let tip = point(r * 0.5 - 22, q)
                    let w: CGFloat = 6
                    var needle = Path()
                    needle.move(to: tip)
                    needle.addLine(to: point(w, q + .pi / 2))
                    needle.addLine(to: point(w * 1.8, q + .pi))
                    needle.addLine(to: point(w, q - .pi / 2))
                    needle.closeSubpath()
                    context.fill(needle, with: .color(Brand.gold))
                    // Неподвижная метка «куда смотрит телефон»
                    var pointer = Path()
                    pointer.move(to: CGPoint(x: c.x, y: c.y - r - 4))
                    pointer.addLine(to: CGPoint(x: c.x - 10, y: c.y - r + 12))
                    pointer.addLine(to: CGPoint(x: c.x + 10, y: c.y - r + 12))
                    pointer.closeSubpath()
                    context.fill(pointer, with: .color(aligned ? Brand.gold : Brand.primary))
                    context.fill(Path(ellipseIn: CGRect(x: c.x - 10, y: c.y - 10, width: 20, height: 20)), with: .color(Brand.card))
                    context.fill(Path(ellipseIn: CGRect(x: c.x - 6, y: c.y - 6, width: 12, height: 12)), with: .color(aligned ? Brand.gold : Brand.primary))
                }

                ForEach(0..<4, id: \.self) { i in
                    let letter = L(["compass_n", "compass_e", "compass_s", "compass_w"][i])
                    Text(letter)
                        .font(.headline)
                        .foregroundStyle(i == 0 ? Color.red : Brand.secondaryText)
                        .position(polar(center, r - 40, Double(i) * 90 - heading))
                }

                KaabaBadge()
                    .position(polar(center, r * 0.5, qibla - heading))
            }
            .frame(width: geo.size.width, height: geo.size.height)
        }
    }

    private func polar(_ c: CGPoint, _ distance: CGFloat, _ degrees: Double) -> CGPoint {
        let a = degrees * .pi / 180
        return CGPoint(x: c.x + distance * CGFloat(sin(a)), y: c.y - distance * CGFloat(cos(a)))
    }
}

/// Кааба: тёмный куб с золотым поясом.
private struct KaabaBadge: View {
    var body: some View {
        ZStack {
            Circle().fill(Brand.heroGradient)
            Circle().strokeBorder(Brand.gold, lineWidth: 2)
            ZStack(alignment: .top) {
                RoundedRectangle(cornerRadius: 2).fill(Color.black)
                Rectangle().fill(Brand.gold).frame(height: 3).padding(.top, 5)
            }
            .frame(width: 20, height: 20)
        }
        .frame(width: 42, height: 42)
    }
}

// MARK: - Тасбих

private let tasbihArabic = [
    "سُبْحَانَ اللَّهِ",
    "الْحَمْدُ لِلَّهِ",
    "اللَّهُ أَكْبَرُ",
    "لَا إِلَٰهَ إِلَّا اللَّهُ",
    "أَسْتَغْفِرُ اللَّهَ",
    "اللَّهُمَّ صَلِّ عَلَىٰ مُحَمَّدٍ",
]

struct TasbihView: View {
    @EnvironmentObject private var store: Store

    var body: some View {
        let phrase = min(max(store.tasbihPhrase, 0), tasbihArabic.count - 1)
        let target = store.tasbihTarget
        let count = store.tasbihCount
        let progress = target > 0 ? Double(count % target) / Double(target) : 0
        let laps = target > 0 ? count / target : 0

        Page {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(0..<tasbihArabic.count, id: \.self) { i in
                        Chip(text: L("zikr_\(i + 1)_name"), selected: i == phrase) {
                            if i != phrase {
                                store.tasbihPhrase = i
                                store.tasbihCount = 0
                            }
                        }
                    }
                }
            }

            VStack(spacing: 4) {
                Text(tasbihArabic[phrase])
                    .font(Brand.arabic(34))
                    .foregroundStyle(Brand.primary)
                    .multilineTextAlignment(.center)
                Text(L("zikr_\(phrase + 1)_meaning"))
                    .font(.subheadline)
                    .foregroundStyle(Brand.secondaryText)
                    .multilineTextAlignment(.center)
            }
            .frame(maxWidth: .infinity)

            Button {
                store.addTasbih()
                let reached = target > 0 && store.tasbihCount % target == 0
                if reached {
                    UINotificationFeedbackGenerator().notificationOccurred(.success)
                } else {
                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                }
            } label: {
                ZStack {
                    Circle().fill(RadialGradient(colors: [Brand.emerald, Brand.emeraldDeep], center: .center, startRadius: 10, endRadius: 180))
                    ProgressRing(progress: progress, color: Brand.gold, track: Brand.cream.opacity(0.15), lineWidth: 8) {
                        VStack(spacing: 2) {
                            Text("\(target > 0 ? count % target : count)")
                                .font(.system(size: 72, weight: .light).monospacedDigit())
                                .foregroundStyle(Brand.cream)
                            if target > 0 {
                                Text(L("tasbih_of", target)).font(.headline).foregroundStyle(Brand.cream.opacity(0.8))
                            }
                            if laps > 0 {
                                Text(L("tasbih_laps", laps)).font(.subheadline.weight(.semibold)).foregroundStyle(Brand.gold)
                            }
                        }
                    }
                    .padding(14)
                }
                .aspectRatio(1, contentMode: .fit)
                .padding(.horizontal, 20)
            }
            .buttonStyle(.plain)

            Text(L("tasbih_tap_hint"))
                .font(.caption)
                .foregroundStyle(Brand.secondaryText)
                .frame(maxWidth: .infinity)

            Card {
                Text(L("tasbih_target")).font(.subheadline.weight(.semibold))
                HStack(spacing: 8) {
                    ForEach([33, 99, 100, 0], id: \.self) { t in
                        Chip(text: t == 0 ? "∞" : "\(t)", selected: target == t) { store.tasbihTarget = t }
                    }
                }
                .padding(.vertical, 8)
                Text(L("tasbih_today", store.tasbihOn(.today())))
                    .font(.subheadline)
                    .foregroundStyle(Brand.secondaryText)
            }
        }
        .navigationTitle(L("tasbih_title"))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button(L("action_reset")) { store.tasbihCount = 0 }
            }
        }
    }
}

/// Плашка выбора.
struct Chip: View {
    let text: String
    let selected: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(text)
                .font(.subheadline.weight(.semibold))
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .foregroundStyle(selected ? Brand.onGold : Color.primary)
                .background(selected ? Brand.gold.opacity(0.35) : Brand.card, in: Capsule())
                .overlay(Capsule().strokeBorder(selected ? Color.clear : Brand.cardBorder, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}
