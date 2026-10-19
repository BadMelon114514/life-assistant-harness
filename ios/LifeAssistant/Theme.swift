import SwiftUI

/// 与 Web / 安卓端一致的配色（浅色 + 深色）。
struct Palette {
    let primary: Color
    let onPrimary: Color
    let primaryContainer: Color
    let background: Color
    let onBackground: Color
    let surface: Color
    let surfaceVariant: Color
    let onSurfaceVariant: Color
    let outline: Color
    let error: Color
    let success: Color
    let warning: Color
    let muted: Color

    static let light = Palette(
        primary: Color(hex: 0x4176E6),
        onPrimary: .white,
        primaryContainer: Color(hex: 0xE4EDFD),
        background: Color(hex: 0xFAFBFC),
        onBackground: Color(hex: 0x0F1115),
        surface: .white,
        surfaceVariant: Color(hex: 0xF5F6F7),
        onSurfaceVariant: Color(hex: 0x61666B),
        outline: Color(hex: 0x000000).opacity(0.12),
        error: Color(hex: 0xD92D20),
        success: Color(hex: 0x16A34A),
        warning: Color(hex: 0xF59E0B),
        muted: Color(hex: 0x8A8F98)
    )

    static let dark = Palette(
        primary: Color(hex: 0x6F9BF0),
        onPrimary: Color(hex: 0x0B1220),
        primaryContainer: Color(hex: 0x1F2C44),
        background: Color(hex: 0x0F1115),
        onBackground: Color(hex: 0xE8EAED),
        surface: Color(hex: 0x17191D),
        surfaceVariant: Color(hex: 0x1F2126),
        onSurfaceVariant: Color(hex: 0xADB2B8),
        outline: Color(hex: 0xFFFFFF).opacity(0.20),
        error: Color(hex: 0xFF8A80),
        success: Color(hex: 0x4ADE80),
        warning: Color(hex: 0xFBBF24),
        muted: Color(hex: 0x8A8F98)
    )

    static func of(_ scheme: ColorScheme) -> Palette {
        scheme == .dark ? .dark : .light
    }
}

extension Color {
    init(hex: UInt32) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255.0,
            green: Double((hex >> 8) & 0xFF) / 255.0,
            blue: Double(hex & 0xFF) / 255.0,
            opacity: 1.0
        )
    }
}

private struct PaletteKey: EnvironmentKey {
    static let defaultValue = Palette.light
}

extension EnvironmentValues {
    var palette: Palette {
        get { self[PaletteKey.self] }
        set { self[PaletteKey.self] = newValue }
    }
}

/// 根据系统/用户主题把配色注入环境。
struct ThemedRoot<Content: View>: View {
    @Environment(\.colorScheme) private var scheme
    @ViewBuilder let content: () -> Content

    var body: some View {
        content().environment(\.palette, Palette.of(scheme))
    }
}
