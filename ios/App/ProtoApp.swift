import Photos
import SwiftUI
import WidgetKit

@main
struct ProtoApp: App {
    var body: some Scene {
        WindowGroup { ContentView() }
    }
}

/// Pick one of the test sprites, see it move, and export it as a GIF: to Photos, where
/// Gifling or Video Widget can pick it up, or anywhere through the share sheet.
struct ContentView: View {
    @State private var subject: Subject = .showdownPikachu
    @State private var shiny = false
    @State private var idle: IdleStyle = Idle.defaultStyle
    @State private var frames: [TimedFrame] = []
    @State private var gifURL: URL?
    @State private var status = ""
    @State private var working = false

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Preview(frames: frames)
                        .frame(maxWidth: .infinity, minHeight: 240)
                        .listRowBackground(Color.clear)
                }
                Section("Sprite") {
                    Picker("Sprite", selection: $subject) {
                        ForEach(Subject.allCases) { Text($0.label).tag($0) }
                    }
                    Toggle("Shiny", isOn: $shiny)
                    if !subject.animated {
                        Picker("Idle movement", selection: $idle) {
                            ForEach(IdleStyle.allCases) { Text($0.label).tag($0) }
                        }
                    }
                }
                Section {
                    Button("Save GIF to Photos", action: saveToPhotos).disabled(gifURL == nil || working)
                    if let gifURL {
                        ShareLink("Share GIF…", item: gifURL)
                    }
                } header: {
                    Text("Export")
                } footer: {
                    Text("Then add it as a widget in Gifling or Video Widget.")
                }
                Section {
                    Button("Reload widgets") {
                        WidgetCenter.shared.reloadAllTimelines()
                        status = "Asked iOS to redraw every PokéWidget Proto widget."
                    }
                } footer: {
                    Text("Long-press the Home Screen → Edit → Add Widget → PokéWidget Proto. Each experiment is its own widget.")
                }
                if !status.isEmpty {
                    Section { Text(status).font(.footnote) }
                }
            }
            .navigationTitle("PokéWidget Proto")
        }
        .task(id: "\(subject.rawValue)-\(shiny)-\(idle.rawValue)") { await render() }
    }

    private func render() async {
        working = true
        defer { working = false }
        do {
            let data = try await SpriteCache.data(for: subject, shiny: shiny)
            let rendered = PixelArt.exportFrames(from: data, subject: subject, idle: idle)
            guard let gif = PixelArt.encodeGIF(rendered) else {
                status = "Couldn't make the GIF."
                return
            }
            let name = "pikachu-\(subject.rawValue)\(shiny ? "-shiny" : "")\(subject.animated ? "" : "-\(idle.rawValue)").gif"
            let url = FileManager.default.temporaryDirectory.appendingPathComponent(name)
            try gif.write(to: url, options: .atomic)
            frames = rendered
            gifURL = url
            status = "\(rendered.count) frames, \(rendered.first?.image.width ?? 0) px, \(gif.count / 1024) KB."
        } catch {
            status = "Couldn't download the sprite: \(error.localizedDescription)"
        }
    }

    private func saveToPhotos() {
        guard let gifURL, let data = try? Data(contentsOf: gifURL) else { return }
        Task {
            // Add-only: we never read the library, only put the GIF in it.
            guard await PHPhotoLibrary.requestAuthorization(for: .addOnly) == .authorized else {
                status = "Photos access was refused. Settings → Privacy & Security → Photos."
                return
            }
            do {
                // As data, not as a UIImage, so Photos keeps every frame.
                try await PHPhotoLibrary.shared().performChanges {
                    PHAssetCreationRequest.forAsset().addResource(with: .photo, data: data, options: nil)
                }
                status = "Saved to Photos."
            } catch {
                status = "Couldn't save: \(error.localizedDescription)"
            }
        }
    }
}

/// Plays the exported frames at their own delays, on a checkerboard so transparency shows.
struct Preview: View {
    var frames: [TimedFrame]

    var body: some View {
        TimelineView(.animation) { context in
            if let frame = frame(at: context.date) {
                Image(decorative: frame, scale: 1)
                    .interpolation(.none)
                    .resizable()
                    .scaledToFit()
                    .frame(maxHeight: 240)
            } else {
                ProgressView()
            }
        }
        .background(Checkerboard().opacity(0.15))
    }

    private func frame(at date: Date) -> CGImage? {
        let total = frames.reduce(0) { $0 + $1.delay }
        guard total > 0 else { return frames.first?.image }
        var t = date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: total)
        for frame in frames {
            if t < frame.delay { return frame.image }
            t -= frame.delay
        }
        return frames.last?.image
    }
}

struct Checkerboard: View {
    var body: some View {
        Canvas { context, size in
            let cell: CGFloat = 12
            for row in 0 ..< Int(size.height / cell) + 1 {
                for column in 0 ..< Int(size.width / cell) + 1 where (row + column).isMultiple(of: 2) {
                    context.fill(Path(CGRect(x: CGFloat(column) * cell, y: CGFloat(row) * cell, width: cell, height: cell)),
                                 with: .color(.gray))
                }
            }
        }
    }
}
