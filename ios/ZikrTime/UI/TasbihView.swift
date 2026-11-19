import AVFoundation
import SwiftUI
import WebKit

private let tasbihArabic = [
    "سُبْحَانَ اللَّهِ", "الْحَمْدُ لِلَّهِ", "اللَّهُ أَكْبَرُ",
    "لَا إِلَٰهَ إِلَّا اللَّهُ", "أَسْتَغْفِرُ اللَّهَ", "اللَّهُمَّ صَلِّ عَلَىٰ مُحَمَّدٍ"
]

struct TasbihView: View {
    @EnvironmentObject private var store: Store
    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.accessibilityReduceMotion) private var reducedMotion
    @AppStorage("tasbih_sound_enabled") private var sound = true
    @State private var sheetOpen = false
    @State private var choosing = false
    @State private var confirmReset = false
    @State private var skyVisible = false

    private var phrase: Int { min(max(0, store.tasbihPhrase), tasbihArabic.count - 1) }
    private static let sky = Bundle.main.url(forResource: "nebula", withExtension: "webp", subdirectory: "tasbih")
        .flatMap { UIImage(contentsOfFile: $0.path) }

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            if !skyVisible, let sky = Self.sky {
                GeometryReader { frame in
                    Image(uiImage: sky).resizable().scaledToFill()
                        .frame(width: frame.size.width, height: frame.size.height).clipped()
                }.ignoresSafeArea()
            }
            TasbihScene(store: store, phrase: phrase, sound: sound, active: scenePhase == .active && !sheetOpen,
                        reducedMotion: reducedMotion, quickSettings: { openSheet(phrases: true) },
                        backgroundReady: { skyVisible = true })
                .ignoresSafeArea()
                .accessibilityHidden(sheetOpen)
            VStack {
                HStack {
                    Button { dismiss() } label: { Image(systemName: "arrow.backward").frame(width: 44, height: 44) }
                        .accessibilityLabel(L("action_back"))
                    Spacer()
                    Button { openSheet(phrases: false) } label: { Image(systemName: "ellipsis").frame(width: 44, height: 44) }
                        .accessibilityLabel(L("action_open_settings"))
                        .accessibilityIdentifier("tasbih_settings")
                }.font(.title3.weight(.semibold)).padding(.horizontal, 8)
                Spacer()
            }.foregroundStyle(.white)
        }
        .toolbar(.hidden, for: .navigationBar, .tabBar)
        .statusBarHidden(true)
        .sheet(isPresented: $sheetOpen) { settings }
    }

    private func openSheet(phrases: Bool) {
        choosing = phrases
        sheetOpen = true
    }

    private var settings: some View {
        NavigationStack {
            Form {
                if choosing {
                    Section(footer: Text(L("tb_phrase_change_hint"))) {
                        ForEach(tasbihArabic.indices, id: \.self) { i in
                            Button {
                                if i != phrase { store.tasbihPhrase = i; store.tasbihCount = 0 }
                            } label: {
                                HStack {
                                    Text(L("zikr_\(i + 1)_name"))
                                    Spacer()
                                    if i == phrase { Image(systemName: "checkmark") }
                                }
                            }
                        }
                    }
                } else {
                    Button { choosing = true } label: {
                        LabeledContent(L("tb_choose_phrase"), value: L("zikr_\(phrase + 1)_name"))
                    }
                }
                Section {
                    Picker(L("tasbih_target"), selection: $store.tasbihTarget) {
                        ForEach([33, 99, 100, 0], id: \.self) { goal in
                            Text(goal == 0 ? "∞" : "\(goal)").tag(goal)
                        }
                    }.pickerStyle(.segmented).accessibilityIdentifier("tasbih_target")
                } header: { Text(L("tasbih_target")) }
                if !choosing {
                    Toggle(L("alarm_sound"), isOn: $sound)
                    Section(footer: Text(L("tb_gesture_hint"))) {
                        LabeledContent(L("tb_total_count"), value: "\(store.tasbihCount)")
                            .accessibilityElement(children: .ignore)
                            .accessibilityLabel(L("tb_total_count"))
                            .accessibilityValue("\(store.tasbihCount)")
                            .accessibilityIdentifier("tasbih_count")
                        if store.tasbihTarget > 0 {
                            LabeledContent(L("tb_completed_laps"), value: "\(store.tasbihCount / store.tasbihTarget)")
                        }
                        Text(L("tasbih_today", store.tasbihOn(.today())))
                    }
                    Button(L("action_reset"), role: .destructive) { confirmReset = true }
                }
            }
            .navigationTitle(choosing ? L("tb_choose_phrase") : L("tasbih_title"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) { Button(L("action_done")) { sheetOpen = false } }
            }
            .alert(L("action_reset"), isPresented: $confirmReset) {
                Button(L("action_close"), role: .cancel) {}
                Button(L("action_reset"), role: .destructive) { store.tasbihCount = 0; sheetOpen = false }
            } message: { Text(L("counter_reset_confirmation")) }
        }.presentationDetents([.medium, .large])
    }
}

@MainActor
private struct TasbihScene: UIViewRepresentable {
    let store: Store
    let phrase: Int
    let sound: Bool
    let active: Bool
    let reducedMotion: Bool
    let quickSettings: () -> Void
    let backgroundReady: () -> Void

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    func makeUIView(context: Context) -> WKWebView {
        let configuration = WKWebViewConfiguration()
        let scripts = configuration.userContentController
        scripts.add(context.coordinator, name: "tasbih")
        let bridge = """
        window.AndroidTasbih = Object.fromEntries(["tap","openSettings","strike","backgroundReady"].map(
            name => [name, () => window.webkit.messageHandlers.tasbih.postMessage(name)]));
        document.addEventListener("DOMContentLoaded", () => {
            const face = new FontFace("SceneAmiri", 'url("../amiri.ttf")');
            face.load().then(font => {document.fonts.add(font); window.dispatchEvent(new Event("resize"));}).catch(() => {});
        });
        """
        scripts.addUserScript(WKUserScript(source: bridge, injectionTime: .atDocumentStart, forMainFrameOnly: true))
        let web = WKWebView(frame: .zero, configuration: configuration)
        web.isOpaque = false
        web.backgroundColor = .clear
        web.scrollView.backgroundColor = .clear
        web.scrollView.isScrollEnabled = false
        web.scrollView.contentInsetAdjustmentBehavior = .never
        web.navigationDelegate = context.coordinator
        context.coordinator.web = web
        context.coordinator.loadScene()
        return web
    }

    func updateUIView(_ web: WKWebView, context: Context) {
        context.coordinator.host = self
        context.coordinator.update()
    }

    static func dismantleUIView(_ web: WKWebView, coordinator: Coordinator) {
        web.configuration.userContentController.removeScriptMessageHandler(forName: "tasbih")
        web.navigationDelegate = nil
        web.stopLoading()
        coordinator.ready = false
        coordinator.web = nil
    }

    @MainActor
    final class Coordinator: NSObject, WKScriptMessageHandler, WKNavigationDelegate {
        var host: TasbihScene
        weak var web: WKWebView?
        var ready = false
        private var configurationKey: ConfigurationKey?
        private var displayedCount = -1
        private var wasActive = false
        private var sample: AVAudioPlayer?

        private struct ConfigurationKey: Equatable {
            let phrase: Int
            let goal: Int
            let lang: String
            let reducedMotion: Bool
        }

        init(_ host: TasbihScene) {
            self.host = host
            super.init()
            if let url = Bundle.main.url(forResource: "tasbih_bead", withExtension: "wav") {
                sample = try? AVAudioPlayer(contentsOf: url)
                sample?.prepareToPlay()
            }
        }

        func loadScene() {
            ready = false
            if let file = Bundle.main.url(forResource: "scene", withExtension: "html", subdirectory: "tasbih"),
               let resources = Bundle.main.resourceURL {
                web?.loadFileURL(file, allowingReadAccessTo: resources)
            }
        }

        func webViewWebContentProcessDidTerminate(_ webView: WKWebView) { loadScene() }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            ready = true
            configurationKey = nil
            update()
        }

        func update() {
            guard ready, let web else { return }
            let store = host.store
            let key = ConfigurationKey(phrase: host.phrase, goal: store.tasbihTarget, lang: L10n.code,
                                       reducedMotion: host.reducedMotion)
            if configurationKey != key {
                configurationKey = key
                displayedCount = store.tasbihCount
                let config: [String: Any] = [
                    "arabic": tasbihArabic[key.phrase], "transcription": L("zikr_\(key.phrase + 1)_name"),
                    "translation": L("zikr_\(key.phrase + 1)_meaning"), "goal": key.goal,
                    "count": displayedCount, "lang": key.lang, "reducedMotion": key.reducedMotion,
                    "tapLabel": L("tb_gesture_hint"), "unlimitedLabel": L("tb_unlimited"),
                    "completedLabel": L("tb_cycle_complete"), "settingsLabel": L("action_open_settings")
                ]
                web.callAsyncJavaScript("window.tasbih.configure(config)", arguments: ["config": config],
                                        in: nil, in: .page, completionHandler: nil)
            } else if displayedCount != store.tasbihCount {
                displayedCount = store.tasbihCount
                web.evaluateJavaScript("window.tasbih.updateCount(\(displayedCount),\(key.goal))")
            }
            if wasActive && !host.active { web.evaluateJavaScript("window.tasbih.settle()"); sample?.stop() }
            wasActive = host.active
        }

        func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
            guard ready, message.frameInfo.isMainFrame, let action = message.body as? String else { return }
            if action == "backgroundReady" { host.backgroundReady(); return }
            guard host.active else { return }
            switch action {
            case "tap":
                host.store.addTasbih()
                displayedCount = host.store.tasbihCount
                let goal = host.store.tasbihTarget
                web?.evaluateJavaScript("window.tasbih.step(\(displayedCount),\(goal))")
                if goal > 0 && displayedCount % goal == 0 {
                    UINotificationFeedbackGenerator().notificationOccurred(.success)
                } else {
                    UIImpactFeedbackGenerator(style: displayedCount % 33 == 0 ? .medium : .light).impactOccurred()
                }
            case "openSettings": host.quickSettings()
            case "strike":
                if host.sound { sample?.currentTime = 0; sample?.play() }
            default: break
            }
        }
    }
}
