package com.zikrtime.app.quiz

/** Stable persisted IDs: the choice describes the material, never a person's faith. */
enum class KnowledgeLevel(val id: Int) {
    BEGINNER(1), INTERMEDIATE(2), ADVANCED(3);

    companion object {
        fun fromId(id: Int?): KnowledgeLevel? = entries.firstOrNull { it.id == id }
    }
}
