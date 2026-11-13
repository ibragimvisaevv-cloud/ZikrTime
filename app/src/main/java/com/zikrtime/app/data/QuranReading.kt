package com.zikrtime.app.data

/** Small layout blocks keep even Al-Baqarah responsive in one continuous scroll.
 * Source verse strings are never normalized or edited; only UI verse markers
 * are added between them. Blocks are not pages and require no next action.
 */
data class QuranTextRange(val ayah: Int, val start: Int, val arabicEnd: Int, val end: Int)

data class QuranReadingBlock(val verses: List<QuranAyah>) {
    val firstAyah: Int get() = verses.first().number
    val lastAyah: Int get() = verses.last().number
    val ranges: List<QuranTextRange>
    val text: String
    init {
        val result = StringBuilder()
        ranges = verses.mapIndexed { index, ayah ->
            if (index > 0) result.append("  ")
            val start = result.length
            result.append(ayah.arabic)
            val arabicEnd = result.length
            val number = ayah.number.toString().map { '٠' + (it - '0') }.joinToString("")
            result.append("  ۝").append(number)
            QuranTextRange(ayah.number, start, arabicEnd, result.length)
        }
        text = result.toString()
    }

    fun ayahAt(offset: Int): Int? = ranges.lastOrNull { offset >= it.start }?.ayah
}

object QuranReading {
    fun blocks(verses: List<QuranAyah>, anchor: Int = 1): List<QuranReadingBlock> {
        // Make the requested verse a block boundary so jumping/resuming never
        // lands several verses before it. All preceding verses remain available.
        val split = verses.indexOfFirst { it.number == anchor }.coerceAtLeast(0)
        return (verses.take(split).chunked(8) + verses.drop(split).chunked(8)).map(::QuranReadingBlock)
    }

    fun blockIndex(blocks: List<QuranReadingBlock>, ayah: Int): Int =
        blocks.indexOfFirst { ayah in it.firstAyah..it.lastAyah }.coerceAtLeast(0)
}

