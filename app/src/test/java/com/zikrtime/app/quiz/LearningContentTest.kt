package com.zikrtime.app.quiz

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class LearningContentTest {
    @Test fun allLocalesShareLearningIdentityWithoutInventedReviewEvidence() {
        val langs=listOf("ru","en","uz","tg","kk","ky","az","tr")
        val master=QuestionBank.parse(File("src/main/assets/quiz/ru.json").readText())
        for(lang in langs) {
            val bank=QuestionBank.parse(File("src/main/assets/quiz/$lang.json").readText())
            assertEquals(master.questions.map { it.id to it.contentVersion },bank.questions.map { it.id to it.contentVersion })
            assertEquals(203, bank.questions.size)
            for (level in 1..3) assertTrue(bank.questions.count { it.difficulty == level } >= 40)
            val additions = bank.questions.filter { it.id.startsWith("l1_") || it.id.startsWith("l2_") || it.id.startsWith("l3_") }
            assertEquals(120, additions.size)
            additions.forEach {
                assertFalse("$lang/${it.id}: source reference", it.source.originalReference.isNullOrBlank())
                assertNotNull("$lang/${it.id}: source link", it.source.safeWebUrl)
            }
            bank.questions.forEach {
                assertNull("No documented review exists: $lang/${it.id}",it.source.reviewedOriginal)
                assertNull("No documented translation review exists: $lang/${it.id}",it.source.reviewedTranslation)
                assertEquals(it.contentVersion,it.source.contentVersion)
            }
        }
    }
}
