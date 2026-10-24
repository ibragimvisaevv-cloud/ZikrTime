package com.zikrtime.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateMapOf
import com.zikrtime.app.prayer.AsrMadhab
import com.zikrtime.app.prayer.CalcMethod
import com.zikrtime.app.prayer.Prayer
import java.time.DayOfWeek
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

    // ---------------- Общие ----------------

    var onboarded: Boolean
        get() = bool(K_ONBOARDED, false)
        set(v) = put(K_ONBOARDED, v)

    val location: City
        get() = City(
            id = (cache[K_CITY_ID] as? String)?.takeIf { it.isNotEmpty() }
                ?: if (cache[K_LAT] == null) Cities.DEFAULT.id else null,
            name = str(K_CITY, Cities.DEFAULT.name),
            latitude = float(K_LAT, Cities.DEFAULT.latitude.toFloat()).toDouble(),
            longitude = float(K_LON, Cities.DEFAULT.longitude.toFloat()).toDouble(),
        )

    fun setLocation(city: City) {
        put(K_CITY_ID, city.id ?: "")
        put(K_CITY, city.name)
        put(K_LAT, city.latitude.toFloat())
        put(K_LON, city.longitude.toFloat())
    }

    var method: CalcMethod
        get() = runCatching { CalcMethod.valueOf(str(K_METHOD, CalcMethod.RUSSIA.name)) }.getOrDefault(CalcMethod.RUSSIA)
        set(v) = put(K_METHOD, v.name)

    var madhab: AsrMadhab
        get() = runCatching { AsrMadhab.valueOf(str(K_MADHAB, AsrMadhab.STANDARD.name)) }.getOrDefault(AsrMadhab.STANDARD)
        set(v) = put(K_MADHAB, v.name)

    /** Брать время намаза из интернета (aladhan.com), если есть связь. */
    var onlineTimes: Boolean
        get() = bool(K_ONLINE, true)
        set(v) = put(K_ONLINE, v)

    /** Когда в последний раз удалось загрузить время из интернета (мс), 0 — ни разу. */
    var onlineUpdatedAt: Long
        get() = long(K_ONLINE_AT, 0L)
        set(v) = put(K_ONLINE_AT, v)

    /** Ручная поправка в минутах для каждого времени (−30…+30), чтобы совпасть с расписанием мечети. */
    fun adjustment(prayer: Prayer): Int = int(K_ADJ + prayer.name, 0)

    fun setAdjustment(prayer: Prayer, minutes: Int) = put(K_ADJ + prayer.name, minutes.coerceIn(-30, 30))

    // ---------------- Уведомления о намазе ----------------

    var prayerNotifications: Boolean
        get() = bool(K_NOTIFY, true)
        set(v) = put(K_NOTIFY, v)

    /** Проигрывать азан вместе с уведомлением о намазе. */
    var notificationAdhan: Boolean
        get() = bool(K_NOTIFY_ADHAN, false)
        set(v) = put(K_NOTIFY_ADHAN, v)

    /** Какая запись азана звучит в уведомлениях (id из AdhanSound.voices). */
    var notificationVoice: String
        get() = str(K_NOTIFY_VOICE, "adhan")
        set(v) = put(K_NOTIFY_VOICE, v)

    /** Пятничные напоминания (сура «Аль-Кахф», салават, час принятия дуа). */
    var fridayReminders: Boolean
        get() = bool(K_FRIDAY, true)
        set(v) = put(K_FRIDAY, v)

    // ---------------- Будильник на фаджр ----------------

    var alarmEnabled: Boolean
        get() = bool(K_ALARM_ON, false)
        set(v) = put(K_ALARM_ON, v)

    /** За сколько минут до фаджра будить (0 — ровно во время фаджра). */
    var alarmOffsetMinutes: Int
        get() = int(K_ALARM_OFFSET, 30)
        set(v) = put(K_ALARM_OFFSET, v.coerceIn(0, 120))

    var alarmDays: Set<DayOfWeek>
        get() = (cache[K_ALARM_DAYS] as? Set<*>)?.mapNotNull { (it as? String)?.toIntOrNull()?.let(DayOfWeek::of) }?.toSet()
            ?: DayOfWeek.values().toSet()
        set(v) = put(K_ALARM_DAYS, v.map { it.value.toString() }.toSet())

    /** "adhan", "adhan_2", "adhan_3" — записи азана, "system" — мелодия будильника телефона. */
    var alarmSound: String
        get() = str(K_ALARM_SOUND, "adhan")
        set(v) = put(K_ALARM_SOUND, v)

    var alarmMaxVolume: Boolean
        get() = bool(K_ALARM_MAXVOL, true)
        set(v) = put(K_ALARM_MAXVOL, v)

    var alarmVibrate: Boolean
        get() = bool(K_ALARM_VIBRATE, true)
        set(v) = put(K_ALARM_VIBRATE, v)

    /** Выключать будильник только правильным ответом на вопрос — чтобы точно проснуться. */
    var alarmWakeCheck: Boolean
        get() = bool(K_ALARM_WAKECHECK, false)
        set(v) = put(K_ALARM_WAKECHECK, v)

    var alarmSnoozeAllowed: Boolean
        get() = bool(K_ALARM_SNOOZE, false)
        set(v) = put(K_ALARM_SNOOZE, v)

    /** Время отложенного сигнала (мс) или 0. */
    var alarmSnoozeAt: Long
        get() = long(K_ALARM_SNOOZE_AT, 0L)
        set(v) = put(K_ALARM_SNOOZE_AT, v)

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

    fun categoryCorrect(category: String): Int = int(K_CAT_CORRECT + category, 0)

    /** Недавно заданные вопросы — чтобы не повторяться. */
    val recentQuestions: List<String>
        get() = str(K_RECENT_Q, "").split(',').filter { it.isNotEmpty() }

    fun recordAnswer(questionId: String, category: String, correct: Boolean) {
        put(K_TOTAL_ANSWERED, totalAnswered + 1)
        if (correct) {
            put(K_TOTAL_CORRECT, totalCorrect + 1)
            put(K_CAT_CORRECT + category, categoryCorrect(category) + 1)
        }
        put(K_RECENT_Q, (listOf(questionId) + recentQuestions.filter { it != questionId }).take(60).joinToString(","))
    }

    fun recordRoundCompleted(date: LocalDate = LocalDate.now()) {
        put(K_QUIZ_DAYS, Streaks.trim(quizDays + date).map { it.toString() }.toSet())
    }

    // ---------------- Тасбих ----------------

    var tasbihCount: Int
        get() = int(K_TASBIH_COUNT, 0)
        set(v) = put(K_TASBIH_COUNT, v)

    var tasbihTarget: Int
        get() = int(K_TASBIH_TARGET, 33)
        set(v) = put(K_TASBIH_TARGET, v)

    var tasbihPhrase: Int
        get() = int(K_TASBIH_PHRASE, 0)
        set(v) = put(K_TASBIH_PHRASE, v)

    fun tasbihToday(date: LocalDate = LocalDate.now()): Int = int(K_TASBIH_DAY + date, 0)

    fun addTasbih(date: LocalDate = LocalDate.now()) {
        tasbihCount += 1
        put(K_TASBIH_DAY + date, tasbihToday(date) + 1)
    }

    // ---------------- Служебное ----------------

    fun getRaw(key: String): String? = cache[key] as? String

    fun putRaw(key: String, value: String) = put(key, value)

    /** Удаляет сохранённые ключи с этим префиксом, кроме перечисленных. */
    fun removeRawExcept(prefix: String, keep: Set<String>) {
        val editor = prefs.edit()
        for (key in cache.keys.toList()) {
            if (key.startsWith(prefix) && key !in keep) {
                editor.remove(key)
                cache.remove(key)
            }
        }
        editor.apply()
    }

    private fun cleanupOldDailyKeys() {
        val cutoff = LocalDate.now().minusDays(7)
        val editor = prefs.edit()
        for (key in prefs.all.keys) {
            val prefix = listOf(K_BONUS, K_PRAYED, K_LOCK_OFF, K_TASBIH_DAY).firstOrNull { key.startsWith(it) } ?: continue
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
    private fun long(key: String, def: Long) = cache[key] as? Long ?: def
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
            is Long -> editor.putLong(key, value)
            is String -> editor.putString(key, value)
            is Set<*> -> editor.putStringSet(key, HashSet(value as Set<String>))
            else -> error("Неподдерживаемый тип для $key")
        }
        editor.apply()
        cache[key] = if (value is Set<*>) HashSet(value) else value
    }

    private const val K_ONBOARDED = "onboarded"
    private const val K_CITY_ID = "city_id"
    private const val K_CITY = "city"
    private const val K_LAT = "lat"
    private const val K_LON = "lon"
    private const val K_METHOD = "method"
    private const val K_MADHAB = "madhab"
    private const val K_NOTIFY = "notify"
    private const val K_NOTIFY_ADHAN = "notify_adhan"
    private const val K_NOTIFY_VOICE = "notify_voice"
    private const val K_FRIDAY = "friday_reminders"
    private const val K_ONLINE = "online_times"
    private const val K_ONLINE_AT = "online_updated_at"
    private const val K_ADJ = "adj_"
    private const val K_ALARM_ON = "alarm_on"
    private const val K_ALARM_OFFSET = "alarm_offset"
    private const val K_ALARM_DAYS = "alarm_days"
    private const val K_ALARM_SOUND = "alarm_sound"
    private const val K_ALARM_MAXVOL = "alarm_max_volume"
    private const val K_ALARM_VIBRATE = "alarm_vibrate"
    private const val K_ALARM_WAKECHECK = "alarm_wake_check"
    private const val K_ALARM_SNOOZE = "alarm_snooze"
    private const val K_ALARM_SNOOZE_AT = "alarm_snooze_at"
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
    private const val K_CAT_CORRECT = "cat_correct_"
    private const val K_RECENT_Q = "recent_questions"
    private const val K_TASBIH_COUNT = "tasbih_count"
    private const val K_TASBIH_TARGET = "tasbih_target"
    private const val K_TASBIH_PHRASE = "tasbih_phrase"
    private const val K_TASBIH_DAY = "tasbih_day_"
}
