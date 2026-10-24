import SwiftUI
import UIKit

/// Фирменные цвета — те же, что в Android-версии.
enum Brand {
    static let emerald = Color(hex: 0x0F5C4A)
    static let emeraldDeep = Color(hex: 0x083C30)
    static let emeraldNight = Color(hex: 0x04231C)
    static let gold = Color(hex: 0xE2B659)
    static let cream = Color(hex: 0xFFF6E0)
    static let onGold = Color(hex: 0x2B1E00)

    /// Фон экранов: тёплый светлый днём, глубокий зелёный ночью.
    static let background = Color(UIColor { $0.userInterfaceStyle == .dark ? UIColor(hex: 0x0B1210) : UIColor(hex: 0xF8F5EE) })
    static let card = Color(UIColor { $0.userInterfaceStyle == .dark ? UIColor(hex: 0x152420) : UIColor(hex: 0xFFFDF8) })
    static let cardBorder = Color(UIColor { $0.userInterfaceStyle == .dark ? UIColor(hex: 0x22352F) : UIColor(hex: 0xE6E0D2) })
    static let primary = Color(UIColor { $0.userInterfaceStyle == .dark ? UIColor(hex: 0x7FD6B9) : UIColor(hex: 0x0F5C4A) })
    static let primarySoft = Color(UIColor { $0.userInterfaceStyle == .dark ? UIColor(hex: 0x1D3A32) : UIColor(hex: 0xDDEFE7) })
    static let goldSoft = Color(UIColor { $0.userInterfaceStyle == .dark ? UIColor(hex: 0x3A3016) : UIColor(hex: 0xFBEFD2) })
    static let secondaryText = Color(UIColor { $0.userInterfaceStyle == .dark ? UIColor(hex: 0xA9B8B2) : UIColor(hex: 0x5F6B66) })

    static let heroGradient = LinearGradient(colors: [emerald, emeraldDeep], startPoint: .topLeading, endPoint: .bottomTrailing)
    static let nightGradient = LinearGradient(colors: [emeraldNight, emeraldDeep, emeraldNight], startPoint: .top, endPoint: .bottom)

    /// Арабский текст — шрифт Amiri (из общего файла amiri.ttf).
    static func arabic(_ size: CGFloat) -> Font { .custom("Amiri", size: size) }
}

extension Color {
    init(hex: UInt32) {
        self.init(
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255
        )
    }
}

extension UIColor {
    convenience init(hex: UInt32) {
        self.init(
            red: CGFloat((hex >> 16) & 0xFF) / 255,
            green: CGFloat((hex >> 8) & 0xFF) / 255,
            blue: CGFloat(hex & 0xFF) / 255,
            alpha: 1
        )
    }
}

/// Фоновый узор из восьмиконечных звёзд.
struct StarPattern: View {
    var color: Color
    var cell: CGFloat = 64

    var body: some View {
        Canvas { context, size in
            let r = cell * 0.32
            var y: CGFloat = 0
            var row = 0
            while y < size.height + cell {
                var x: CGFloat = row % 2 == 0 ? 0 : cell / 2
                while x < size.width + cell {
                    context.stroke(Self.star(center: CGPoint(x: x, y: y), radius: r), with: .color(color), lineWidth: 1.2)
                    x += cell
                }
                y += cell * 0.75
                row += 1
            }
        }
        .allowsHitTesting(false)
    }

    static func star(center c: CGPoint, radius r: CGFloat) -> Path {
        var path = Path()
        for square in 0..<2 {
            for i in 0..<4 {
                let a = (45.0 * Double(square) + 90.0 * Double(i) + 45.0) * .pi / 180
                let p = CGPoint(x: c.x + r * CGFloat(cos(a)), y: c.y + r * CGFloat(sin(a)))
                if i == 0 { path.move(to: p) } else { path.addLine(to: p) }
            }
            path.closeSubpath()
        }
        return path
    }
}
