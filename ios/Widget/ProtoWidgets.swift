import AppIntents
import SwiftUI
import WidgetKit

/// Three widgets, one per experiment, to place side by side on the Home Screen.
@main
struct ProtoWidgets: WidgetBundle {
    var body: some Widget {
        BaselineWidget()
        RotationWidget()
        FrameFontWidget()
    }
}

// MARK: - Shared configuration

enum SubjectChoice: String, AppEnum {
    case showdownPikachu, blackWhitePikachu, emeraldPikachu

    static let typeDisplayRepresentation: TypeDisplayRepresentation = "Sprite"
    static let caseDisplayRepresentations: [SubjectChoice: DisplayRepresentation] = [
        .showdownPikachu: "Pikachu · Showdown",
        .blackWhitePikachu: "Pikachu · Black / White",
        .emeraldPikachu: "Pikachu · Emerald (still)",
    ]

    var subject: Subject { Subject(rawValue: rawValue)! }
}

/// Whether the system's widget background stays, or is replaced with nothing, to see whether
/// a full-colour widget can sit straight on the wallpaper.
enum BackgroundChoice: String, AppEnum {
    case system, clear

    static let typeDisplayRepresentation: TypeDisplayRepresentation = "Background"
    static let caseDisplayRepresentations: [BackgroundChoice: DisplayRepresentation] = [
        .system: "System",
        .clear: "None",
    ]
}

extension View {
    @ViewBuilder
    func protoBackground(_ choice: BackgroundChoice) -> some View {
        switch choice {
        case .system: containerBackground(.fill.tertiary, for: .widget)
        case .clear: containerBackground(Color.clear, for: .widget)
        }
    }
}

/// A sprite's pixels drawn as crisp squares, scaled to fit.
struct PixelImage: View {
    var image: CGImage

    var body: some View {
        Image(decorative: image, scale: 1)
            .interpolation(.none)
            .resizable()
            .scaledToFit()
    }
}

/// The first frame of a sprite, for the widgets that draw a picture rather than fonts.
func firstFrame(_ subject: Subject, shiny: Bool) async -> CGImage? {
    guard let data = try? await SpriteCache.data(for: subject, shiny: shiny) else { return nil }
    return PixelArt.decode(data).first?.image
}

struct MessageView: View {
    var text: String

    var body: some View {
        Text(text).font(.caption).multilineTextAlignment(.center).foregroundStyle(.secondary)
    }
}
