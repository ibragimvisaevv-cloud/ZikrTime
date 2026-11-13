package com.zikrtime.app.data

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class QuranTopicsTest {
    companion object {
        private fun asset(name: String): File = listOf(File("src/main/assets/quran/$name"), File("app/src/main/assets/quran/$name"))
            .first { it.isFile }
        private val book by lazy { QuranContent.read { asset(it).readBytes() } }
    }

    @Test fun everyPassageExistsAndKeepsCanonicalTextAndOrder() {
        QuranTopics.requireValid(book)
        assertEquals(16, QuranTopics.all.size)
        assertEquals(64, QuranTopics.all.sumOf { it.passages.size })
        QuranTopics.all.forEach { topic ->
            assertEquals(4, topic.passages.size)
            topic.passages.forEach { passage ->
                val actual = passage.verses(book)
                assertEquals((passage.first..passage.last).toList(), actual.map { it.number })
                actual.forEach { verse ->
                    assertSame(book.ayahs.single { it.surah == verse.surah && it.number == verse.number }, verse)
                }
                assertEquals("https://quranenc.com/en/browse/russian_rwwad/${passage.surah}/${passage.first}", passage.sourceUrl)
            }
        }
    }

    @Test fun verseSearchFindsInteriorOfRangeAndDoesNotInventMatches() {
        val found = QuranTopics.search(" 2 : 155 ", book) { it.id }
        assertEquals(listOf("patience"), found.map { it.id })
        assertEquals(found, QuranTopics.search("2/155", book) { it.id })
        assertTrue(QuranTopics.search("2:287", book) { it.id }.isEmpty())
        assertTrue(QuranTopics.search("0:1", book) { it.id }.isEmpty())
        assertTrue(QuranTopics.search("115:1", book) { it.id }.isEmpty())
        assertTrue(QuranTopics.search("2:0", book) { it.id }.isEmpty())
    }

    @Test fun localizedWordsAreCaseAccentAndWhitespaceTolerant() {
        fun label(topic: QuranTopic) = if (topic.id == "patience") "Терпение в испытаниях" else ""
        assertEquals(listOf("patience"), QuranTopics.search(" ТЕРПЕНИЕ   испытаниях ", book, ::label).map { it.id })
        assertEquals(listOf("patience"), QuranTopics.search("sabir", book) { if (it.id == "patience") "Sabır" else "" }.map { it.id })
        assertEquals(listOf("knowledge"), QuranTopics.search("ilim", book) { if (it.id == "knowledge") "İlim" else "" }.map { it.id })
        assertTrue(QuranTopics.search("not-a-topic-0821", book, ::label).isEmpty())
        assertEquals(QuranTopics.all, QuranTopics.search("  ", book, ::label))
    }

    @Test fun surahSearchUsesBundledNamesAndExactReaderAnchor() {
        val name = book.surahs.single { it.id == 103 }.nameRu
        assertEquals(listOf("patience"), QuranTopics.search(name, book) { "" }.map { it.id })
        val passage = QuranTopics.find("patience")!!.passages.first()
        assertEquals(2, passage.surah)
        assertEquals(153, passage.first)
        assertEquals("2:153–157", passage.reference)
        val blocks = QuranReading.blocks(book.verses(passage.surah), passage.first)
        assertEquals(153, blocks[QuranReading.blockIndex(blocks, 153)].firstAyah)
        assertFalse(passage.contains(3, 153))
        assertFalse(passage.contains(2, 152))
        assertFalse(passage.contains(2, 158))
        assertNull(QuranTopics.find("unknown"))
    }

    @Test fun missingVersesAndInvalidRangesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { QuranTopicPassage(115, 1) }
        assertThrows(IllegalArgumentException::class.java) { QuranTopicPassage(2, 0) }
        assertThrows(IllegalArgumentException::class.java) { QuranTopicPassage(2, 10, 9) }
        assertThrows(IllegalArgumentException::class.java) {
            QuranTopics.requireValid(book.copy(ayahs = book.ayahs.filterNot { it.surah == 2 && it.number == 155 }))
        }
    }
}
