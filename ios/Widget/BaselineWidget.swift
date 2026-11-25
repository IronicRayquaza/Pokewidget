import AppIntents
import SwiftUI
import WidgetKit

/// Experiment 1: only what Apple supports. A still frame, and a tap that swaps to the shiny,
/// done by an App Intent (iOS 17+). This is what an iPhone widget looks like with no tricks.
struct BaselineWidget: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: "Baseline", intent: BaselineConfig.self, provider: BaselineProvider()) { entry in
            BaselineView(entry: entry).protoBackground(entry.background)
        }
        .configurationDisplayName("1 · Baseline")
        .description("A still sprite; tap for the shiny. Apple's supported API only.")
        .supportedFamilies([.systemSmall, .systemLarge])
    }
}

struct BaselineConfig: WidgetConfigurationIntent {
    static let title: LocalizedStringResource = "Baseline"

    @Parameter(title: "Sprite", default: .showdownPikachu)
    var sprite: SubjectChoice

    @Parameter(title: "Background", default: .system)
    var background: BackgroundChoice
}

/// The shiny flag lives in the widget extension's own defaults: the tap runs in its process.
struct ToggleShinyIntent: AppIntent {
    static let title: LocalizedStringResource = "Toggle shiny"
    static let key = "baselineShiny"

    func perform() async throws -> some IntentResult {
        let defaults = UserDefaults.standard
        defaults.set(!defaults.bool(forKey: Self.key), forKey: Self.key)
        return .result()
    }
}

struct BaselineEntry: TimelineEntry {
    var date = Date()
    var image: CGImage?
    var shiny = false
    var background: BackgroundChoice = .system
}

struct BaselineProvider: AppIntentTimelineProvider {
    func placeholder(in context: Context) -> BaselineEntry { BaselineEntry() }

    func snapshot(for configuration: BaselineConfig, in context: Context) async -> BaselineEntry {
        await entry(for: configuration)
    }

    func timeline(for configuration: BaselineConfig, in context: Context) async -> Timeline<BaselineEntry> {
        Timeline(entries: [await entry(for: configuration)], policy: .never)
    }

    private func entry(for configuration: BaselineConfig) async -> BaselineEntry {
        let shiny = UserDefaults.standard.bool(forKey: ToggleShinyIntent.key)
        return BaselineEntry(
            image: await firstFrame(configuration.sprite.subject, shiny: shiny),
            shiny: shiny,
            background: configuration.background
        )
    }
}

struct BaselineView: View {
    var entry: BaselineEntry

    var body: some View {
        Button(intent: ToggleShinyIntent()) {
            if let image = entry.image {
                PixelImage(image: image)
            } else {
                MessageView(text: "Couldn't load the sprite")
            }
        }
        .buttonStyle(.plain)
    }
}
