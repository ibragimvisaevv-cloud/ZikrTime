import SwiftUI

/// Карточка: по умолчанию — стеклянная, с цветом — мягкий градиент того же цвета.
struct Card<Content: View>: View {
    var padding: CGFloat = 20
    var background: Color = Brand.card
    @ViewBuilder var content: Content

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 24, style: .continuous)
        let inner = VStack(alignment: .leading, spacing: 0) { content }
            .padding(padding)
            .frame(maxWidth: .infinity, alignment: .leading)
        if background == Brand.card {
            inner.glass()
        } else {
            inner
                .background(LinearGradient(colors: [background, background.opacity(0.85)], startPoint: .top, endPoint: .bottom), in: shape)
                .overlay(shape.strokeBorder(LinearGradient(colors: [.white.opacity(0.6), .white.opacity(0.05)], startPoint: .topLeading, endPoint: .bottomTrailing), lineWidth: 1))
                .shadow(color: background.opacity(0.5), radius: 10, x: 0, y: 4)
        }
    }
}

struct SectionLabel: View {
    let text: String
    var body: some View {
        Text(text)
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(Brand.primary)
            .padding(.leading, 4)
            .padding(.top, 4)
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// Значок в скруглённом квадрате.
struct IconBadge: View {
    let systemName: String
    var tint: Color = Brand.primary
    var background: Color = Brand.primarySoft
    var size: CGFloat = 40

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: size * 0.32, style: .continuous)
        Image(systemName: systemName)
            .font(.system(size: size * 0.45, weight: .semibold))
            .foregroundStyle(tint)
            .frame(width: size, height: size)
            .background(LinearGradient(colors: [background.opacity(0.55), background], startPoint: .topLeading, endPoint: .bottomTrailing), in: shape)
            .overlay(shape.strokeBorder(LinearGradient(colors: [.white.opacity(0.6), .clear], startPoint: .topLeading, endPoint: .bottomTrailing), lineWidth: 1))
    }
}

/// Строка настройки: значок, заголовок, пояснение и стрелка.
struct SettingRow: View {
    let title: String
    var subtitle: String?
    var icon: String?

    var body: some View {
        HStack(spacing: 14) {
            if let icon { IconBadge(systemName: icon) }
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.body).foregroundStyle(.primary)
                if let subtitle {
                    Text(subtitle).font(.footnote).foregroundStyle(Brand.secondaryText)
                }
            }
            Spacer(minLength: 8)
            Image(systemName: "chevron.right")
                .font(.footnote.weight(.semibold))
                .foregroundStyle(Brand.secondaryText)
        }
        .padding(.vertical, 8)
        .contentShape(Rectangle())
    }
}

struct ToggleRow: View {
    let title: String
    var subtitle: String?
    @Binding var isOn: Bool

    var body: some View {
        Toggle(isOn: $isOn) {
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.body)
                if let subtitle {
                    Text(subtitle).font(.footnote).foregroundStyle(Brand.secondaryText)
                }
            }
        }
        .tint(Brand.primary)
        .padding(.vertical, 6)
    }
}

struct PrimaryButton: View {
    let title: String
    var color: Color = Brand.emerald
    var textColor: Color = .white
    let action: () -> Void

    var body: some View {
        if color == Brand.emerald && textColor == .white {
            ShimmerButton(title: title, action: action)
        } else {
            Button(action: action) {
                Text(title)
                    .font(.headline)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity, minHeight: 54)
                    .padding(.horizontal, 12)
                    .foregroundStyle(textColor)
                    .background(color, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            }
            .buttonStyle(PressScaleStyle())
        }
    }
}

struct TonalButton: View {
    let title: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.subheadline.weight(.semibold))
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity, minHeight: 50)
                .padding(.horizontal, 12)
                .foregroundStyle(Brand.primary)
                .glass(cornerRadius: 16, shadow: 6)
        }
        .buttonStyle(PressScaleStyle())
    }
}

/// Кольцевой индикатор.
struct ProgressRing<Content: View>: View {
    var progress: Double
    var color: Color = Brand.primary
    var track: Color = Brand.primarySoft
    var lineWidth: CGFloat = 10
    @ViewBuilder var content: Content

    var body: some View {
        ZStack {
            Circle().stroke(track, lineWidth: lineWidth)
            Circle()
                .trim(from: 0, to: max(0, min(1, progress)))
                .stroke(color, style: StrokeStyle(lineWidth: lineWidth, lineCap: .round))
                .rotationEffect(.degrees(-90))
            content
        }
        .padding(lineWidth / 2)
    }
}

/// Крупное время: цифры большим шрифтом, «AM/PM» мелко рядом (если телефон в 12-часовом формате).
struct BigClock: View {
    let date: Date
    var size: CGFloat
    var color: Color
    var weight: Font.Weight = .light

    var body: some View {
        HStack(alignment: .lastTextBaseline, spacing: 6) {
            Text(Fmt.clockDigits(date))
                .font(.system(size: size, weight: weight).monospacedDigit())
            if let marker = Fmt.amPm(date) {
                Text(marker).font(.system(size: size * 0.32, weight: .medium))
            }
        }
        .foregroundStyle(color)
        .lineLimit(1)
        .minimumScaleFactor(0.6)
    }
}

/// Круглая отметка «сделано».
struct CheckCircle: View {
    let checked: Bool
    let enabled: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            ZStack {
                if checked {
                    Circle().fill(LinearGradient(colors: [Color(hex: 0x1A7D63), Brand.emeraldDeep], startPoint: .topLeading, endPoint: .bottomTrailing))
                    Image(systemName: "checkmark").font(.system(size: 12, weight: .bold)).foregroundStyle(.white)
                } else {
                    Circle().strokeBorder(Brand.secondaryText.opacity(enabled ? 0.9 : 0.3), lineWidth: 1.8)
                }
            }
            .frame(width: 26, height: 26)
            .frame(width: 44, height: 44)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }
}

/// Значок намаза (системные символы).
func prayerSymbol(_ p: Prayer) -> String {
    switch p {
    case .fajr: return "sun.haze"
    case .sunrise: return "sunrise"
    case .dhuhr: return "sun.max"
    case .asr: return "sun.min"
    case .maghrib: return "sunset"
    case .isha: return "moon.stars"
    }
}

/// Прокручиваемая страница с отступами.
struct Page<Content: View>: View {
    @ViewBuilder var content: Content

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) { content }
                .padding(.horizontal, 20)
                .padding(.vertical, 12)
        }
        .background(AuroraBackground())
    }
}
