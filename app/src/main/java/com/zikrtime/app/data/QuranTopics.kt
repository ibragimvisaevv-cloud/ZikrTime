package com.zikrtime.app.data

import java.util.Locale
import java.text.Normalizer

/** References only. Text always comes from the integrity-checked QuranBook. */
data class QuranTopicPassage(val surah: Int, val first: Int, val last: Int = first) {
    init { require(surah in 1..114 && first > 0 && last >= first) }
    val key: String get() = "${surah}_${first}_$last"
    val reference: String get() = "$surah:$first" + if (first == last) "" else "–$last"
    val sourceUrl: String get() = "https://quranenc.com/en/browse/russian_rwwad/$surah/$first"
    fun contains(surah: Int, ayah: Int): Boolean = this.surah == surah && ayah in first..last
    fun verses(book: QuranBook): List<QuranAyah> = book.verses(surah).filter { it.number in first..last }
}

data class QuranTopic(val id: String, val passages: List<QuranTopicPassage>)

/** An editorial reading index, not a tafsir or a claim of exhaustive coverage. */
object QuranTopics {
    private fun p(surah: Int, first: Int, last: Int = first) = QuranTopicPassage(surah, first, last)
    val all: List<QuranTopic> = listOf(
        QuranTopic("patience", listOf(p(2,153,157), p(3,200), p(16,126,128), p(103,1,3))),
        QuranTopic("gratitude", listOf(p(2,152), p(14,7,8), p(16,78), p(31,12))),
        QuranTopic("parents", listOf(p(17,23,24), p(31,14,15), p(46,15), p(29,8))),
        QuranTopic("mercy", listOf(p(7,156,157), p(21,107), p(39,53,54), p(90,12,17))),
        QuranTopic("repentance", listOf(p(4,17,18), p(25,68,71), p(39,53,54), p(66,8))),
        QuranTopic("honesty", listOf(p(9,119), p(33,70,71), p(22,30,31), p(83,1,6))),
        QuranTopic("justice", listOf(p(4,58), p(4,135), p(5,8), p(16,90))),
        QuranTopic("charity", listOf(p(2,261,264), p(2,267), p(2,271), p(76,8,10))),
        QuranTopic("knowledge", listOf(p(20,114), p(39,9), p(58,11), p(96,1,5))),
        QuranTopic("remembrance", listOf(p(13,27,28), p(33,41,42), p(2,152), p(7,205,206))),
        QuranTopic("trust", listOf(p(3,159,160), p(14,11,12), p(65,1,3), p(11,123))),
        QuranTopic("supplication", listOf(p(2,186), p(2,201), p(3,8,9), p(14,40,41))),
        QuranTopic("forgiveness", listOf(p(3,133,136), p(24,22), p(41,34,35), p(42,40,43))),
        QuranTopic("humility", listOf(p(25,63,66), p(31,18,19), p(17,37,38), p(49,11,12))),
        QuranTopic("family", listOf(p(4,36,38), p(17,26,29), p(30,21), p(49,10))),
        QuranTopic("reflection", listOf(p(3,190,194), p(30,20,25), p(67,3,4), p(88,17,20))),
    )

    fun find(id: String): QuranTopic? = all.firstOrNull { it.id == id }

    fun requireValid(book: QuranBook) {
        require(all.map { it.id }.distinct().size == all.size)
        all.forEach { topic ->
            require(topic.id.matches(Regex("[a-z]+")) && topic.passages.isNotEmpty())
            require(topic.passages.distinct().size == topic.passages.size)
            topic.passages.forEach { p ->
                require(book.surahs.any { it.id == p.surah && p.last <= it.ayahCount })
                require(p.verses(book).map { it.number } == (p.first..p.last).toList())
            }
        }
    }

    private fun normalized(value: String) = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").replace('ё', 'е').replace('ı', 'i').trim()

    /** Locale labels are supplied by the UI; references also match an ayah inside a range. */
    fun search(query: String, book: QuranBook, label: (QuranTopic) -> String): List<QuranTopic> {
        val value = normalized(query)
        if (value.isEmpty()) return all
        val reference = Regex("^(\\d{1,3})\\s*[:/]\\s*(\\d{1,3})$").matchEntire(value)
        if (reference != null) {
            val surah = reference.groupValues[1].toInt()
            val ayah = reference.groupValues[2].toInt()
            return all.filter { t -> t.passages.any { it.contains(surah, ayah) } }
        }
        val words = value.split(Regex("\\s+"))
        return all.filter { topic ->
            val names = topic.passages.mapNotNull { p -> book.surahs.firstOrNull { it.id == p.surah } }
                .joinToString(" ") { "${it.nameRu} ${it.nameLatin} ${it.nameArabic}" }
            val text = normalized(label(topic) + " " + names + " " + topic.passages.joinToString(" ") { it.reference })
            words.all { text.contains(it) }
        }
    }
}
