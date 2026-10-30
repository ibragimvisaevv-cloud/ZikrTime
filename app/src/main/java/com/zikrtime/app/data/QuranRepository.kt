package com.zikrtime.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Complete bundled text; no network, generated fallback, or partially trusted cache. */
object QuranRepository {
    private val lock = Mutex()
    @Volatile private var cached: QuranBook? = null

    suspend fun load(context: Context): QuranBook = withContext(Dispatchers.IO) {
        cached ?: lock.withLock {
            cached ?: QuranContent.read { path ->
                context.applicationContext.assets.open("quran/$path").use { it.readBytes() }
            }.also { cached = it }
        }
    }
}
