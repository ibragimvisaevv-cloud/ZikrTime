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
            assertEquals(32,bank.questions.count { it.source.originalReference!=null })
            bank.questions.forEach {
                assertNull("No documented review exists: $lang/${it.id}",it.source.reviewedOriginal)
                assertNull("No documented translation review exists: $lang/${it.id}",it.source.reviewedTranslation)
                assertEquals(it.contentVersion,it.source.contentVersion)
            }
        }
    }
}
