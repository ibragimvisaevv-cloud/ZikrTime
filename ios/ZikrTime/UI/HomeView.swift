import SwiftUI

struct HomeView: View {
    @EnvironmentObject private var store: Store
    @EnvironmentObject private var online: OnlineTimes
    @EnvironmentObject private var router: Router
    @State private var now = Date()

    var body: some View {
        let today = LocalDay(now)
        let times = PrayerSchedule.times(for: today)
        let next = PrayerSchedule.next(after: now)
        let current = currentPrayer(times)
        let prayed = store.prayedOn(today)

        Page {
            VStack(alignment: .leading, spacing: 4) {
                Text(L("greeting")).font(.title2.weight(.semibold))
                Text(Fmt.gregorian(today) + " · " + Fmt.hijri(today))
                    .font(.subheadline)
                    .foregroundStyle(Brand.secondaryText)
            }

            NavigationLink(value: Route.city) {
                HStack(spacing: 6) {
                    Image(systemName: "mappin.and.ellipse").foregroundStyle(Brand.primary)
                    Text(CityCatalog.shared.displayName(store.city, lang: L10n.code))
                        .font(.subheadline.weight(.medium))
                        .lineLimit(1)
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .glass(cornerRadius: 20, shadow: 6)
            }
            .buttonStyle(.plain)

            NextPrayerHero(prayer: next.prayer, at: next.time, previous: PrayerSchedule.previousStart(before: now), now: now)

            Card(padding: 12) {
                Text(L("home_today"))
                    .font(.headline)
                    .padding(.leading, 8)
                    .padding(.vertical, 6)
                ForEach(Prayer.allCases, id: \.self) { p in
                    PrayerRow(
                        prayer: p,
                        time: times[p].map(Fmt.clock) ?? "—",
                        isCurrent: p == current,
                        started: (times[p] ?? now) <= now,
                        done: prayed.contains(p),
                        toggle: { store.setPrayed(today, p, !prayed.contains(p)) }
                    )
                }
                Text(store.method == .moscowCathedral ? L("home_source_dumrf")
                     : L(PrayerSchedule.isOnline(today) ? "home_source_online" : "home_source_local", L(store.method.nameKey)))
                    .font(.caption)
                    .foregroundStyle(Brand.secondaryText)
                    .padding(.horizontal, 8)
                    .padding(.top, 8)
            }

            HStack(spacing: 12) {
                ActionTile(icon: "location.north.circle", title: L("qibla_title"),
                           value: "\(Int(Qibla.bearing(lat: store.city.latitude, lon: store.city.longitude).rounded()))°") {
                    router.tab = .qibla
                }
                ActionTile(icon: "circle.dotted", title: L("tasbih_title"), value: "\(store.tasbihOn(today))") {
                    router.prayerPath.append(.tasbih)
                }
                ActionTile(icon: "alarm", title: L("alarm_short"),
                           value: FajrAlarm.next().map { Fmt.clock($0.trigger) } ?? L("state_off")) {
                    router.prayerPath.append(.alarm)
                }
            }

            if today.weekday == 5 { FridayCard(day: today) }

            HStack(spacing: 12) {
                StreakCard(title: L("streak_knowledge"), value: Streaks.current(store.quizDaySet, today: today), hint: L("streak_knowledge_hint"))
                StreakCard(title: L("streak_prayers"), value: Streaks.current(store.prayerDays, today: today), hint: L("streak_prayers_hint"))
            }
        }
        .navigationTitle("")
        .toolbar(.hidden, for: .navigationBar)
        .task {
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 15_000_000_000)
                now = Date()
            }
        }
        .onChange(of: online.version) { _, _ in now = Date() }
    }

    /// Текущий намаз: последний начавшийся (утренний заканчивается с восходом).
    private func currentPrayer(_ times: [Prayer: Date]) -> Prayer? {
        guard let p = Prayer.obligatory.last(where: { (times[$0] ?? .distantFuture) <= now }) else { return nil }
        if p == .fajr, let sunrise = times[.sunrise], sunrise <= now { return nil }
        return p
    }
}

private struct NextPrayerHero: View {
    let prayer: Prayer
    let at: Date
    let previous: Date?
    let now: Date

    var body: some View {
        GlassHero {
            VStack(alignment: .leading, spacing: 6) {
                HStack(alignment: .center) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(L("home_next_prayer")).font(.subheadline.weight(.medium)).foregroundStyle(Brand.cream.opacity(0.8))
                        Text(L(prayer.nameKey)).font(.system(size: 34, weight: .semibold)).foregroundStyle(Brand.cream).lineLimit(1)
                    }
                    Spacer()
                    Image(systemName: prayerSymbol(prayer)).font(.system(size: 38)).foregroundStyle(Brand.gold)
                }
                ViewThatFits(in: .horizontal) {
                    HStack(alignment: .lastTextBaseline, spacing: 12) {
                        BigClock(date: at, size: 40, color: Brand.gold)
                        countdown
                    }
                    VStack(alignment: .leading, spacing: 2) {
                        BigClock(date: at, size: 40, color: Brand.gold)
                        countdown
                    }
                }
                if let previous {
                    let total = at.timeIntervalSince(previous)
                    let passed = now.timeIntervalSince(previous)
                    ProgressView(value: total > 0 ? max(0, min(1, passed / total)) : 0)
                        .tint(Brand.gold)
                        .padding(.top, 10)
                }
            }
            .padding(24)
        }
    }

    private var countdown: some View {
        let left = max(0, Int(at.timeIntervalSince(now) / 60)) + 1
        return Text(L("home_in_time", Fmt.duration(minutes: left)))
            .font(.headline)
            .foregroundStyle(Brand.cream)
            .lineLimit(1)
            .fixedSize()
    }
}

private struct PrayerRow: View {
    let prayer: Prayer
    let time: String
    let isCurrent: Bool
    let started: Bool
    let done: Bool
    let toggle: () -> Void

    var body: some View {
        HStack(spacing: 14) {
            IconBadge(systemName: prayerSymbol(prayer),
                      tint: prayer.isPrayer ? Brand.primary : Brand.secondaryText,
                      background: isCurrent ? Brand.card : Brand.primarySoft.opacity(0.6),
                      size: 38)
            VStack(alignment: .leading, spacing: 0) {
                Text(L(prayer.nameKey))
                    .font(.body.weight(isCurrent ? .semibold : .regular))
                    .foregroundStyle(prayer.isPrayer ? Color.primary : Brand.secondaryText)
                if isCurrent {
                    Text(L("home_now")).font(.caption2.weight(.semibold)).foregroundStyle(Brand.primary)
                }
            }
            Spacer()
            Text(time)
                .font(.headline.monospacedDigit())
                .foregroundStyle(prayer.isPrayer ? Color.primary : Brand.secondaryText)
            if prayer.isPrayer {
                CheckCircle(checked: done, enabled: started, action: toggle)
            } else {
                Color.clear.frame(width: 44, height: 44)
            }
        }
        .padding(.leading, 8)
        .padding(.vertical, 2)
        .background(isCurrent ? Brand.primarySoft : Color.clear, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }
}

private struct ActionTile: View {
    let icon: String
    let title: String
    let value: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Card(padding: 14) {
                IconBadge(systemName: icon, size: 36)
                Text(title).font(.footnote.weight(.medium)).lineLimit(1).padding(.top, 10)
                Text(value).font(.headline.monospacedDigit()).foregroundStyle(Brand.primary).lineLimit(1).minimumScaleFactor(0.7)
            }
        }
        .buttonStyle(PressScaleStyle())
    }
}

private struct StreakCard: View {
    let title: String
    let value: Int
    let hint: String

    var body: some View {
        Card(padding: 16, background: Brand.goldSoft) {
            Text(title).font(.footnote.weight(.semibold))
            Text(Fmt.days(value)).font(.title2.weight(.semibold))
            Text(hint).font(.caption).foregroundStyle(Brand.secondaryText)
        }
    }
}

/// В пятницу — карточка с аятом или хадисом о пятнице; нажатие — следующий текст.
private struct FridayCard: View {
    let day: LocalDay
    @State private var index = (Calendar.current.component(.hour, from: Date()) / 3) % FridayReminders.items.count

    var body: some View {
        let n = FridayReminders.items[index]
        Button {
            withAnimation(.easeInOut) { index = (index + 1) % FridayReminders.items.count }
        } label: {
            Card {
                HStack(spacing: 12) {
                    IconBadge(systemName: "book.closed", tint: Color(hex: 0x8A6414), background: Brand.gold.opacity(0.35))
                    Text(L("home_friday_title")).font(.headline).foregroundStyle(.primary)
                    Spacer()
                    Text("\(index + 1)/\(FridayReminders.items.count)").font(.caption.weight(.semibold)).foregroundStyle(Brand.secondaryText)
                }
                Text("«" + FridayReminders.text(n) + "»")
                    .font(.body)
                    .foregroundStyle(.primary)
                    .multilineTextAlignment(.leading)
                    .padding(.top, 12)
                Text(FridayReminders.source(n))
                    .font(.footnote.weight(.medium))
                    .foregroundStyle(Brand.primary)
                    .padding(.top, 8)
            }
        }
        .buttonStyle(PressScaleStyle(scale: 0.98))
    }
}
