import SwiftUI

struct AlarmSettingsView: View {
    @EnvironmentObject private var store: Store
    @EnvironmentObject private var online: OnlineTimes
    @State private var denied = false
    @State private var testScheduled = false

    var body: some View {
        let next = FajrAlarm.next()
        Page {
            AlarmHero(enabled: Binding(
                get: { store.alarmEnabled },
                set: { value in
                    store.alarmEnabled = value
                    changed()
                }
            ), next: next)

            if store.alarmEnabled {
                Card {
                    HStack {
                        Text(L("alarm_offset")).font(.body)
                        Spacer()
                        Text(store.alarmOffset == 0 ? L("alarm_offset_zero") : L("alarm_offset_value", store.alarmOffset))
                            .font(.headline)
                            .foregroundStyle(Brand.primary)
                    }
                    Slider(value: Binding(
                        get: { Double(store.alarmOffset) },
                        set: { store.alarmOffset = Int(($0 / 5).rounded()) * 5 }
                    ), in: 0...120, step: 5, onEditingChanged: { editing in
                        if !editing { changed() }
                    })
                    .tint(Brand.primary)

                    Text(L("alarm_days")).font(.body).padding(.top, 12)
                    HStack(spacing: 2) {
                        ForEach(weekOrder(), id: \.self) { day in
                            let on = store.alarmDays.contains(day)
                            Button {
                                if on { store.alarmDays.remove(day) } else { store.alarmDays.insert(day) }
                                changed()
                            } label: {
                                Text(L("weekdays_short_\(day - 1)"))
                                    .font(.footnote.weight(.semibold))
                                    .lineLimit(1)
                                    .minimumScaleFactor(0.6)
                                    .padding(.horizontal, 3)
                                    .frame(width: 40, height: 40)
                                    .foregroundStyle(on ? Color.white : Brand.secondaryText)
                                    .background(on ? Brand.primary : Brand.primarySoft, in: Circle())
                                    .frame(maxWidth: .infinity)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(.top, 10)
                }

                Card {
                    Text(L("alarm_sound")).font(.body)
                    AdhanVoiceList(selected: Binding(
                        get: { store.alarmVoice },
                        set: {
                            store.alarmVoice = $0
                            changed()
                        }
                    ))
                }

                Card {
                    Label(L(FajrAlarm.usesAlarmKit ? "alarm_ios_note" : "alarm_ios_fallback"), systemImage: "info.circle")
                        .font(.footnote)
                        .foregroundStyle(Brand.secondaryText)
                }

                if denied {
                    Card {
                        Text(L("alarm_permission_denied")).font(.footnote).foregroundStyle(.red)
                        TonalButton(title: L("action_open_settings")) { openSettings() }.padding(.top, 8)
                    }
                }

                TonalButton(title: L("alarm_test")) {
                    Task {
                        await FajrAlarm.scheduleTest()
                        testScheduled = true
                        denied = FajrAlarm.isDenied()
                    }
                }
                if testScheduled {
                    Text(L("alarm_test_toast")).font(.caption).foregroundStyle(Brand.primary).frame(maxWidth: .infinity)
                }
                Text(L("alarm_note")).font(.caption).foregroundStyle(Brand.secondaryText)
            }
        }
        .navigationTitle(L("alarm_title"))
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { denied = FajrAlarm.isDenied() }
        .onChange(of: online.version) { _, _ in changed() }
    }

    private func changed() {
        Task {
            await FajrAlarm.reschedule()
            denied = FajrAlarm.isDenied()
        }
    }

    /// Неделя начинается так, как принято в стране телефона.
    private func weekOrder() -> [Int] {
        let first = Calendar.current.firstWeekday // 1 — воскресенье
        let start = first == 1 ? 7 : first - 1     // в нашей нумерации 1 — понедельник
        return (0..<7).map { (start - 1 + $0) % 7 + 1 }
    }
}

/// Главная карточка: включатель и крупное время следующего будильника.
private struct AlarmHero: View {
    @Binding var enabled: Bool
    let next: (trigger: Date, fajr: Date)?

    var body: some View {
        GlassHero {
            VStack(alignment: .leading, spacing: 8) {
                HStack(spacing: 12) {
                    Image(systemName: "alarm.fill").font(.title2).foregroundStyle(Brand.gold)
                    Text(L("alarm_enable")).font(.headline).foregroundStyle(Brand.cream)
                    Spacer()
                    Toggle("", isOn: $enabled).labelsHidden().tint(Brand.gold)
                }
                if enabled, let next {
                    BigClock(date: next.trigger, size: 56, color: Brand.gold)
                    Text(Fmt.gregorian(LocalDay(next.trigger)) + " · " + L("alarm_fajr_at", Fmt.clock(next.fajr)))
                        .font(.subheadline)
                        .foregroundStyle(Brand.cream.opacity(0.85))
                } else {
                    Text(enabled ? L("alarm_next_none") : L("alarm_enable_hint"))
                        .font(.subheadline)
                        .foregroundStyle(Brand.cream.opacity(0.85))
                }
            }
            .padding(22)
        }
    }
}
