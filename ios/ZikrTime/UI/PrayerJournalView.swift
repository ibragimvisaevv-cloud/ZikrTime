import SwiftUI

enum PrayerMark: String, CaseIterable {
    case done, madeUp = "made_up", missed, unmarked
    var label: String { L("journal_" + rawValue) }
}

struct PrayerJournalView: View {
    @EnvironmentObject private var store: Store
    @EnvironmentObject private var online: OnlineTimes
    @State private var selection: LocalDay?
    @State private var days = 7
    @State private var editing: Prayer?
    @State private var pendingLimit = 5

    var body: some View {
        TimelineView(.periodic(from: .now, by: 30)) { clock in
            let zone = PrayerSchedule.zone
            let today = LocalDay(clock.date, in: zone)
            let day = min(selection ?? today, today)
            let times = PrayerSchedule.times(for: day)
            let todayTimes = day == today ? times : PrayerSchedule.times(for: today)
            let summary = totals(today: today, times: todayTimes, now: clock.date)
            Page {
                Picker(L("journal_title"), selection: $days) {
                    Text(Fmt.days(7)).tag(7)
                    Text(Fmt.days(30)).tag(30)
                }.pickerStyle(.segmented)
                Card {
                    VStack(spacing: 10) {
                        ForEach(PrayerMark.allCases, id: \.self) { mark in
                            LabeledContent(mark.label, value: String(summary[mark, default: 0]))
                        }
                    }
                }
                Text(L("journal_note")).font(.footnote).foregroundStyle(Brand.secondaryText)
                DatePicker(L("journal_day"), selection: Binding(
                    get: { day.start(in: zone) },
                    set: { selection = LocalDay($0, in: zone) }
                ), in: ...clock.date, displayedComponents: .date)
                .environment(\.timeZone, zone)
                Card {
                    VStack(spacing: 0) {
                        ForEach(Prayer.obligatory, id: \.self) { prayer in
                            let available = (times[prayer] ?? .distantFuture) <= clock.date
                            Button { editing = prayer } label: {
                                HStack {
                                    Text(L(prayer.nameKey))
                                    Spacer()
                                    Text(available ? store.mark(day, prayer).label : L("journal_upcoming"))
                                        .font(.subheadline).foregroundStyle(Brand.secondaryText)
                                }.padding(.vertical, 10)
                            }
                            .disabled(!available).buttonStyle(.plain)
                            if prayer != .isha { Divider() }
                        }
                    }
                }
                SectionLabel(text: L("journal_pending"))
                let pending = store.journal.keys.sorted(by: >).compactMap { LocalDay(key: $0) }.filter {
                    $0 <= today && store.journal[$0.key]?.values.contains(PrayerMark.missed.rawValue) == true
                }
                if pending.isEmpty { Text(L("journal_no_pending")).font(.subheadline) }
                ForEach(pending.prefix(pendingLimit), id: \.self) { missedDay in
                    Card {
                        VStack(alignment: .leading, spacing: 10) {
                            Text(missedDay.start(in: zone), style: .date).environment(\.timeZone, zone)
                            ForEach(Prayer.obligatory.filter { store.mark(missedDay, $0) == .missed }, id: \.self) { prayer in
                                Button(L(prayer.nameKey)) { selection = missedDay; editing = prayer }
                            }
                        }.frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
                if pending.count > pendingLimit { Button(L("journal_more")) { pendingLimit += 10 } }
            }
            .confirmationDialog(editing.map { L($0.nameKey) } ?? L("journal_title"), isPresented: Binding(
                get: { editing != nil }, set: { if !$0 { editing = nil } }
            ), titleVisibility: .visible, presenting: editing) { prayer in
                ForEach(PrayerMark.allCases, id: \.self) { mark in
                    Button(mark.label) { store.setPrayerMark(day, prayer, mark); editing = nil }
                        .disabled((mark == .missed || mark == .madeUp) && store.mark(day, prayer) != mark &&
                                  !ended(prayer, day: day, times: times, now: clock.date))
                }
            }
        }.navigationTitle(L("journal_title")).navigationBarTitleDisplayMode(.inline)
    }

    private func totals(today: LocalDay, times: [Prayer: Date], now: Date) -> [PrayerMark: Int] {
        var counts: [PrayerMark: Int] = [:]
        for offset in 0..<days {
            let day = today.adding(days: -offset)
            for prayer in Prayer.obligatory where offset > 0 || (times[prayer] ?? .distantFuture) <= now {
                counts[store.mark(day, prayer), default: 0] += 1
            }
        }
        return counts
    }

    private func ended(_ prayer: Prayer, day: LocalDay, times: [Prayer: Date], now: Date) -> Bool {
        if prayer == .isha {
            return (PrayerSchedule.times(for: day.adding(days: 1))[.fajr] ?? .distantFuture) <= now
        }
        guard let next = Prayer(rawValue: prayer.rawValue + 1) else { return false }
        return (times[next] ?? .distantFuture) <= now
    }
}
