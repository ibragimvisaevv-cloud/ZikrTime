import SwiftUI

/// «Дорогой» вид: живой фон с мягкими пятнами света, стеклянные карточки, кнопки с бегущим бликом.
/// Цвета те же — изумруд и золото.

private let emeraldBright = Color(hex: 0x1A7D63)
private let goldLight = Color(hex: 0xFFE7A8)
private let goldDark = Color(hex: 0xC9962F)

/// Фон экранов: тёплый светлый (или глубокий зелёный ночью) с медленно плывущими пятнами света.
struct AuroraBackground: View {
    @Environment(\.colorScheme) private var scheme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var moved = false

    var body: some View {
        let dark = scheme == .dark
        ZStack {
            Brand.background
            Circle()
                .fill(Brand.emerald.opacity(dark ? 0.32 : 0.16))
                .frame(width: 380, height: 380)
                .blur(radius: 90)
                .offset(x: moved ? -130 : -50, y: moved ? -280 : -210)
            Circle()
                .fill(Brand.gold.opacity(dark ? 0.14 : 0.20))
                .frame(width: 300, height: 300)
                .blur(radius: 90)
                .offset(x: moved ? 160 : 90, y: moved ? -40 : 40)
            Circle()
                .fill(emeraldBright.opacity(dark ? 0.30 : 0.12))
                .frame(width: 340, height: 340)
                .blur(radius: 100)
                .offset(x: moved ? -40 : 70, y: moved ? 330 : 260)
        }
        .ignoresSafeArea()
        .allowsHitTesting(false)
        .onAppear {
            guard !reduceMotion else { return }
            withAnimation(.easeInOut(duration: 10).repeatForever(autoreverses: true)) { moved = true }
        }
    }
}

/// Бегущий по поверхности блик.
struct Sheen: ViewModifier {
    var opacity: Double = 0.28
    var period: Double = 3.6
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var phase: CGFloat = -1

    func body(content: Content) -> some View {
        content
            .overlay {
                GeometryReader { geo in
                    LinearGradient(colors: [.clear, .white.opacity(opacity), .clear], startPoint: .leading, endPoint: .trailing)
                        .frame(width: max(geo.size.width * 0.4, 60), height: geo.size.height * 3)
                        .rotationEffect(.degrees(20))
                        .offset(x: phase * geo.size.width * 1.4, y: -geo.size.height)
                }
                .allowsHitTesting(false)
                .clipped()
            }
            .onAppear {
                guard !reduceMotion else { return }
                withAnimation(.linear(duration: period).delay(0.6).repeatForever(autoreverses: false)) { phase = 1.2 }
            }
    }
}

/// Стеклянная поверхность: размытие фона, светлая заливка, тонкая светящаяся рамка и мягкая тень.
struct Glass: ViewModifier {
    var cornerRadius: CGFloat = 24
    var shadow: CGFloat = 14
    @Environment(\.colorScheme) private var scheme

    func body(content: Content) -> some View {
        let shape = RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
        let dark = scheme == .dark
        content
            .background(
                LinearGradient(colors: dark ? [Color(hex: 0x16302A).opacity(0.80), Color(hex: 0x0C1C18).opacity(0.72)]
                                            : [Color.white.opacity(0.62), Color.white.opacity(0.30)],
                               startPoint: .topLeading, endPoint: .bottomTrailing),
                in: shape
            )
            .background(.ultraThinMaterial, in: shape)
            .overlay(
                shape.strokeBorder(
                    LinearGradient(colors: [Color.white.opacity(dark ? 0.22 : 0.9), Color.white.opacity(dark ? 0.04 : 0.2), Brand.gold.opacity(0.25)],
                                   startPoint: .topLeading, endPoint: .bottomTrailing),
                    lineWidth: 1
                )
            )
            .shadow(color: Brand.emerald.opacity(dark ? 0.35 : 0.12), radius: shadow, x: 0, y: shadow * 0.45)
    }
}

extension View {
    func glass(cornerRadius: CGFloat = 24, shadow: CGFloat = 14) -> some View { modifier(Glass(cornerRadius: cornerRadius, shadow: shadow)) }
    func sheen(opacity: Double = 0.28, period: Double = 3.6) -> some View { modifier(Sheen(opacity: opacity, period: period)) }
}

/// Мягкое нажатие для всех «дорогих» кнопок.
struct PressScaleStyle: ButtonStyle {
    var scale: CGFloat = 0.97
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? scale : 1)
            .animation(.spring(response: 0.25, dampingFraction: 0.7), value: configuration.isPressed)
    }
}

/// Тёмно-изумрудный блок со звёздным узором, золотой рамкой, свечением и бликом.
struct GlassHero<Content: View>: View {
    @ViewBuilder var content: Content
    @State private var glow = false

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 30, style: .continuous)
        ZStack(alignment: .topLeading) {
            LinearGradient(colors: [emeraldBright, Brand.emerald, Brand.emeraldDeep, Brand.emeraldNight],
                           startPoint: .topLeading, endPoint: .bottomTrailing)
            RadialGradient(colors: [Brand.gold.opacity(glow ? 0.32 : 0.15), .clear], center: .topTrailing, startRadius: 0, endRadius: 280)
            StarPattern(color: Brand.gold.opacity(0.11))
            Color.clear.sheen(opacity: 0.10, period: 6)
            content
        }
        .clipShape(shape)
        .overlay(shape.strokeBorder(LinearGradient(colors: [goldLight.opacity(0.7), Brand.gold.opacity(0.15), goldLight.opacity(0.45)],
                                                   startPoint: .topLeading, endPoint: .bottomTrailing), lineWidth: 1))
        .shadow(color: Brand.emeraldDeep.opacity(0.35), radius: 18, x: 0, y: 10)
        .onAppear { withAnimation(.easeInOut(duration: 4).repeatForever(autoreverses: true)) { glow = true } }
    }
}

/// Главная кнопка: изумрудный градиент и бегущий блик.
struct ShimmerButton: View {
    let title: String
    var enabled: Bool = true
    let action: () -> Void

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 18, style: .continuous)
        Button(action: action) {
            Text(title)
                .font(.headline)
                .multilineTextAlignment(.center)
                .foregroundStyle(.white)
                .padding(.horizontal, 16)
                .frame(maxWidth: .infinity, minHeight: 56)
                .background(LinearGradient(colors: enabled ? [emeraldBright, Brand.emerald, Brand.emeraldDeep] : [.gray, .gray],
                                           startPoint: .topLeading, endPoint: .bottomTrailing), in: shape)
                .overlay(shape.strokeBorder(LinearGradient(colors: [.white.opacity(0.35), .clear], startPoint: .top, endPoint: .bottom), lineWidth: 1))
                .sheen()
                .clipShape(shape)
                .shadow(color: Brand.emerald.opacity(enabled ? 0.35 : 0), radius: 12, x: 0, y: 6)
        }
        .buttonStyle(PressScaleStyle())
        .disabled(!enabled)
    }
}

/// Золотая кнопка для тёмных блоков.
struct GoldButton: View {
    let title: String
    let action: () -> Void

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 18, style: .continuous)
        Button(action: action) {
            Text(title)
                .font(.headline)
                .multilineTextAlignment(.center)
                .foregroundStyle(Brand.onGold)
                .padding(.horizontal, 16)
                .frame(maxWidth: .infinity, minHeight: 56)
                .background(LinearGradient(colors: [goldLight, Brand.gold, goldDark], startPoint: .topLeading, endPoint: .bottomTrailing), in: shape)
                .sheen(opacity: 0.45)
                .clipShape(shape)
                .shadow(color: goldDark.opacity(0.45), radius: 12, x: 0, y: 6)
        }
        .buttonStyle(PressScaleStyle())
    }
}

/// Небольшая золотистая плашка (тема вопроса, «Партнёр»).
struct CategoryChip: View {
    let text: String
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        Text(text)
            .font(.caption.weight(.semibold))
            .foregroundStyle(scheme == .dark ? goldLight : Color(hex: 0x5C4310))
            .padding(.horizontal, 12)
            .padding(.vertical, 5)
            .background(LinearGradient(colors: [Brand.gold.opacity(0.30), goldLight.opacity(0.45)], startPoint: .leading, endPoint: .trailing), in: Capsule())
            .overlay(Capsule().strokeBorder(Brand.gold.opacity(0.45), lineWidth: 1))
    }
}

/// Выбор голоса азана: нажатие на кнопку ▶ — прослушать (тихо), на строку — выбрать.
struct AdhanVoiceList: View {
    @Binding var selected: String
    @ObservedObject private var preview = AdhanPreview.shared

    var body: some View {
        VStack(spacing: 0) {
            ForEach(AdhanVoice.all) { voice in
                HStack(spacing: 12) {
                    Button { selected = voice.id } label: {
                        HStack(spacing: 14) {
                            Image(systemName: selected == voice.id ? "largecircle.fill.circle" : "circle")
                                .font(.title3)
                                .foregroundStyle(selected == voice.id ? Brand.primary : Brand.secondaryText)
                            Text(L("adhan_voice", voice.number)).foregroundStyle(.primary)
                            Spacer()
                        }
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    Button { preview.toggle(voice) } label: {
                        Image(systemName: preview.playing == voice.id ? "stop.fill" : "play.fill")
                            .font(.footnote.weight(.bold))
                            .foregroundStyle(.white)
                            .frame(width: 38, height: 38)
                            .background(LinearGradient(colors: [Color(hex: 0x1A7D63), Brand.emeraldDeep], startPoint: .topLeading, endPoint: .bottomTrailing), in: Circle())
                    }
                    .buttonStyle(PressScaleStyle(scale: 0.9))
                    .accessibilityLabel(L("adhan_preview"))
                }
                .padding(.vertical, 8)
            }
        }
        .onDisappear { preview.stop() }
    }
}
