import SwiftUI

struct FocusSessionView: View {
    @AppStorage("focus_session_length") private var minutes = 25
    @AppStorage("focus_session_end") private var deadline = 0.0

    var body: some View {
        TimelineView(.periodic(from: .now, by: 1)) { tick in
            let remaining = max(0, Int(ceil(deadline - tick.date.timeIntervalSince1970)))
            Page {
                Card {
                    Text(FocusText("session")).font(.title3.weight(.semibold))
                    if remaining > 0 {
                        Text(String(format: "%d:%02d", remaining / 60, remaining % 60))
                            .font(.system(size: 44, weight: .semibold).monospacedDigit())
                            .accessibilityIdentifier("focus_time_remaining")
                        Button(FocusText("end")) { deadline = 0 }
                            .accessibilityIdentifier("focus_end")
                    } else {
                        Picker(FocusText("session"), selection: $minutes) {
                            ForEach([15, 25, 45], id: \.self) { n in
                                Text(Duration.seconds(n * 60).formatted(.units(allowed: [.minutes], width: .abbreviated))).tag(n)
                            }
                        }.pickerStyle(.segmented).padding(.vertical, 10)
                        PrimaryButton(title: L("action_start")) {
                            deadline = Date.now.addingTimeInterval(Double(minutes) * 60).timeIntervalSince1970
                        }.accessibilityIdentifier("focus_start")
                    }
                }
                #if ZIKR_SCREEN_TIME
                ScreenTimeControls()
                #else
                Text(FocusText("timer_hint")).font(.subheadline).foregroundStyle(Brand.secondaryText)
                #endif
                NavigationLink(value: Route.quiz(category: nil)) {
                    Label(L("knowledge_start"), systemImage: "book")
                }
            }
        }
        .navigationTitle(L("focus_title"))
        .navigationBarTitleDisplayMode(.inline)
    }
}

func FocusText(_ key: String) -> String {
    L10n.bundle.localizedString(forKey: key, value: nil, table: "Focus")
}
