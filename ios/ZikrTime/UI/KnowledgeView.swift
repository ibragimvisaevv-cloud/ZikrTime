import SwiftUI

func categorySymbol(_ id: String) -> String {
    switch id {
    case "quran": return "book.closed"
    case "prophets": return "person.2"
    case "seerah": return "map"
    case "fiqh": return "checklist"
    case "arabic": return "character"
    case "ayah": return "text.quote"
    default: return "star"
    }
}

struct KnowledgeView: View {
    @EnvironmentObject private var store: Store
    private let bank = QuizBank.load(lang: L10n.code)

    var body: some View {
        let today = LocalDay.today()
        Page {
            VStack(alignment: .leading, spacing: 2) {
                Text(L("knowledge_title")).font(.largeTitle.weight(.bold))
                Text(L("knowledge_subtitle")).font(.subheadline).foregroundStyle(Brand.secondaryText)
            }

            Card(background: Brand.goldSoft) {
                HStack(spacing: 14) {
                    IconBadge(systemName: "sparkles", tint: Brand.onGold, background: Brand.gold.opacity(0.5), size: 44)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(L("streak_knowledge")).font(.subheadline.weight(.semibold))
                        Text(Fmt.days(Streaks.current(store.quizDaySet, today: today))).font(.title2.weight(.semibold))
                    }
                }
                NavigationLink(value: Route.quiz(category: nil)) {
                    Text(L("knowledge_start"))
                        .font(.headline)
                        .frame(maxWidth: .infinity, minHeight: 54)
                        .foregroundStyle(.white)
                        .background(Brand.emerald, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                }
                .buttonStyle(.plain)
                .padding(.top, 16)
            }

            SectionLabel(text: L("knowledge_topics"))
            LazyVGrid(columns: [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)], spacing: 12) {
                ForEach(bank.categories) { cat in
                    NavigationLink(value: Route.quiz(category: cat.id)) {
                        Card(padding: 16) {
                            IconBadge(systemName: categorySymbol(cat.id), size: 36)
                            Text(cat.name)
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(.primary)
                                .lineLimit(2)
                                .padding(.top, 10)
                            Text(L("knowledge_topic_progress", store.categoryCorrect[cat.id] ?? 0, bank.questions.filter { $0.category == cat.id }.count))
                                .font(.caption)
                                .foregroundStyle(Brand.secondaryText)
                        }
                    }
                    .buttonStyle(.plain)
                }
            }

            SectionLabel(text: L("knowledge_progress"))
            Card {
                StatLine(label: L("stat_correct"), value: "\(store.totalCorrect)")
                StatLine(label: L("stat_accuracy"), value: store.totalAnswered > 0 ? "\(store.totalCorrect * 100 / store.totalAnswered)%" : "—")
                StatLine(label: L("streak_knowledge"), value: Fmt.days(Streaks.current(store.quizDaySet, today: today)))
                StatLine(label: L("stat_best_streak"), value: Fmt.days(Streaks.best(store.quizDaySet)))
            }

            Text(L("knowledge_disclaimer")).font(.caption).foregroundStyle(Brand.secondaryText)
        }
        .toolbar(.hidden, for: .navigationBar)
    }
}

struct StatLine: View {
    let label: String
    let value: String

    var body: some View {
        HStack {
            Text(label).font(.body)
            Spacer()
            Text(value).font(.headline.monospacedDigit())
        }
        .padding(.vertical, 6)
    }
}

/// Раунд викторины: нужно дать 3 правильных ответа. После каждого ответа — короткая справка.
struct QuizView: View {
    let category: String?
    @EnvironmentObject private var store: Store
    @Environment(\.dismiss) private var dismiss
    @State private var bank = QuizBank.load(lang: L10n.code)
    @State private var queue: [Question] = []
    @State private var index = 0
    @State private var selected: Int?
    @State private var correct = 0
    @State private var answered = 0
    @State private var finished = false

    private let target = 3

    var body: some View {
        Page {
            if finished {
                result
            } else if queue.indices.contains(index) {
                QuizCard(
                    question: queue[index],
                    categoryName: bank.categoryName(queue[index].category),
                    progress: correct,
                    target: target,
                    selected: selected,
                    onSelect: answer,
                    onNext: next
                )
            }
        }
        .navigationTitle(category.map { bank.categoryName($0) } ?? L("knowledge_title"))
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            if queue.isEmpty { queue = bank.roundOrder(category: category, recent: store.recentQuestions) }
        }
    }

    private var result: some View {
        VStack(spacing: 16) {
            Image(systemName: "checkmark.seal.fill")
                .font(.system(size: 64))
                .foregroundStyle(Brand.gold)
                .padding(.top, 24)
            Text(L("quiz_mashallah")).font(.largeTitle.weight(.bold))
            Text(L("knowledge_topic_progress", correct, answered))
                .font(.title3)
                .foregroundStyle(Brand.secondaryText)
            PrimaryButton(title: L("quiz_again")) {
                queue = bank.roundOrder(category: category, recent: store.recentQuestions)
                index = 0
                selected = nil
                correct = 0
                answered = 0
                finished = false
            }
            .padding(.top, 8)
            TonalButton(title: L("action_done")) { dismiss() }
        }
        .frame(maxWidth: .infinity)
    }

    private func answer(_ option: Int) {
        guard selected == nil else { return }
        let q = queue[index]
        selected = option
        answered += 1
        let ok = option == q.answerIndex
        if ok { correct += 1 }
        store.recordAnswer(id: q.id, category: q.category, correct: ok)
        if correct >= target { store.recordRound() }
    }

    private func next() {
        if correct >= target {
            finished = true
            return
        }
        selected = nil
        index += 1
        if index >= queue.count {
            queue = bank.roundOrder(category: category, recent: store.recentQuestions)
            index = 0
        }
    }
}

/// Карточка вопроса (используется и в викторине, и в проверке «проснулся ли» на будильнике).
struct QuizCard: View {
    let question: Question
    let categoryName: String
    let progress: Int
    let target: Int
    let selected: Int?
    let onSelect: (Int) -> Void
    let onNext: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 6) {
                ForEach(0..<target, id: \.self) { i in
                    Capsule()
                        .fill(i < progress ? Brand.primary : Brand.primarySoft)
                        .frame(height: 6)
                }
                Text(L("quiz_progress", progress, target))
                    .font(.footnote.weight(.semibold).monospacedDigit())
                    .foregroundStyle(Brand.secondaryText)
                    .padding(.leading, 8)
                    .fixedSize()
            }

            Text(categoryName)
                .font(.caption.weight(.semibold))
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(Brand.goldSoft, in: Capsule())

            Text(question.text).font(.title3.weight(.semibold))

            if let arabic = question.arabic {
                Text(arabic)
                    .font(Brand.arabic(30))
                    .multilineTextAlignment(.center)
                    .environment(\.layoutDirection, .rightToLeft)
                    .frame(maxWidth: .infinity)
                    .padding(16)
                    .background(Brand.primarySoft, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
            }

            ForEach(Array(question.options.enumerated()), id: \.offset) { i, option in
                OptionButton(letter: ["A", "B", "C", "D"][min(i, 3)], text: option, state: state(for: i)) { onSelect(i) }
            }

            if let selected {
                InfoCard(question: question, wasCorrect: selected == question.answerIndex)
                PrimaryButton(title: progress >= target ? L("quiz_finish") : L("quiz_next"), action: onNext)
            }
        }
    }

    private func state(for i: Int) -> OptionButton.OptionState {
        guard let selected else { return .idle }
        if i == question.answerIndex { return .correct }
        if i == selected { return .wrong }
        return .dimmed
    }
}

private struct OptionButton: View {
    enum OptionState { case idle, correct, wrong, dimmed }

    let letter: String
    let text: String
    let state: OptionState
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 14) {
                Text(letter)
                    .font(.footnote.weight(.bold))
                    .frame(width: 30, height: 30)
                    .background(letterBackground, in: Circle())
                    .foregroundStyle(state == .correct || state == .wrong ? Color.white : Color.primary)
                if text.hasArabic {
                    Text(text)
                        .font(Brand.arabic(26))
                        .frame(maxWidth: .infinity)
                        .environment(\.layoutDirection, .rightToLeft)
                } else {
                    Text(text).font(.body).frame(maxWidth: .infinity, alignment: .leading)
                }
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .frame(maxWidth: .infinity, minHeight: 56)
            .background(background, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).strokeBorder(border, lineWidth: 1.5))
            .opacity(state == .dimmed ? 0.55 : 1)
        }
        .buttonStyle(.plain)
        .disabled(state != .idle)
    }

    private var background: Color {
        switch state {
        case .correct: return Color.green.opacity(0.14)
        case .wrong: return Color.red.opacity(0.12)
        default: return Brand.card
        }
    }

    private var border: Color {
        switch state {
        case .correct: return Color.green.opacity(0.8)
        case .wrong: return Color.red.opacity(0.7)
        default: return Brand.cardBorder
        }
    }

    private var letterBackground: Color {
        switch state {
        case .correct: return Color.green
        case .wrong: return Color.red
        default: return Brand.primarySoft
        }
    }
}

/// «Верно!» / «Не совсем», правильный ответ и «Полезно знать».
private struct InfoCard: View {
    let question: Question
    let wasCorrect: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Label(wasCorrect ? L("quiz_correct") : L("quiz_wrong"),
                  systemImage: wasCorrect ? "checkmark.circle.fill" : "xmark.circle.fill")
                .font(.headline)
                .foregroundStyle(wasCorrect ? Color.green : Color.red)
            if !wasCorrect {
                Text(L("quiz_correct_answer", question.options[question.answerIndex]))
                    .font(.subheadline.weight(.semibold))
            }
            Divider().padding(.vertical, 2)
            Label(L("quiz_info_title"), systemImage: "lightbulb")
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(Brand.primary)
            Text(question.info).font(.subheadline)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Brand.goldSoft, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
    }
}
