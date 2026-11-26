import AppIntents
import SwiftUI
import WidgetKit

/// Experiment 3: idle movement from a private, continuously rotating effect.
///
/// SwiftUI's `_clockHandRotationEffect` turns a view like a clock hand, forever, with no
/// updates from us. One rotation spins the sprite, which is useless; three nested ones make it
/// glide. A point turning at +ω around a point turning at -ω traces an ellipse (two equal
/// circles cancel into a straight line, Tusi's couple), and a third rotation undoes the spin so
/// the sprite stays upright:
///
///     position(t) = A·e^{iωt} + B·e^{-iωt}      orientation = ω - 2ω + ω = 0
///
/// So Bob (vertical line), Sway (horizontal line) and Hover (oval) are possible. Breathe and
/// Swell change the sprite's scale, which no rotation can do. The motion is a smooth sine, not
/// the held poses of `Idle.swift`, so on pixel art it may read slightly soft.
struct RotationWidget: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: "Rotation", intent: RotationConfig.self, provider: RotationProvider()) { entry in
            RotationView(entry: entry).protoBackground(entry.background)
        }
        .configurationDisplayName("3 · Idle by rotation")
        .description("A still sprite gliding through Bob, Sway or Hover, using a private SwiftUI effect.")
        .supportedFamilies([.systemSmall, .systemLarge])
    }
}

enum GlideChoice: String, AppEnum {
    case bob, sway, hover

    static let typeDisplayRepresentation: TypeDisplayRepresentation = "Movement"
    static let caseDisplayRepresentations: [GlideChoice: DisplayRepresentation] = [
        .bob: "Bob", .sway: "Sway", .hover: "Hover",
    ]

    /// The idle style this imitates, for its loop length and reach.
    var style: IdleStyle {
        switch self {
        case .bob: .bob
        case .sway: .sway
        case .hover: .hover
        }
    }

    /// The ellipse matching the style's poses, in source pixels: centre, plus the two circles.
    /// `x` = (a + b)·cos, `y` = (a - b)·sin for real a, b; a vertical line uses imaginary ones.
    var glide: Glide {
        switch self {
        // dy 0 … -2: a 1 px swing about -1.
        case .bob: Glide(centre: CGSize(width: 0, height: -1), a: CGSize(width: 0, height: 0.5), b: CGSize(width: 0, height: 0.5))
        // dx -2 … 2: a 2 px swing about 0.
        case .sway: Glide(centre: .zero, a: CGSize(width: 1, height: 0), b: CGSize(width: 1, height: 0))
        // dx -1 … 1, dy 0 … -3: semi-axes 1 and 1.5 about (0, -1.5).
        case .hover: Glide(centre: CGSize(width: 0, height: -1.5), a: CGSize(width: 1.25, height: 0), b: CGSize(width: -0.25, height: 0))
        }
    }
}

struct Glide {
    var centre: CGSize
    var a: CGSize
    var b: CGSize
}

struct RotationConfig: WidgetConfigurationIntent {
    static let title: LocalizedStringResource = "Idle by rotation"

    @Parameter(title: "Movement", default: .bob)
    var movement: GlideChoice

    @Parameter(title: "Background", default: .system)
    var background: BackgroundChoice
}

struct RotationEntry: TimelineEntry {
    var date = Date()
    var image: CGImage?
    var movement: GlideChoice = .bob
    var background: BackgroundChoice = .system
}

struct RotationProvider: AppIntentTimelineProvider {
    func placeholder(in context: Context) -> RotationEntry { RotationEntry() }

    func snapshot(for configuration: RotationConfig, in context: Context) async -> RotationEntry {
        await entry(for: configuration)
    }

    func timeline(for configuration: RotationConfig, in context: Context) async -> Timeline<RotationEntry> {
        Timeline(entries: [await entry(for: configuration)], policy: .never)
    }

    private func entry(for configuration: RotationConfig) async -> RotationEntry {
        RotationEntry(
            image: await firstFrame(.emeraldPikachu, shiny: false),
            movement: configuration.movement,
            background: configuration.background
        )
    }
}

struct RotationView: View {
    var entry: RotationEntry

    /// Says so when the build couldn't use the private API, so a still sprite isn't misread.
    @ViewBuilder private var rotationNote: some View {
        #if !PRIVATE_ROTATION
        Text("Built without the rotation API")
            .font(.caption2)
            .foregroundStyle(.secondary)
            .padding(4)
        #endif
    }

    var body: some View {
        GeometryReader { geometry in
            if let image = entry.image {
                let side = CGFloat(max(image.width, image.height) + 2 * PixelArt.idleMargin)
                let unit = min(geometry.size.width, geometry.size.height) / side
                let glide = entry.movement.glide
                let period = Double(entry.movement.style.intervalMs * Idle.frames(entry.movement.style).count) / 1000
                PixelImage(image: image)
                    .padding(CGFloat(PixelArt.idleMargin) * unit)
                    .glide(glide, unit: unit, period: period)
                    .offset(x: glide.centre.width * unit, y: glide.centre.height * unit)
                    .frame(width: geometry.size.width, height: geometry.size.height)
                    .overlay(alignment: .bottom) { rotationNote }
            } else {
                MessageView(text: "Couldn't load the sprite")
            }
        }
    }
}

extension View {
    /// The three nested rotations described above. Each rotates about the view's own frame,
    /// which `.offset` leaves where it was, so an offset view orbits the original centre.
    func glide(_ glide: Glide, unit: CGFloat, period: TimeInterval) -> some View {
        self
            .clockRotation(period: period)                                  // +ω: keeps it upright
            .offset(x: glide.b.width * unit, y: glide.b.height * unit)
            .scaleEffect(x: -1, y: 1)                                       // mirror, turn, mirror
            .clockRotation(period: period / 2)                              //   = -2ω
            .scaleEffect(x: -1, y: 1)
            .offset(x: glide.a.width * unit, y: glide.a.height * unit)
            .clockRotation(period: period)                                  // +ω
    }

    /// One full turn every `period` seconds, forever, driven by the system clock.
    @ViewBuilder
    func clockRotation(period: TimeInterval) -> some View {
        #if PRIVATE_ROTATION
        self._clockHandRotationEffect(.custom(period), in: .current, anchor: .center)
        #else
        self
        #endif
    }
}
