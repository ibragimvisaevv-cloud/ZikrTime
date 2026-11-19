import SwiftUI
import UIKit

struct ReadingRemindersView: View {
    @ObservedObject private var reminders = ReadingReminders.shared
    @Environment(\.scenePhase) private var phase
    @State private var busy = false

    var body: some View {
        Page {
            Text(L("reading_hint")).font(.subheadline).foregroundStyle(Brand.secondaryText)
            ForEach(ReadingKind.allCases, id: \.self) { kind in
                Card {
                    VStack(alignment: .leading, spacing: 14) {
                        Toggle(kind.title, isOn: Binding(
                            get: { reminders.enabled.contains(kind.rawValue) },
                            set: { on in
                                busy = true
                                Task { await reminders.setEnabled(on, kind: kind); busy = false }
                            }
                        )).font(.headline).disabled(busy)
                        Text(kind.passage)
                        DatePicker(selection: Binding(
                            get: {
                                let value = reminders.minute(kind)
                                return Calendar.current.date(from: DateComponents(hour: value / 60, minute: value % 60)) ?? Date()
                            }, set: { date in
                                let parts = Calendar.current.dateComponents([.hour, .minute], from: date)
                                Task { await reminders.setMinute((parts.hour ?? 0) * 60 + (parts.minute ?? 0), kind: kind) }
                            }), displayedComponents: .hourAndMinute) {
                                Text(kind.title)
                            }.labelsHidden().accessibilityLabel(kind.title)
                        NavigationLink(L("reading_open"), value: Route.quranPassage(chapter: kind.chapter, ayah: kind.ayah))
                        Link(L(kind == .bedtime ? "reading_bukhari" : "reading_ifta"),
                             destination: URL(string: kind == .bedtime ? "https://sunnah.com/bukhari/40/11" :
                                "https://www.dar-alifta.org/en/fatwa/details/1964/reciting-quran-before-the-friday-prayer")!)
                            .font(.footnote)
                    }.frame(maxWidth: .infinity, alignment: .leading)
                }
            }
            if reminders.blocked {
                Text(L("reading_blocked"))
                Link(L("action_open_settings"), destination: URL(string: UIApplication.openSettingsURLString)!)
            }
            if let failure = reminders.failure { Text(failure).foregroundStyle(.red) }
        }
        .task { await reminders.refresh() }
        .onChange(of: phase) { _, value in if value == .active { Task { await reminders.refresh() } } }
        .navigationTitle(L("reading_title")).navigationBarTitleDisplayMode(.inline)
    }
}
