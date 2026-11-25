import XCTest

/// `Idle.swift` must move a still sprite exactly as the web and Android apps do. The fixture
/// is dumped from `web/src/core/idle.ts` by `tools/dump-idle.mjs`; regenerate it if that changes.
final class IdleTests: XCTestCase {
    private struct Entry: Decodable {
        var resolved: IdleStyle
        var intervalMs: Int
        var frames: [IdleFrame]
    }

    private func fixture() throws -> [String: [String: Entry]] {
        let url = try XCTUnwrap(Bundle(for: Self.self).url(forResource: "idle-fixture", withExtension: "json"))
        return try JSONDecoder().decode([String: [String: Entry]].self, from: Data(contentsOf: url))
    }

    func testMatchesTheWebTable() throws {
        let sets = try fixture()
        XCTAssertFalse(sets.isEmpty)
        for (setId, styles) in sets {
            XCTAssertEqual(Set(styles.keys), Set(IdleStyle.allCases.map(\.rawValue)), setId)
            for (name, expected) in styles {
                let style = try XCTUnwrap(IdleStyle(rawValue: name))
                let resolved = Idle.resolve(style, setId: setId)
                XCTAssertEqual(resolved, expected.resolved, "\(setId) \(name)")
                XCTAssertEqual(resolved.intervalMs, expected.intervalMs, "\(setId) \(name)")
                XCTAssertEqual(Idle.frames(style, setId: setId), expected.frames, "\(setId) \(name)")
            }
        }
    }
}
