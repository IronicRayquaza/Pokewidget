import CoreGraphics
import Foundation
import ImageIO
import UniformTypeIdentifiers

/// One frame of an animation and how long it stays up.
struct TimedFrame {
    var image: CGImage
    var delay: TimeInterval
}

enum PixelArt {
    /// Every frame of a GIF, or the single image of a PNG, already composited. ImageIO applies
    /// each GIF frame's disposal for us, so every frame here is the full picture.
    static func decode(_ data: Data) -> [TimedFrame] {
        guard let source = CGImageSourceCreateWithData(data as CFData, nil) else { return [] }
        return (0 ..< CGImageSourceGetCount(source)).compactMap { index in
            guard let image = CGImageSourceCreateImageAtIndex(source, index, nil) else { return nil }
            return TimedFrame(image: image, delay: delay(of: source, at: index))
        }
    }

    /// The GIF's own delay, with browsers' rule for the tiny ones: under 20 ms means 100 ms.
    private static func delay(of source: CGImageSource, at index: Int) -> TimeInterval {
        let properties = CGImageSourceCopyPropertiesAtIndex(source, index, nil) as? [CFString: Any]
        let gif = properties?[kCGImagePropertyGIFDictionary] as? [CFString: Any]
        let raw = (gif?[kCGImagePropertyGIFUnclampedDelayTime] as? Double)
            ?? (gif?[kCGImagePropertyGIFDelayTime] as? Double)
            ?? 0.1
        return raw < 0.02 ? 0.1 : raw
    }

    /// A transparent RGBA canvas.
    static func canvas(width: Int, height: Int) -> CGContext? {
        let context = CGContext(
            data: nil, width: width, height: height, bitsPerComponent: 8, bytesPerRow: 0,
            space: CGColorSpace(name: CGColorSpace.sRGB)!,
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        )
        // Nearest neighbour: every source pixel becomes a crisp square, never a blur.
        context?.interpolationQuality = .none
        context?.setShouldAntialias(false)
        return context
    }

    /// The upscale that brings the longest side closest to `target` without passing it.
    static func integerScale(for image: CGImage, target: Int) -> Int {
        max(1, target / max(image.width, image.height))
    }

    /// `image` drawn `scale` times bigger with square pixels.
    static func upscale(_ image: CGImage, by scale: Int) -> CGImage? {
        guard scale > 1 else { return image }
        let width = image.width * scale
        let height = image.height * scale
        guard let context = canvas(width: width, height: height) else { return nil }
        context.draw(image, in: CGRect(x: 0, y: 0, width: width, height: height))
        return context.makeImage()
    }

    /// Room around a still sprite so no idle pose is ever clipped, in source pixels.
    static let idleMargin = 4

    /// A still sprite's idle loop as frames, drawn the way `WidgetView.tsx` does it: translate
    /// by the pose's offset, then scale about the bottom centre of the sprite.
    static func idleLoop(_ still: CGImage, style: IdleStyle, setId: String, scale: Int) -> [TimedFrame] {
        let resolved = Idle.resolve(style, setId: setId)
        let poses = Idle.frames(resolved)
        let delay = Double(resolved.intervalMs) / 1000
        let margin = idleMargin * scale
        let spriteWidth = CGFloat(still.width * scale)
        let spriteHeight = CGFloat(still.height * scale)
        let width = Int(spriteWidth) + margin * 2
        let height = Int(spriteHeight) + margin * 2

        return poses.compactMap { pose in
            guard let context = canvas(width: width, height: height) else { return nil }
            // CoreGraphics has y up; the poses have negative y as up, so dy flips sign.
            let sx = CGFloat(pose.scaleXPermille) / CGFloat(Idle.natural)
            let sy = CGFloat(pose.scaleYPermille) / CGFloat(Idle.natural)
            let bottomCentre = CGPoint(x: CGFloat(margin) + spriteWidth / 2, y: CGFloat(margin))
            context.translateBy(x: CGFloat(pose.dxSource * scale), y: CGFloat(-pose.dySource * scale))
            context.translateBy(x: bottomCentre.x, y: bottomCentre.y)
            context.scaleBy(x: sx, y: sy)
            context.translateBy(x: -bottomCentre.x, y: -bottomCentre.y)
            context.draw(still, in: CGRect(x: CGFloat(margin), y: CGFloat(margin), width: spriteWidth, height: spriteHeight))
            return context.makeImage().map { TimedFrame(image: $0, delay: delay) }
        }
    }

    /// An endlessly looping GIF. GIF transparency is one bit, so pixels are either fully there
    /// or fully gone, which is what pixel art has anyway.
    static func encodeGIF(_ frames: [TimedFrame]) -> Data? {
        let data = NSMutableData()
        guard !frames.isEmpty,
              let destination = CGImageDestinationCreateWithData(data, UTType.gif.identifier as CFString, frames.count, nil)
        else { return nil }
        CGImageDestinationSetProperties(destination, [
            kCGImagePropertyGIFDictionary: [kCGImagePropertyGIFLoopCount: 0],
        ] as CFDictionary)
        for frame in frames {
            CGImageDestinationAddImage(destination, frame.image, [
                kCGImagePropertyGIFDictionary: [
                    kCGImagePropertyGIFDelayTime: frame.delay,
                    kCGImagePropertyGIFUnclampedDelayTime: frame.delay,
                ],
            ] as CFDictionary)
        }
        return CGImageDestinationFinalize(destination) ? data as Data : nil
    }

    /// What Export makes: the animation as-is for animated sprites, the idle loop for stills,
    /// upscaled so it stays crisp in a widget app (they scale it down, never up, ideally).
    static func exportFrames(from data: Data, subject: Subject, idle: IdleStyle, target: Int = 480) -> [TimedFrame] {
        let decoded = decode(data)
        guard let first = decoded.first else { return [] }
        let scale = integerScale(for: first.image, target: target)
        if subject.animated {
            return decoded.compactMap { frame in
                upscale(frame.image, by: scale).map { TimedFrame(image: $0, delay: frame.delay) }
            }
        }
        return idleLoop(first.image, style: idle, setId: subject.setId, scale: scale)
    }
}
