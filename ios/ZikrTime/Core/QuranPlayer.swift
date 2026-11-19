import AVFoundation
import Combine
import CryptoKit
import Foundation

@MainActor
final class QuranPlayer: ObservableObject {
    static let shared = QuranPlayer()
    @Published private(set) var reciter: QuranVoice
    @Published private(set) var chapter = 0
    @Published private(set) var ayah = 1
    @Published private(set) var word: Int?
    @Published private(set) var playing = false
    @Published private(set) var rate: Float
    @Published private(set) var repeats: Int
    @Published private(set) var continueAfter: Bool
    @Published private(set) var errorMessage: String?
    @Published private(set) var downloading: Set<Int> = []
    private let defaults = UserDefaults.standard
    private let engine = AVPlayer()
    private var timeline: QuranTimeline?
    private var timeObserver: Any?
    private var observation: NSKeyValueObservation?
    private var notifications: [AnyCancellable] = []
    private var gapNeighbor: Int?
    private var remaining = 1
    private var verseEnd: Int?
    private var seekSerial = 0
    private var seeking = false
    private var pendingPosition: Int?
    private var downloadKeys: Set<String> = []
    private var wasInterrupted = false
    private var selectedWord = true

    private init() {
        reciter = QuranVoice(rawValue: UserDefaults.standard.string(forKey: "quran.voice") ?? "") ?? .alafasy
        let value = UserDefaults.standard.float(forKey: "quran.rate")
        rate = value >= 0.5 && value <= 2 ? value : 1
        repeats = UserDefaults.standard.object(forKey: "quran.repeats") as? Int ?? 1
        continueAfter = UserDefaults.standard.object(forKey: "quran.continue") as? Bool ?? true
        remaining = repeats
        timeObserver = engine.addPeriodicTimeObserver(forInterval: CMTime(value: 1, timescale: 16), queue: .main) { [weak self] time in
            Task { @MainActor in self?.tick(time) }
        }
        NotificationCenter.default.publisher(for: AVAudioSession.interruptionNotification)
            .receive(on: RunLoop.main).sink { [weak self] note in self?.interrupted(note) }.store(in: &notifications)
        NotificationCenter.default.publisher(for: .AVPlayerItemDidPlayToEndTime)
            .receive(on: RunLoop.main).sink { [weak self] note in
                guard let self, let item = note.object as? AVPlayerItem, item === self.engine.currentItem,
                      self.playing, !self.seeking else { return }
                if self.restartIfRepeating() { return }
                self.playing = false
                self.word = nil
                self.remaining = self.repeats
                self.pendingPosition = self.timeline?.start(ayah: self.ayah, word: nil)
            }.store(in: &notifications)
    }

    private func interrupted(_ note: Notification) {
        guard let value = note.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt,
              let type = AVAudioSession.InterruptionType(rawValue: value) else { return }
        if type == .began {
            wasInterrupted = playing
            engine.pause()
            playing = false
        } else {
            let flags = (note.userInfo?[AVAudioSessionInterruptionOptionKey] as? UInt) ?? 0
            let resume = wasInterrupted && AVAudioSession.InterruptionOptions(rawValue: flags).contains(.shouldResume)
            wasInterrupted = false
            if resume { play() }
        }
    }

    func select(chapter newChapter: Int, ayah newAyah: Int, word newWord: Int? = nil, fromBeginning: Bool = false) {
        do {
            if newChapter != chapter || timeline == nil {
                let replacement = try QuranTimeline(root: QuranLibrary.resourceRoot(), voice: reciter, chapter: newChapter)
                clearItem()
                timeline = replacement
                chapter = newChapter
            }
            guard let start = timeline?.start(ayah: newAyah, word: newWord, fromBeginning: fromBeginning) else { throw QuranResourceError.invalid }
            errorMessage = nil
            ayah = newAyah
            word = newWord
            selectedWord = true
            gapNeighbor = nil
            remaining = repeats
            verseEnd = timeline?.verses.first(where: { $0.ayah == newAyah && $0.end > start })?.end
            seek(to: start)
        } catch { fail() }
    }

    func toggle() {
        if playing { engine.pause(); playing = false; wasInterrupted = false }
        else { play() }
    }

    private func play() {
        if chapter == 0 { select(chapter: 1, ayah: 1) }
        guard let timeline else { return }
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playback, mode: .default)
            try session.setActive(true)
            playing = true
            errorMessage = nil
            if let position = pendingPosition { seek(to: position) }
            else if engine.currentItem == nil {
                seek(to: timeline.start(ayah: ayah, word: word) ?? 0)
            } else if !seeking { engine.playImmediately(atRate: rate) }
        } catch { fail() }
    }

    private func clearItem() {
        seekSerial += 1
        seeking = false
        observation = nil
        engine.pause()
        engine.replaceCurrentItem(with: nil)
        pendingPosition = nil
    }

    private func seek(to ms: Int) {
        guard let timeline else { return }
        seekSerial += 1
        seeking = false
        pendingPosition = ms
        engine.pause()
        guard playing else { return }
        if engine.currentItem == nil {
            let local = offlineURL(voice: reciter, chapter: chapter)
            let item = AVPlayerItem(url: FileManager.default.fileExists(atPath: local.path) ? local : timeline.track.url)
            engine.replaceCurrentItem(with: item)
            observation = item.observe(\.status, options: [.initial, .new]) { [weak self] item, _ in
                Task { @MainActor in
                    guard let self, item === self.engine.currentItem else { return }
                    if item.status == .failed { self.fail() }
                    else if item.status == .readyToPlay { self.seekWhenReady() }
                }
            }
        }
        seekWhenReady()
    }

    private func seekWhenReady() {
        guard let target = pendingPosition, engine.currentItem?.status == .readyToPlay, playing else { return }
        pendingPosition = nil
        seeking = true
        let serial = seekSerial
        engine.seek(to: CMTime(value: Int64(target), timescale: 1000), toleranceBefore: .zero, toleranceAfter: .zero) { [weak self] done in
            Task { @MainActor in
                guard let self, serial == self.seekSerial else { return }
                self.seeking = false
                if done && self.playing { self.engine.playImmediately(atRate: self.rate) }
            }
        }
    }

    private func restartIfRepeating() -> Bool {
        guard remaining == 0 || remaining > 1, let start = timeline?.start(ayah: ayah, word: nil) else { return false }
        if remaining > 1 { remaining -= 1 }
        word = nil
        gapNeighbor = nil
        verseEnd = timeline?.verses.first(where: { $0.ayah == ayah && $0.end > start })?.end
        seek(to: start)
        return true
    }

    private func tick(_ time: CMTime) {
        guard playing, !seeking, pendingPosition == nil, engine.timeControlStatus == .playing,
              let timeline, time.seconds.isFinite else { return }
        // Ignore periodic callbacks queued for the previous item or seek.
        let actual = engine.currentTime().seconds
        guard actual.isFinite, abs(actual - time.seconds) < 0.25 else { return }
        let ms = Int(actual * 1000)
        if let end = verseEnd, ms >= end {
            if restartIfRepeating() { return }
            if !continueAfter {
                engine.pause()
                playing = false
                word = nil
                gapNeighbor = nil
                remaining = repeats
                pendingPosition = timeline.start(ayah: ayah, word: nil)
                return
            }
            verseEnd = nil
        }
        let verse = timeline.verse(at: ms)
        if let verse, verse.ayah > 0, verse.ayah != ayah {
            ayah = verse.ayah
            remaining = repeats
        }
        if let verse, verse.ayah == ayah { verseEnd = verse.end }
        let cursor = timeline.cursor(at: ms)
        let next = verse?.ayah == ayah && cursor?.word.ayah == ayah ? cursor : nil
        let nextWord = next?.word.word
        let nextGap = next?.gapNeighbor
        if word != nextWord || gapNeighbor != nextGap || selectedWord {
            selectedWord = false
            gapNeighbor = nextGap
            word = nextWord
        }
    }

    func highlightWords(arabic: String) -> Set<Int> {
        guard let word else { return [] }
        return timeline?.pair(arabic: arabic, ayah: ayah, word: word, neighbor: gapNeighbor, selected: selectedWord) ?? []
    }

    func setVoice(_ voice: QuranVoice) {
        guard voice != reciter else { return }
        let position = pendingPosition.map(Double.init) ?? engine.currentTime().seconds * 1000
        let inOpening = ayah == 1 && position < Double(timeline?.start(ayah: 1, word: nil) ?? 0)
        clearItem()
        timeline = nil
        reciter = voice
        defaults.set(voice.rawValue, forKey: "quran.voice")
        refreshDownloads()
        if chapter > 0 { select(chapter: chapter, ayah: ayah, word: word, fromBeginning: inOpening) }
    }
    func setRate(_ value: Float) {
        guard value >= 0.5, value <= 2 else { return }
        rate = value
        defaults.set(value, forKey: "quran.rate")
        if playing && !seeking && pendingPosition == nil { engine.rate = value }
    }
    func setRepeats(_ value: Int) {
        guard [0, 1, 3, 5, 10].contains(value) else { return }
        repeats = value
        remaining = value
        defaults.set(value, forKey: "quran.repeats")
    }
    func setContinue(_ value: Bool) {
        continueAfter = value
        defaults.set(value, forKey: "quran.continue")
    }
    func stop() {
        clearItem()
        playing = false
        wasInterrupted = false
        word = nil
        gapNeighbor = nil
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }
    private func fail() {
        stop()
        errorMessage = L("audio_player_failure")
    }

    private func offlineURL(voice: QuranVoice, chapter: Int) -> URL {
        FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("QuranAudio/\(voice.rawValue)/\(chapter).mp3")
    }
    func isDownloaded(_ chapter: Int) -> Bool {
        FileManager.default.fileExists(atPath: offlineURL(voice: reciter, chapter: chapter).path)
    }
    private func refreshDownloads() {
        downloading = Set((1...114).filter { downloadKeys.contains("\(reciter.rawValue)/\($0)") })
    }
    func download(_ chapter: Int) async {
        let voice = reciter
        let key = "\(voice.rawValue)/\(chapter)"
        guard !isDownloaded(chapter), downloadKeys.insert(key).inserted else { return }
        refreshDownloads()
        defer { downloadKeys.remove(key); refreshDownloads() }
        do {
            let track = try QuranTimeline(root: QuranLibrary.resourceRoot(), voice: voice, chapter: chapter).track
            let (temp, response) = try await URLSession.shared.download(from: track.url)
            defer { try? FileManager.default.removeItem(at: temp) }
            guard let http = response as? HTTPURLResponse, http.statusCode == 200 else { throw QuranResourceError.invalid }
            let destination = offlineURL(voice: voice, chapter: chapter)
            try await Task.detached(priority: .utility) {
                let file = try FileHandle(forReadingFrom: temp)
                defer { try? file.close() }
                guard try file.seekToEnd() == UInt64(track.size_bytes) else { throw QuranResourceError.invalid }
                if let hash = track.sha256 {
                    try file.seek(toOffset: 0)
                    var digest = SHA256()
                    while let data = try file.read(upToCount: 1024 * 1024), !data.isEmpty { digest.update(data: data) }
                    guard digest.finalize().map({ String(format: "%02x", $0) }).joined() == hash else { throw QuranResourceError.invalid }
                }
                var dir = destination.deletingLastPathComponent()
                try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
                var values = URLResourceValues()
                values.isExcludedFromBackup = true
                try dir.setResourceValues(values)
                try FileManager.default.moveItem(at: temp, to: destination)
            }.value
            errorMessage = nil
        } catch {
            errorMessage = L("qaudio_error_network")
        }
    }
    func removeDownload(_ chapter: Int) {
        objectWillChange.send()
        do { try FileManager.default.removeItem(at: offlineURL(voice: reciter, chapter: chapter)) }
        catch { errorMessage = L("qaudio_error_storage") }
    }
}
