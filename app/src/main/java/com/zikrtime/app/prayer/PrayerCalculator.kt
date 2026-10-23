package com.zikrtime.app.prayer

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.tan

/** Пять обязательных намазов и восход (восход — не намаз, но нужен для расписания). */
enum class Prayer(val title: String, val isPrayer: Boolean) {
    FAJR("Фаджр", true),
    SUNRISE("Восход", false),
    DHUHR("Зухр", true),
    ASR("Аср", true),
    MAGHRIB("Магриб", true),
    ISHA("Иша", true);

    companion object {
        val obligatory = entries.filter { it.isPrayer }
    }
}

/**
 * Методы расчёта. Угол фаджра и иши — высота солнца под горизонтом в градусах.
 * Если ishaMinutes задан, иша наступает через это количество минут после магриба.
 */
enum class CalcMethod(
    val title: String,
    val fajrAngle: Double,
    val ishaAngle: Double?,
    val ishaMinutes: Int? = null,
    /** Номер метода в API aladhan.com. */
    val aladhanId: Int,
) {
    RUSSIA("Духовное управление мусульман России (ДУМ РФ)", 16.0, 15.0, aladhanId = 14),
    MWL("Всемирная исламская лига", 18.0, 17.0, aladhanId = 3),
    TURKEY("Управление по делам религии Турции (Диянет)", 18.0, 17.0, aladhanId = 13),
    MAKKAH("Умм аль-Кура, Мекка", 18.5, null, ishaMinutes = 90, aladhanId = 4),
    EGYPT("Египетское управление геодезии", 19.5, 17.5, aladhanId = 5),
    KARACHI("Университет исламских наук, Карачи", 18.0, 18.0, aladhanId = 1),
    ISNA("Исламское общество Северной Америки (ISNA)", 15.0, 15.0, aladhanId = 2),
}

/** Мазхаб для аср-намаза: длина тени равна предмету (1) или вдвое больше (2, ханафитский). */
enum class AsrMadhab(val title: String, val shadowFactor: Double) {
    STANDARD("Шафиитский, маликитский, ханбалитский", 1.0),
    HANAFI("Ханафитский", 2.0),
}

data class PrayerTimes(
    val date: LocalDate,
    val times: Map<Prayer, ZonedDateTime>,
) {
    operator fun get(p: Prayer): ZonedDateTime = times.getValue(p)
}

/**
 * Астрономический расчёт времени намаза.
 * Формулы основаны на открытом алгоритме PrayTimes (praytimes.org): положение солнца,
 * уравнение времени и склонение. Для северных широт (например, Москва летом), где
 * солнце не опускается на нужный угол, используется правило «на основе угла»:
 * доля ночи = угол / 60.
 */
object PrayerCalculator {

    fun calculate(
        date: LocalDate,
        latitude: Double,
        longitude: Double,
        method: CalcMethod,
        madhab: AsrMadhab,
        zone: ZoneId,
    ): PrayerTimes {
        val jDate = julian(date.year, date.monthValue, date.dayOfMonth) - longitude / (15.0 * 24.0)

        // Начальные приближения (часы местного солнечного времени), затем два шага уточнения:
        // на втором шаге положение солнца берётся уже на найденное время.
        var t = doubleArrayOf(5.0, 6.0, 12.0, 13.0, 18.0, 18.0, 18.0)
        repeat(2) {
            t = compute(t.map { (if (it.isNaN()) 0.0 else it) / 24.0 }.toDoubleArray(), jDate, latitude, method, madhab)
        }

        // Перевод из местного солнечного времени в часы UTC для этой даты.
        val utc = t.map { it - longitude / 15.0 }.toDoubleArray()
        adjustHighLatitudes(utc, method)

        // Магриб — сразу после захода солнца; иша по минутам, если метод так считает.
        utc[MAGHRIB] = utc[SUNSET]
        method.ishaMinutes?.let { utc[ISHA] = utc[MAGHRIB] + it / 60.0 }

        val midnightUtc = date.atStartOfDay(ZoneOffset.UTC).toInstant()
        fun at(hours: Double): ZonedDateTime {
            val minutes = (hours * 60.0).roundToLong()
            return Instant.ofEpochSecond(midnightUtc.epochSecond + minutes * 60).atZone(zone)
        }

        return PrayerTimes(
            date = date,
            times = mapOf(
                Prayer.FAJR to at(utc[FAJR]),
                Prayer.SUNRISE to at(utc[SUNRISE]),
                Prayer.DHUHR to at(utc[DHUHR]),
                Prayer.ASR to at(utc[ASR]),
                Prayer.MAGHRIB to at(utc[MAGHRIB]),
                Prayer.ISHA to at(utc[ISHA]),
            ),
        )
    }

    // Индексы во внутреннем массиве
    private const val FAJR = 0
    private const val SUNRISE = 1
    private const val DHUHR = 2
    private const val ASR = 3
    private const val SUNSET = 4
    private const val MAGHRIB = 5
    private const val ISHA = 6

    private const val RISE_SET_ANGLE = 0.833

    private fun compute(
        dayPortion: DoubleArray,
        jDate: Double,
        lat: Double,
        method: CalcMethod,
        madhab: AsrMadhab,
    ): DoubleArray {
        val fajr = sunAngleTime(method.fajrAngle, dayPortion[FAJR], jDate, lat, ccw = true)
        val sunrise = sunAngleTime(RISE_SET_ANGLE, dayPortion[SUNRISE], jDate, lat, ccw = true)
        val dhuhr = midDay(dayPortion[DHUHR], jDate)
        val asr = asrTime(madhab.shadowFactor, dayPortion[ASR], jDate, lat)
        val sunset = sunAngleTime(RISE_SET_ANGLE, dayPortion[SUNSET], jDate, lat, ccw = false)
        val maghrib = sunset
        val isha = method.ishaAngle?.let { sunAngleTime(it, dayPortion[ISHA], jDate, lat, ccw = false) }
            ?: Double.NaN
        return doubleArrayOf(fajr, sunrise, dhuhr, asr, sunset, maghrib, isha)
    }

    /** Правило для высоких широт: фаджр не раньше, а иша не позже, чем (угол/60) ночи от восхода/заката. */
    private fun adjustHighLatitudes(t: DoubleArray, method: CalcMethod) {
        val night = timeDiff(t[SUNSET], t[SUNRISE])

        val fajrPortion = method.fajrAngle / 60.0 * night
        if (t[FAJR].isNaN() || timeDiff(t[FAJR], t[SUNRISE]) > fajrPortion) {
            t[FAJR] = t[SUNRISE] - fajrPortion
        }

        val ishaAngle = method.ishaAngle ?: return
        val ishaPortion = ishaAngle / 60.0 * night
        if (t[ISHA].isNaN() || timeDiff(t[SUNSET], t[ISHA]) > ishaPortion) {
            t[ISHA] = t[SUNSET] + ishaPortion
        }
    }

    // ---- Астрономия ----

    private data class SunPosition(val declination: Double, val equation: Double)

    private fun sunPosition(jd: Double): SunPosition {
        val d = jd - 2451545.0
        val g = fixAngle(357.529 + 0.98560028 * d)
        val q = fixAngle(280.459 + 0.98564736 * d)
        val l = fixAngle(q + 1.915 * dsin(g) + 0.020 * dsin(2 * g))
        val e = 23.439 - 0.00000036 * d
        val ra = darctan2(dcos(e) * dsin(l), dcos(l)) / 15.0
        val eqt = q / 15.0 - fixHour(ra)
        val decl = darcsin(dsin(e) * dsin(l))
        return SunPosition(decl, eqt)
    }

    private fun midDay(time: Double, jDate: Double): Double {
        val eqt = sunPosition(jDate + time).equation
        return fixHour(12.0 - eqt)
    }

    private fun sunAngleTime(angle: Double, time: Double, jDate: Double, lat: Double, ccw: Boolean): Double {
        val decl = sunPosition(jDate + time).declination
        val noon = midDay(time, jDate)
        val cosArg = (-dsin(angle) - dsin(decl) * dsin(lat)) / (dcos(decl) * dcos(lat))
        if (cosArg < -1.0 || cosArg > 1.0) return Double.NaN // солнце не достигает этого угла
        val t = darccos(cosArg) / 15.0
        return noon + if (ccw) -t else t
    }

    private fun asrTime(factor: Double, time: Double, jDate: Double, lat: Double): Double {
        val decl = sunPosition(jDate + time).declination
        val angle = -darccot(factor + dtan(abs(lat - decl)))
        return sunAngleTime(angle, time, jDate, lat, ccw = false)
    }

    private fun julian(yearIn: Int, monthIn: Int, day: Int): Double {
        var year = yearIn
        var month = monthIn
        if (month <= 2) {
            year -= 1
            month += 12
        }
        val a = floor(year / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (year + 4716)) + floor(30.6001 * (month + 1)) + day + b - 1524.5
    }

    // ---- Тригонометрия в градусах ----

    private fun dtr(d: Double) = d * Math.PI / 180.0
    private fun rtd(r: Double) = r * 180.0 / Math.PI
    private fun dsin(d: Double) = sin(dtr(d))
    private fun dcos(d: Double) = cos(dtr(d))
    private fun dtan(d: Double) = tan(dtr(d))
    private fun darcsin(x: Double) = rtd(asin(x))
    private fun darccos(x: Double) = rtd(acos(x))
    private fun darctan2(y: Double, x: Double) = rtd(atan2(y, x))
    private fun darccot(x: Double) = rtd(atan(1.0 / x))

    private fun fixAngle(a: Double) = fix(a, 360.0)
    private fun fixHour(a: Double) = fix(a, 24.0)
    private fun fix(a: Double, b: Double): Double {
        val r = a - b * floor(a / b)
        return if (r < 0) r + b else r
    }

    /** Разница во времени от t1 до t2 по кругу 24 часов. */
    private fun timeDiff(t1: Double, t2: Double) = fixHour(t2 - t1)
}
