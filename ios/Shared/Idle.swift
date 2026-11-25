import Foundation

/// Generated idle movement for still sprites: a port of `web/src/core/idle.ts`, which is itself
/// a port of Android's `sprite/IdleAnimator.kt`. Same steps and timings, so a still sprite
/// moves the same way on all three. `Tests/IdleTests.swift` checks this table against the
/// TypeScript one.
///
/// The loop steps from held pose to held pose rather than tweening: on pixel art a smooth
/// tween reads as the image being resampled, a stepped one as the creature moving.
struct IdleFrame: Equatable, Codable {
    /// Scales in thousandths.
    var scaleXPermille: Int
    var scaleYPermille: Int
    /// Offsets in the sprite's own pixels; multiplied by the upscale when drawn. Negative y is up.
    var dxSource: Int
    var dySource: Int
}

enum IdleStyle: String, CaseIterable, Identifiable, Codable {
    case none, bob, breathe, sway, hover, settle, auto

    var id: String { rawValue }

    var label: String {
        switch self {
        case .none: "Still"
        case .bob: "Bob"
        case .breathe: "Breathe"
        case .sway: "Sway"
        case .hover: "Hover"
        case .settle: "Swell"
        case .auto: "Auto"
        }
    }

    /// How long each pose is held.
    var intervalMs: Int {
        switch self {
        case .none: 1000
        case .bob: 260
        case .breathe: 150
        case .sway: 170
        case .hover: 130
        case .settle: 170
        case .auto: 150
        }
    }
}

enum Idle {
    static let natural = 1000

    /// The style a still sprite gets when nobody has chosen one.
    static let defaultStyle: IdleStyle = .auto

    private static func frame(_ sx: Int = natural, _ sy: Int = natural, _ dx: Int = 0, _ dy: Int = 0) -> IdleFrame {
        IdleFrame(scaleXPermille: sx, scaleYPermille: sy, dxSource: dx, dySource: dy)
    }

    /// Every style except `auto`, which resolves to one of these.
    static func frames(_ style: IdleStyle) -> [IdleFrame] {
        switch style {
        case .none:
            [frame()]
        case .bob:
            // 0, -1, -2, -1: a smooth rise and fall with a slight pause at the bottom.
            [frame(1000, 1000, 0, 0), frame(1000, 1000, 0, -1), frame(1000, 1000, 0, -2), frame(1000, 1000, 0, -1)]
        case .breathe:
            // Volume-conserving squash and stretch, extremes held for two steps each.
            [frame(1000, 1000), frame(1015, 985), frame(1030, 970), frame(1030, 970), frame(1015, 985), frame(1000, 1000)]
        case .sway:
            // A weight shift, long on purpose: a fast sway reads as a shiver.
            [0, 1, 2, 2, 1, 0, -1, -2, -2, -1].map { frame(natural, natural, $0, 0) }
        case .hover:
            // A lazy oval rather than a straight line.
            [(0, 0), (1, -1), (1, -2), (0, -3), (-1, -3), (-1, -2), (0, -1)].map { frame(natural, natural, $0.0, $0.1) }
        case .settle:
            // A uniform swell with a slight rise: bigger, never a different shape.
            [frame(1000, 1000, 0, 0), frame(1010, 1010, 0, -1), frame(1020, 1020, 0, -2),
             frame(1020, 1020, 0, -2), frame(1010, 1010, 0, -1), frame(1000, 1000, 0, 0)]
        case .auto:
            frames(.breathe)
        }
    }

    /// Sets whose art is a rendered 3D model rather than a drawn sprite.
    static let renderSets: Set<String> = [
        "versions_generation_vi_x_y",
        "versions_generation_vi_omegaruby_alphasapphire",
        "versions_generation_vii_ultra_sun_ultra_moon",
        "versions_generation_viii_brilliant_diamond_shining_pearl",
        "versions_generation_ix_scarlet_violet",
        "versions_generation_ix_champions",
        "other_home",
        "other_official_artwork",
    ]

    /// Auto becomes a real style: a breath for pixel art, a swell for a render.
    static func resolve(_ style: IdleStyle, setId: String) -> IdleStyle {
        guard style == .auto else { return style }
        return renderSets.contains(setId) ? .settle : .breathe
    }

    static func frames(_ style: IdleStyle, setId: String) -> [IdleFrame] {
        frames(resolve(style, setId: setId))
    }
}
