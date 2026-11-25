import Foundation

/// The three Pokémon the prototype tries, hardcoded instead of the full catalog.
///
/// URLs follow `spriteUrls()` in `web/src/core/catalog.ts`: the PokeAPI sprites repo pinned to
/// the same commit the apps use, jsDelivr first and raw GitHub as the fallback. Nothing is
/// bundled; every sprite is fetched on first use and cached.
enum Subject: String, CaseIterable, Identifiable, Codable {
    case showdownPikachu
    case blackWhitePikachu
    case emeraldPikachu

    var id: String { rawValue }

    /// `spritesSha` in `app/src/main/assets/sets.json`.
    static let spritesSha = "c10459b9b0129eaca5c5d9b1cac65336debb1d08"

    var label: String {
        switch self {
        case .showdownPikachu: "Pikachu · Showdown"
        case .blackWhitePikachu: "Pikachu · Black / White"
        case .emeraldPikachu: "Pikachu · Emerald (still)"
        }
    }

    /// The `sets.json` id, which decides the idle style `auto` resolves to.
    var setId: String {
        switch self {
        case .showdownPikachu: "other_showdown"
        case .blackWhitePikachu: "versions_generation_v_black_white_animated"
        case .emeraldPikachu: "versions_generation_iii_emerald"
        }
    }

    /// True when the file itself is an animated GIF; false for stills that get idle movement.
    var animated: Bool { self != .emeraldPikachu }

    private var setPath: String {
        switch self {
        case .showdownPikachu: "sprites/pokemon/other/showdown"
        case .blackWhitePikachu: "sprites/pokemon/versions/generation-v/black-white/animated"
        case .emeraldPikachu: "sprites/pokemon/versions/generation-iii/emerald"
        }
    }

    private var ext: String { animated ? "gif" : "png" }

    static let pokemonId = 25

    /// Every set above has a `shiny` variant for #25.
    func urls(shiny: Bool) -> [URL] {
        let path = [setPath, shiny ? "shiny" : nil, "\(Self.pokemonId).\(ext)"]
            .compactMap { $0 }
            .joined(separator: "/")
        return [
            "https://cdn.jsdelivr.net/gh/PokeAPI/sprites@\(Self.spritesSha)/\(path)",
            "https://raw.githubusercontent.com/PokeAPI/sprites/\(Self.spritesSha)/\(path)",
        ].compactMap(URL.init(string:))
    }
}

/// Download-once, keep-forever cache, the same policy as the apps: the URLs are pinned to one
/// commit, so a cached file can never go stale. Each process (app, widget) has its own cache,
/// so the prototype needs no App Group.
enum SpriteCache {
    static func data(for subject: Subject, shiny: Bool) async throws -> Data {
        let file = directory.appendingPathComponent("\(subject.rawValue)\(shiny ? "-shiny" : "")")
        if let cached = try? Data(contentsOf: file) { return cached }
        var lastError: Error = URLError(.badURL)
        for url in subject.urls(shiny: shiny) {
            do {
                let (data, response) = try await URLSession.shared.data(from: url)
                guard (response as? HTTPURLResponse)?.statusCode == 200 else {
                    lastError = URLError(.badServerResponse)
                    continue
                }
                try? data.write(to: file, options: .atomic)
                return data
            } catch {
                lastError = error
            }
        }
        throw lastError
    }

    private static var directory: URL {
        let base = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
        let dir = base.appendingPathComponent("sprites", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }
}
