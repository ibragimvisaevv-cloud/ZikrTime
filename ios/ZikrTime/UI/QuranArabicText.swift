import SwiftUI
import UIKit

/// Keeps the publisher's letter order and maps each UTF-16 glyph back to its numbered verse and token.
struct QuranArabicText: UIViewRepresentable {
    let chapter: Int
    let verses: [QuranVerse]
    let activeAyah: Int?
    let highlightedWords: Set<Int>
    let followWord: Int?
    let fontSize: CGFloat
    let select: (Int, Int?) -> Void
    let showActions: (Int) -> Void

    struct Span {
        let ayah: Int
        let range: NSRange
        let arabicLength: Int
        let words: [Int: NSRange]
    }

    @MainActor final class Coordinator: NSObject {
        static let readers = NSHashTable<Coordinator>.weakObjects()
        var parent: QuranArabicText
        var spans: [Span] = []
        var lastFollow: [Int] = []
        var assetID: [Int] = []
        var previousText: String = ""
        var renderedAyah: Int?
        var renderedWords: Set<Int> = []
        var renderedSize: CGFloat = 0
        var renderedStyle: UIUserInterfaceStyle = .unspecified
        weak var view: UITextView?

        init(_ parent: QuranArabicText) {
            self.parent = parent
            super.init()
            Self.readers.add(self)
        }

        static func visibleText(chapter: Int) -> [Int: Range<Int>] {
            var result: [Int: Range<Int>] = [:]
            for item in readers.allObjects where item.parent.chapter == chapter {
                guard let text = item.view, text.window != nil else { continue }
                var ancestor = text.superview
                while let node = ancestor {
                    if let scroll = node as? UIScrollView, scroll.isScrollEnabled {
                        guard !scroll.isDragging, !scroll.isDecelerating else { return [:] }
                        let viewport = scroll.convert(scroll.bounds.inset(by: scroll.adjustedContentInset), to: text)
                        let intersection = viewport.intersection(text.bounds)
                        if !intersection.isNull, !intersection.isEmpty {
                            let manager = text.layoutManager
                            let glyphs = manager.glyphRange(forBoundingRect: intersection, in: text.textContainer)
                            manager.enumerateLineFragments(forGlyphRange: glyphs) { _, used, _, line, _ in
                                guard used.minY >= intersection.minY - 1, used.maxY <= intersection.maxY + 1 else { return }
                                let chars = manager.characterRange(forGlyphRange: line, actualGlyphRange: nil)
                                for span in item.spans {
                                    let covered = NSIntersectionRange(chars, NSRange(location: span.range.location, length: span.arabicLength))
                                    guard covered.length > 0 else { continue }
                                    let lo = covered.location - span.range.location
                                    let hi = lo + covered.length
                                    let old = result[span.ayah]
                                    result[span.ayah] = min(lo, old?.lowerBound ?? lo)..<max(hi, old?.upperBound ?? hi)
                                }
                            }
                        }
                        break
                    }
                    ancestor = node.superview
                }
            }
            return result
        }

        func target(at point: CGPoint) -> (Int, Int?)? {
            guard let view else { return nil }
            let manager = view.layoutManager
            let glyph = manager.glyphIndex(for: point, in: view.textContainer)
            guard glyph < manager.numberOfGlyphs,
                  manager.lineFragmentUsedRect(forGlyphAt: glyph, effectiveRange: nil).contains(point) else { return nil }
            let index = manager.characterIndexForGlyph(at: glyph)
            guard let span = spans.first(where: { NSLocationInRange(index, $0.range) }) else { return nil }
            return (span.ayah, span.words.first(where: { NSLocationInRange(index, $0.value) })?.key)
        }
        @objc func tap(_ gesture: UITapGestureRecognizer) {
            if let (ayah, word) = target(at: gesture.location(in: view)) { parent.select(ayah, word) }
        }
        @objc func hold(_ gesture: UILongPressGestureRecognizer) {
            guard gesture.state == .began, let (ayah, _) = target(at: gesture.location(in: view)) else { return }
            parent.showActions(ayah)
        }
    }

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    func makeUIView(context: Context) -> UITextView {
        let view = UITextView(usingTextLayoutManager: false)
        view.isScrollEnabled = false
        view.isEditable = false
        view.isSelectable = false
        view.isAccessibilityElement = true
        view.backgroundColor = .clear
        view.textContainerInset = .zero
        view.textContainer.lineFragmentPadding = 0
        view.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        context.coordinator.view = view
        let tap = UITapGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.tap(_:)))
        let hold = UILongPressGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.hold(_:)))
        tap.require(toFail: hold)
        view.addGestureRecognizer(tap)
        view.addGestureRecognizer(hold)
        return view
    }

    func updateUIView(_ view: UITextView, context: Context) {
        let state = context.coordinator
        state.parent = self
        let ids = verses.map(\.id)
        let dark = context.environment.colorScheme == .dark
        let style: UIUserInterfaceStyle = dark ? .dark : .light
        // Surah IDs restart from one. Compare the first verse too when navigation reuses a view.
        let first = verses.first?.arabic ?? ""
        if state.assetID != ids || state.previousText != first || state.renderedAyah != activeAyah ||
            state.renderedWords != highlightedWords || state.renderedSize != fontSize || state.renderedStyle != style {
            let body = NSMutableAttributedString(string: "")
            var spans: [Span] = []
            for verse in verses {
                if body.length > 0 { body.append(NSAttributedString(string: " ")) }
                let start = body.length
                body.append(NSAttributedString(string: verse.arabic))
                var words: [Int: NSRange] = [:]
                for token in QuranTimeline.tokens(verse.arabic) where token.spoken {
                    words[token.index] = NSRange(location: start + token.range.location, length: token.range.length)
                }
                let number = String(verse.id).compactMap(\.wholeNumberValue).map { String(UnicodeScalar(0x660 + $0)!) }.joined()
                body.append(NSAttributedString(string: "  ۝" + number))
                spans.append(Span(ayah: verse.id, range: NSRange(location: start, length: body.length - start),
                                  arabicLength: verse.arabic.utf16.count, words: words))
            }
            let paragraph = NSMutableParagraphStyle()
            paragraph.baseWritingDirection = .rightToLeft
            paragraph.alignment = .right
            paragraph.lineSpacing = 6
            let ink = dark ? UIColor(hex: 0xF5EEDC) : UIColor(hex: 0x302A1F)
            let all = NSRange(location: 0, length: body.length)
            body.addAttributes([.font: UIFont(name: "Amiri-Regular", size: fontSize) ?? UIFont(name: "Amiri", size: fontSize) ?? UIFont.systemFont(ofSize: fontSize),
                                .foregroundColor: ink, .paragraphStyle: paragraph], range: all)
            if let span = spans.first(where: { $0.ayah == activeAyah }) {
                body.addAttribute(.backgroundColor, value: UIColor(hex: dark ? 0x4D4430 : 0xF5E7C7), range: span.range)
                for word in highlightedWords {
                    if let range = span.words[word] {
                        body.addAttributes([.backgroundColor: UIColor(hex: 0xE2BC72), .foregroundColor: UIColor(hex: 0x282115)], range: range)
                    }
                }
            }
            state.spans = spans
            state.assetID = ids
            state.previousText = first
            state.renderedAyah = activeAyah
            state.renderedWords = highlightedWords
            state.renderedSize = fontSize
            state.renderedStyle = style
            view.attributedText = body
            view.accessibilityIdentifier = "quran_text_\(ids.first ?? 0)"
            view.accessibilityLabel = body.string
        }
        let focus = [activeAyah ?? 0, followWord ?? 0]
        guard focus != state.lastFollow else { return }
        state.lastFollow = focus
        guard let followWord, let span = state.spans.first(where: { $0.ayah == activeAyah }), let range = span.words[followWord] else { return }
        DispatchQueue.main.async { [weak view] in
            guard let view, view.window != nil, state.lastFollow == focus else { return }
            var ancestor = view.superview
            while let candidate = ancestor {
                if let scroller = candidate as? UIScrollView, scroller.isScrollEnabled {
                    let glyphs = view.layoutManager.glyphRange(forCharacterRange: range, actualCharacterRange: nil)
                    let rect = view.layoutManager.boundingRect(forGlyphRange: glyphs, in: view.textContainer)
                    let target = view.convert(rect, to: scroller).insetBy(dx: 0, dy: -8)
                    if !scroller.bounds.inset(by: scroller.adjustedContentInset).contains(target), !scroller.isDragging {
                        scroller.scrollRectToVisible(target, animated: true)
                    }
                    break
                }
                ancestor = candidate.superview
            }
        }
    }

    func sizeThatFits(_ proposal: ProposedViewSize, uiView: UITextView, context: Context) -> CGSize? {
        guard let width = proposal.width, width > 0 else { return nil }
        let size = uiView.sizeThatFits(CGSize(width: width, height: .greatestFiniteMagnitude))
        return CGSize(width: width, height: ceil(size.height))
    }
}
