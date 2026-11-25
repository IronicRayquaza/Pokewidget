import AppIntents
import SwiftUI
import WidgetKit

/// Experiment 2: real sprite frames, flipped by timers drawn in generated fonts.
///
/// The technique is Bryce Bostwick's WidgetAnimation (MIT),
/// https://github.com/brycebostwick/WidgetAnimation, which this view follows closely. The fonts
/// come from `tools/make-frame-fonts.py`; its docstring explains how a font maps a timer's
/// seconds to a frame. Font `j` of `N = 2 · fps` holds frames j, j + N, j + 2N, ...; each is
/// revealed one frame after the previous by a blinking mask, which is itself a timer in a font
/// whose glyph is a full square on even seconds and nothing on odd ones.
struct FrameFontWidget: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: "FrameFont", intent: FrameFontConfig.self, provider: FrameFontProvider()) { entry in
            FrameFontView(entry: entry).protoBackground(entry.background)
        }
        .configurationDisplayName("2 · Frames by timer fonts")
        .description("Real animation frames, flipped by timer text in generated fonts.")
        .supportedFamilies([.systemSmall, .systemLarge])
    }
}

/// What `make-frame-fonts.py` wrote next to the fonts.
struct FrameFontManifest: Decodable {
    struct Entry: Decodable {
        var id: String
        var label: String
        var periodSeconds: Int
        var fonts: Int
    }

    var fps: Int
    var blinkFont: String
    var subjects: [Entry]

    static let bundled: FrameFontManifest? = Bundle.main.url(forResource: "frame-fonts", withExtension: "json")
        .flatMap { try? Data(contentsOf: $0) }
        .flatMap { try? JSONDecoder().decode(FrameFontManifest.self, from: $0) }
}

enum FontSubjectChoice: String, AppEnum {
    case showdown25, emerald25

    static let typeDisplayRepresentation: TypeDisplayRepresentation = "Sprite"
    static let caseDisplayRepresentations: [FontSubjectChoice: DisplayRepresentation] = [
        .showdown25: "Pikachu · Showdown (animated)",
        .emerald25: "Pikachu · Emerald + Breathe",
    ]
}

struct FrameFontConfig: WidgetConfigurationIntent {
    static let title: LocalizedStringResource = "Frames by timer fonts"

    @Parameter(title: "Sprite", default: .showdown25)
    var sprite: FontSubjectChoice

    @Parameter(title: "Background", default: .system)
    var background: BackgroundChoice
}

struct FrameFontEntry: TimelineEntry {
    var date = Date()
    var subject: FontSubjectChoice = .showdown25
    var background: BackgroundChoice = .system
}

struct FrameFontProvider: AppIntentTimelineProvider {
    func placeholder(in context: Context) -> FrameFontEntry { FrameFontEntry() }

    func snapshot(for configuration: FrameFontConfig, in context: Context) async -> FrameFontEntry {
        FrameFontEntry(subject: configuration.sprite, background: configuration.background)
    }

    // One entry, never refreshed: the timers keep running on their own.
    func timeline(for configuration: FrameFontConfig, in context: Context) async -> Timeline<FrameFontEntry> {
        Timeline(entries: [FrameFontEntry(subject: configuration.sprite, background: configuration.background)], policy: .never)
    }
}

struct FrameFontView: View {
    var entry: FrameFontEntry

    var body: some View {
        if let manifest = FrameFontManifest.bundled,
           let subject = manifest.subjects.first(where: { $0.id == entry.subject.rawValue }) {
            GeometryReader { geometry in
                let size = min(geometry.size.width, geometry.size.height)
                FlipBook(prefix: "PWFrames-\(subject.id)-", fonts: subject.fonts, fps: manifest.fps,
                         blinkFont: manifest.blinkFont, size: size)
                    .frame(width: size, height: size)
                    .clipped()
                    .frame(width: geometry.size.width, height: geometry.size.height)
            }
        } else {
            MessageView(text: "No frame fonts: run tools/make-frame-fonts.py, then rebuild")
        }
    }
}

/// Bryce Bostwick's stack of timers, generalised from 16 fonts at 8 fps to `fonts` at `fps`.
struct FlipBook: View {
    var prefix: String
    var fonts: Int
    var fps: Int
    var blinkFont: String
    var size: CGFloat

    var body: some View {
        // Every timer counts from the same moment, a little in the past, so that after adding
        // the per-frame offsets they have all started.
        let reference = Date() - 60
        let frameDuration = 1 / Double(fps)
        let half = fonts / 2

        ZStack {
            ZStack {
                // Frame 0 is always there, underneath; everything else covers it in turn.
                timer(0, reference: reference, frameDuration: frameDuration)
                ForEach(1 ..< half, id: \.self) { i in
                    timer(i, reference: reference, frameDuration: frameDuration)
                        .mask(Blink(font: blinkFont, offset: -Double(i) * frameDuration).frame(width: size, height: size))
                }
            }
            // The second half is only on screen every other second, so the loop can fall back
            // to the first half.
            ZStack {
                ForEach(half ..< fonts, id: \.self) { i in
                    timer(i, reference: reference, frameDuration: frameDuration)
                        .mask(Blink(font: blinkFont, offset: -Double(i) * frameDuration).frame(width: size, height: size))
                }
            }
            .mask(Blink(font: blinkFont, offset: 1).frame(width: size, height: size))
        }
    }

    private func timer(_ i: Int, reference: Date, frameDuration: Double) -> some View {
        Text(reference + 1 + frameDuration * Double(i), style: .timer)
            .font(.custom("\(prefix)\(i)", size: size))
            .lastCharacterCentred(size: size)
    }
}

/// A square that is there for a second and gone for a second, forever.
struct Blink: View {
    static let reference = Date() - 60
    var font: String
    var offset: TimeInterval

    var body: some View {
        GeometryReader { geometry in
            let side = max(geometry.size.width, geometry.size.height)
            Text(Self.reference - offset, style: .timer)
                .font(.custom(font, size: side))
                .lastCharacterCentred(size: side, inGeometryReader: true)
        }
        .clipped()
    }
}

extension Text {
    /// A timer's text grows on the left ("9" → "1:00"), so give it room for nine characters,
    /// align it to the right, and shift it so the last character, the frame, is in view.
    func lastCharacterCentred(size: CGFloat, inGeometryReader: Bool = false) -> some View {
        self
            .frame(width: size * 9, height: size)
            .multilineTextAlignment(.trailing)
            .offset(x: -size * (inGeometryReader ? 8 : 4))
    }
}
