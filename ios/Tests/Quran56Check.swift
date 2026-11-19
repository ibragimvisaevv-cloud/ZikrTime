import Foundation

@main
struct Quran56Check {
    static func main() throws {
        let dir = URL(fileURLWithPath: CommandLine.arguments[1], isDirectory: true)
        let book = try QuranLibrary(root: dir)
        precondition(book.surahs.reduce(0) { $0 + book.verses($1.id).count } == 6236)
        let first = book.verses(1)[0]
        precondition(!first.translation.isEmpty && QuranTimeline.tokens(first.arabic).filter(\.spoken).count == 4)
        let starts: [QuranVoice: Int] = [.dosari: 29490, .husary: 48510, .minshawi: 40100]
        for voice in QuranVoice.allCases {
            let timeline = try QuranTimeline(root: dir, voice: voice, chapter: 5)
            let start = timeline.start(ayah: 2, word: 1)!
            if let expected = starts[voice] { precondition(start == expected) }
            let cursor = timeline.cursor(at: start)
            precondition(cursor?.word.ayah == 2 && cursor?.word.word == 1)
            precondition(timeline.verse(at: start)?.ayah == 2)
            precondition(timeline.start(ayah: 1, word: nil, fromBeginning: true) == 0)
            precondition(timeline.start(ayah: 1, word: 1) == timeline.verses.first(where: { $0.ayah == 0 })?.start)
            precondition(timeline.pair(arabic: book.verses(5)[0].arabic, ayah: 1, word: 1, neighbor: nil, selected: true).contains(1))
            precondition(timeline.pair(arabic: book.verses(5)[1].arabic, ayah: 2, word: 1,
                                       neighbor: nil, selected: true).contains(1))
        }
        let repeated = try QuranTimeline(root: dir, voice: .dosari, chapter: 92)
        precondition(repeated.verse(at: 53943)?.ayah == 14)
        precondition(repeated.cursor(at: 53943)?.word.word == 1)
        print("Full text, four recordings, exact seek and repeat occurrence verified.")
    }
}
