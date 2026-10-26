import SwiftUI

struct RamadanView: View {
    @EnvironmentObject private var store: Store
    @EnvironmentObject private var online: OnlineTimes

    var body: some View {
        TimelineView(.periodic(from: .now, by: 1)) { timeline in
            let now = timeline.date
            let today = LocalDay(now)
            let period = Ramadan.currentOrNext(today: today, shift: store.ramadanShift)
            let times = PrayerSchedule.times(for: today)
            let fajr = times[.fajr] ?? now
            let maghrib = times[.maghrib] ?? now
            let tomorrow = PrayerSchedule.times(for: today.adding(days: 1))
            let iftarNext = now >= fajr && now < maghrib
            let target = now < fajr ? fajr : iftarNext ? maghrib : tomorrow[.fajr] ?? now
            Page {
                GlassHero {
                    VStack(alignment: .leading, spacing: 12) {
                        Label(L("ramadan_title"), systemImage: "moon.stars.fill").font(.title2.weight(.semibold))
                        if let p = period {
                            if let day = p.dayOf(today) {
                                Text(L("ramadan_day", day, p.days))
                            } else {
                                Text(L("ramadan_starts", Fmt.gregorian(p.start)))
                            }
                        }
                        Text(L(iftarNext ? "ramadan_to_iftar" : "ramadan_to_suhur_end")).font(.subheadline)
                        Text(target, style: .timer).font(.system(size: 42, weight: .light)).monospacedDigit().foregroundStyle(Brand.gold)
                        HStack {
                            VStack(alignment: .leading) {
                                Text(L("ramadan_suhur")).font(.caption)
                                Text(Fmt.clock(now >= maghrib ? tomorrow[.fajr] ?? fajr : fajr))
                            }
                            Spacer()
                            VStack(alignment: .trailing) {
                                Text(L("ramadan_iftar")).font(.caption)
                                Text(Fmt.clock(now >= maghrib ? tomorrow[.maghrib] ?? maghrib : maghrib))
                            }
                        }
                    }
                    .foregroundStyle(Brand.cream)
                    .padding(22)
                }
                if let p = period {
                    Card {
                        let marked = (0..<p.days).filter { store.ramadanFasts.contains(p.start.adding(days: $0).key) }.count
                        Text(L("ramadan_fasts_title")).font(.headline)
                        Text(L("ramadan_fasts_count", marked, p.days)).foregroundStyle(Brand.primary).padding(.top, 4)
                        Text(L("ramadan_fasts_hint")).font(.caption).padding(.vertical, 8)
                        LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 6), spacing: 12) {
                            ForEach(0..<p.days, id: \.self) { offset in
                                let date = p.start.adding(days: offset)
                                let marked = store.ramadanFasts.contains(date.key)
                                Button {
                                    if marked { store.ramadanFasts.remove(date.key) }
                                    else { store.ramadanFasts.insert(date.key) }
                                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                                } label: {
                                    Text("\(offset + 1)").font(.body.weight(.semibold))
                                        .frame(maxWidth: .infinity, minHeight: 40)
                                        .background(marked ? Brand.gold : Brand.primarySoft, in: Circle())
                                        .foregroundStyle(marked ? Color.black : Brand.primary)
                                        .opacity(date > today ? 0.35 : 1)
                                }
                                .disabled(date > today)
                                .accessibilityLabel(Fmt.gregorian(date))
                                .accessibilityAddTraits(marked ? [.isSelected] : [])
                            }
                        }
                    }
                }
                Card {
                    Text(L("ramadan_start_title")).font(.headline)
                    Picker(L("ramadan_start_title"), selection: $store.ramadanShift) {
                        Text(L("ramadan_shift_earlier")).tag(-1)
                        Text(L("ramadan_shift_none")).tag(0)
                        Text(L("ramadan_shift_later")).tag(1)
                    }.pickerStyle(.menu)
                }
                Text(L("ramadan_note")).font(.footnote).foregroundStyle(Brand.secondaryText)
            }
        }
        .navigationTitle(L("ramadan_title"))
        .navigationBarTitleDisplayMode(.inline)
    }
}
