package com.zikrtime.app.data

/** Small layout blocks keep even Al-Baqarah responsive in one continuous scroll.
 * Source verse strings are never normalized or edited; only UI verse markers
 * are added between them. Blocks are not pages and require no next action.
 */
data class QuranReadingBlock(val verses: List<QuranAyah>) {
    val firstAyah: Int get() = verses.first().number
    val lastAyah: Int get() = verses.last().number
    val text: String = verses.joinToString("  ") { ayah ->
        val number = ayah.number.toString().map { '٠' + (it - '0') }.joinToString("")
        ayah.arabic + "  ۝" + number
    }
}

object QuranReading {
    fun blocks(verses: List<QuranAyah>): List<QuranReadingBlock> =
        verses.chunked(8).map(::QuranReadingBlock)

    fun blockIndex(blocks: List<QuranReadingBlock>, ayah: Int): Int =
        blocks.indexOfFirst { ayah in it.firstAyah..it.lastAyah }.coerceAtLeast(0)
}

