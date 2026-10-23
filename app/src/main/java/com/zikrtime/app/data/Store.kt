package com.zikrtime.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateMapOf
import com.zikrtime.app.prayer.AsrMadhab
import com.zikrtime.app.prayer.CalcMethod
import com.zikrtime.app.prayer.Prayer
import java.time.LocalDate

/**
 * Все настройки и прогресс пользователя. Хранятся в SharedPreferences на телефоне
 * и одновременно в наблюдаемой карте, чтобы экраны Compose обновлялись сами.
 * Вызывать только из главного потока.
 */
object Store {

    const val BONUS_MINUTES_PER_ROUND = 10
    const val MAX_BONUS_MINUTES_PER_DAY = 60
    const val CORRECT_ANSWERS_PER_ROUND = 3

    private lateinit var prefs: SharedPreferences
    private val cache = mutableStateMapOf<String, Any?>()

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences("zikrtime", Context.MODE_PRIVATE)
        cleanupOldDailyKeys()
        cache.putAll(prefs.all)
    }

    // ---------------- Общие настройки ----------------

    var onboarded: Boolean
        get() = bool(K_ONBOARDED, false)
        set(v) = put(K_ONBOARDED, v)

    val location: City
        get() = City(
            name = str(K_CITY, City.DEFAULT.name),
            latitude = float(K_LAT, City.DEFAULT.latitude.toFloat()).toDouble(),
            longitude = float(K_LON, City.DEFAULT.longitude.toFloat()).toDouble(),
        )

    fun setLocation(city: City) {
        put(K_CITY, city.name)
        put(K_LAT, city.latitude.toFloat())
        put(K_LON, city.longitude.toFloat())
    }

    var method: CalcMethod
        get() = runCatching { CalcMethod.valueOf(str(K_METHOD, CalcMethod.MWL.name)) }.getOrDefault(CalcMethod.MWL)
        set(v) = put(K_METHOD, v.name)

    var madhab: AsrMadhab
        get() = runCatching { AsrMadhab.valueOf(str(K_MADHAB, AsrMadhab.STANDARD.name)) }.getOrDefault(AsrMadhab.STANDARD)
        set(v) = put(K_MADHAB, v.name)

    var prayerNotifications: Boolean
        get() = bool(K_NOTIFY, true)
        set(v) = put(K_NOTIFY, v)

    // ---------------- Контроль соцсетей ----------------

    var blockingEnabled: Boolean
        get() = bool(K_BLOCKING, false)
        set(v) = put(K_BLOCKING, v)

    var dailyLimitMinutes: Int
        get() = int(K_LIMIT, 30)
        set(v) = put(K_LIMIT, v)

    var blockedPackages: Set<String>
        get() = strSet(K_PACKAGES)
        set(v) = put(K_PACKAGES, v)

    var appsInitialized: Boolean
        get() = bool(K_APPS_INIT, false)
        set(v) = put(K_APPS_INIT, v)

    var prayerLockEnabled: Boolean
        get() = bool(K_PRAYER_LOCK, true)
        set(v) = put(K_PRAYER_LOCK, v)

    var prayerLockMinutes: Int
        get() = int(K_PRAYER_LOCK_MIN, 15)
        set(v) = put(K_PRAYER_LOCK_MIN, v)

    fun bonusMinutes(date: LocalDate = LocalDate.now()): Int = int(K_BONUS + date, 0)

    /** Добавляет бонусные минуты за раунд викторины, но не больше дневного потолка. Возвращает, сколько добавлено. */
    fun addBonus(date: LocalDate = LocalDate.now()): Int {
        val current = bonusMinutes(date)
        val added = (MAX_BONUS_MINUTES_PER_DAY - current).coerceIn(0, BONUS_MINUTES_PER_ROUND)
        if (added > 0) put(K_BONUS + date, current + added)
        return added
    }

    fun allowedMinutes(date: LocalDate = LocalDate.now()): Int = dailyLimitMinutes + bonusMinutes(date)

    // ---------------- Намазы ----------------

    fun prayedOn(date: LocalDate): Set<Prayer> =
        strSet(K_PRAYED + date).mapNotNull { runCatching { Prayer.valueOf(it) }.getOrNull() }.toSet()

    fun setPrayed(date: LocalDate, prayer: Prayer, done: Boolean) {
        val updated = prayedOn(date).toMutableSet().apply { if (done) add(prayer) else remove(prayer) }
        put(K_PRAYED + date, updated.map { it.name }.toSet())

        // День засчитывается в серию, когда отмечены все пять намазов.
        val days = prayerDays.toMutableSet()
        if (updated.containsAll(Prayer.obligatory)) days.add(date) else days.remove(date)
        put(K_PRAYER_DAYS, Streaks.trim(days).map { it.toString() }.toSet())
    }

    /** Намазы, после которых пользователь нажал «Я совершил намаз» — блокировка для них снята. */
    fun lockDismissed(date: LocalDate): Set<Prayer> =
        strSet(K_LOCK_OFF + date).mapNotNull { runCatching { Prayer.valueOf(it) }.getOrNull() }.toSet()

    fun dismissLock(date: LocalDate, prayer: Prayer) {
        put(K_LOCK_OFF + date, (lockDismissed(date) + prayer).map { it.name }.toSet())
    }

    val prayerDays: Set<LocalDate>
        get() = dates(K_PRAYER_DAYS)

    // ---------------- Викторина ----------------

    val quizDays: Set<LocalDate>
        get() = dates(K_QUIZ_DAYS)

    val totalCorrect: Int get() = int(K_TOTAL_CORRECT, 0)
    val totalAnswered: Int get() = int(K_TOTAL_ANSWERED, 0)

    fun recordAnswer(correct: Boolean) {
        put(K_TOTAL_ANSWERED, totalAnswered + 1)
        if (correct) put(K_TOTAL_CORRECT, totalCorrect + 1)
    }

    fun recordRoundCompleted(date: LocalDate = LocalDate.now()) {
        put(K_QUIZ_DAYS, Streaks.trim(quizDays + date).map { it.toString() }.toSet())
    }

    // ---------------- Внутреннее ----------------

    private fun cleanupOldDailyKeys() {
        val cutoff = LocalDate.now().minusDays(7)
        val editor = prefs.edit()
        for (key in prefs.all.keys) {
            val prefix = listOf(K_BONUS, K_PRAYED, K_LOCK_OFF).firstOrNull { key.startsWith(it) } ?: continue
            val date = runCatching { LocalDate.parse(key.removePrefix(prefix)) }.getOrNull() ?: continue
            if (date.isBefore(cutoff)) editor.remove(key)
        }
        editor.apply()
    }

    private fun dates(key: String): Set<LocalDate> =
        strSet(key).mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet()

    private fun bool(key: String, def: Boolean) = cache[key] as? Boolean ?: def
    private fun int(key: String, def: Int) = cache[key] as? Int ?: def
    private fun float(key: String, def: Float) = cache[key] as? Float ?: def
    private fun str(key: String, def: String) = cache[key] as? String ?: def

    @Suppress("UNCHECKED_CAST")
    private fun strSet(key: String): Set<String> = (cache[key] as? Set<String>) ?: emptySet()

    private fun put(key: String, value: Any) {
        val editor = prefs.edit()
        @Suppress("UNCHECKED_CAST")
        when (value) {
            is Boolean -> editor.putBoolean(key, value)
            is Int -> editor.putInt(key, value)
            is Float -> editor.putFloat(key, value)
            is String -> editor.putString(key, value)
            is Set<*> -> editor.putStringSet(key, HashSet(value as Set<String>))
            else -> error("Неподдерживаемый тип для $key")
        }
        editor.apply()
        cache[key] = if (value is Set<*>) HashSet(value) else value
    }

    private const val K_ONBOARDED = "onboarded"
    private const val K_CITY = "city"
    private const val K_LAT = "lat"
    private const val K_LON = "lon"
    private const val K_METHOD = "method"
    private const val K_MADHAB = "madhab"
    private const val K_NOTIFY = "notify"
    private const val K_BLOCKING = "blocking"
    private const val K_LIMIT = "limit"
    private const val K_PACKAGES = "packages"
    private const val K_APPS_INIT = "apps_init"
    private const val K_PRAYER_LOCK = "prayer_lock"
    private const val K_PRAYER_LOCK_MIN = "prayer_lock_min"
    private const val K_BONUS = "bonus_"
    private const val K_PRAYED = "prayed_"
    private const val K_LOCK_OFF = "lockoff_"
    private const val K_PRAYER_DAYS = "prayer_days"
    private const val K_QUIZ_DAYS = "quiz_days"
    private const val K_TOTAL_CORRECT = "total_correct"
    private const val K_TOTAL_ANSWERED = "total_answered"
}
