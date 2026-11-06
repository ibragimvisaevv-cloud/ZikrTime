package com.zikrtime.app.prayer

import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale
import javax.net.ssl.HttpsURLConnection

/** Local wall-clock times as published by the named authority, including sunrise. */
object RegionalFeeds {
    data class Source(val cityId: String, val name: String, val url: String, val zone: ZoneId)

    private val moscow = ZoneId.of("Europe/Moscow")
    private val commonFields = listOf("fajr", "sunrise", "dhuhr", "asr", "maghrib", "isha")
    private val dagestanFields = listOf("namaz_1", "voshod", "namaz_2", "namaz_3", "namaz_4", "namaz_5")
    private val dagestan = mapOf("makhachkala" to "mahashkala", "khasavyurt" to "hasavyurt", "derbent" to "derbent")
    private val bashkortostan = mapOf(
        "ufa" to "Уфа", "agidel" to "Агидель", "baymak" to "Баймак", "belebey" to "Белебей",
        "beloretsk" to "Белорецк", "birsk" to "Бирск", "blagoveshchensk_rb" to "Благовещенск",
        "davlekanovo" to "Давлеканово", "ishimbay" to "Ишимбай", "kumertau" to "Кумертау",
        "mezhgorye" to "Межгорье", "meleuz" to "Мелеуз", "neftekamsk" to "Нефтекамск",
        "oktyabrsky_rb" to "Октябрьский", "salavat" to "Салават", "sibay" to "Сибай",
        "sterlitamak" to "Стерлитамак", "tuymazy" to "Туймазы", "uchaly" to "Учалы", "yanaul" to "Янаул"
    )
    // Coordinates and local names from api.muftyat.kz/cities/. Nearby settlements have separate tables.
    private data class KazakhPlace(val name: String, val point: String, val zone: String = "Asia/Almaty")
    private val kazakhstan = mapOf(
        "aktobe" to KazakhPlace("Ақтөбе қаласы", "50.300377/57.154555", "Asia/Aqtobe"),
        "almaty" to KazakhPlace("Алматы қаласы", "43.238293/76.945465"),
        "astana" to KazakhPlace("Астана қаласы", "51.133333/71.433333"),
        "atyrau" to KazakhPlace("Атырау қаласы", "47.116667/51.883333", "Asia/Atyrau"),
        "karaganda" to KazakhPlace("Қарағанды қаласы", "49.806406/73.085485"),
        "shymkent" to KazakhPlace("Шымкент қаласы", "42.368009/69.612769"),
        "taraz" to KazakhPlace("Тараз қаласы", "42.883333/71.366667"),
        "turkistan" to KazakhPlace("Түркістан қаласы", "43.302025/68.268979")
    )
    private val kyrgyzstan = mapOf("bishkek" to 1, "osh" to 2, "karakol" to 7)
    // CDUM's published links retain legacy directory names. The year is verified from the document.
    private val cdum = mapOf(
        "saint_petersburg" to ("Spb.php" to "Europe/Moscow"),
        "astrakhan" to ("astrakhan/Astrakhan.php" to "Europe/Astrakhan"),
        "kirov" to ("%D0%9A%D0%B8%D1%80%D0%BE%D0%B2/Kirov.php" to "Europe/Kirov"),
        "yekaterinburg" to ("ekb/Ekb2015.php" to "Asia/Yekaterinburg"),
        "izhevsk" to ("izhevsk/Izhevsk2015.php" to "Europe/Samara"),
        "yoshkar_ola" to ("yoshkar-ola/Yoshkar-Ola2015.php" to "Europe/Moscow"),
        "volgograd" to ("%D0%92%D0%BE%D0%BB%D0%B3%D0%BE%D0%B3%D1%80%D0%B0%D0%B4/Volgograd.php" to "Europe/Volgograd"),
        "kurgan" to ("Kurgan/Kurgan.php" to "Asia/Yekaterinburg"),
        "orenburg" to ("orenburg/Orenburg2015.php" to "Asia/Yekaterinburg"),
        "penza" to ("%D0%9F%D0%B5%D0%BD%D0%B7%D0%B0/Penza2015.php" to "Europe/Moscow"),
        "perm" to ("%D0%9F%D0%B5%D1%80%D0%BC%D1%8C/Perm.php" to "Asia/Yekaterinburg"),
        "rostov_on_don" to ("Rostov-na-Dony/Rostov.php" to "Europe/Moscow"),
        "salekhard" to ("Surgut/Surgut2015.php" to "Asia/Yekaterinburg"),
        "samara" to ("samara/Samara2015.php" to "Europe/Samara"),
        "ulyanovsk" to ("Ulyanovsk/Ulyanovsk2015.php" to "Europe/Ulyanovsk"),
        "khabarovsk" to ("khabarovsk/Habarovsk2015.php" to "Asia/Vladivostok"),
        "cheboksary" to ("cheboksary/Cheboksary.php" to "Europe/Moscow"),
        "chelyabinsk" to ("Chelyabinsk/Chelyabinsk2015.php" to "Asia/Yekaterinburg")
    )
    private val dotted = DateTimeFormatter.ofPattern("dd.MM.uuuu", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT)
    private val dashed = DateTimeFormatter.ofPattern("dd-MM-uuuu", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT)

    internal val apiCityIds: Set<String> get() =
        setOf("grozny", "kazan") + dagestan.keys + bashkortostan.keys + kazakhstan.keys + kyrgyzstan.keys

    fun source(cityId: String): Source? = when (cityId) {
        "grozny" -> Source(cityId, "ДУМ ЧР · таблица через Assolat", "https://dumchr.ru/", moscow)
        "kazan" -> Source(cityId, "ДУМ Республики Татарстан", "https://dumrt.ru/ru/", moscow)
        in dagestan -> Source(cityId, "Муфтият Республики Дагестан", "https://muftiyatrd.ru/vremya-namaza", moscow)
        in bashkortostan -> Source(cityId, "ДУМ Республики Башкортостан", "https://dumrb.com/", ZoneId.of("Asia/Yekaterinburg"))
        in kazakhstan -> Source(cityId, "ДУМ Казахстана", "https://www.muftyat.kz/ru/namaz_times/", ZoneId.of(kazakhstan.getValue(cityId).zone))
        in kyrgyzstan -> Source(cityId, "ДУМ Кыргызстана", "https://muftiyat.kg/ru/calendar/", ZoneId.of("Asia/Bishkek"))
        in cdum -> cdum.getValue(cityId).let { (path, zone) ->
            Source(cityId, "ЦДУМ России", "https://www.cdum.ru/time-namaz/" + path, ZoneId.of(zone))
        }
        "nizhny_novgorod" -> Source(cityId, "ДУМ Нижегородской области", "https://islamnn.ru/namazy/2026/nn2026.pdf", moscow)
        "saratov" -> Source(cityId, "ДУМ Саратовской области", "https://dumso.ru/raspisanie", ZoneId.of("Europe/Saratov"))
        else -> null
    }

    /** Blocking. A missing or malformed month must never replace a cached verified month. */
    fun download(source: Source, month: YearMonth): Map<LocalDate, List<LocalTime>> {
        val city = source.cityId
        val entries: List<Pair<LocalDate, List<String>>> = when (city) {
            "grozny" -> {
                val response = json("https://assolat.ru/api/namaz/" + month.year + "/" + month.monthValue + "?city=grozny")
                require(response.getString("city") == city && response.matches(month))
                datedRows(response.getJSONArray("data"), "date", DateTimeFormatter.ISO_LOCAL_DATE, month)
            }
            "kazan" -> {
                // 1 = start of Fajr, 2 = congregation, 4 = zenith, 5 = published Dhuhr.
                get("https://dumrt.ru/netcat_files/391/638/Kazan.csv").lineSequence()
                    .filter { it.isNotBlank() }.map { it.trim().split(';') }
                    .map { row -> LocalDate.parse(row.first().trimStart('\uFEFF'), dotted) to row }
                    .filter { (date, _) -> YearMonth.from(date) == month }
                    .map { (date, row) -> date to listOf(1, 3, 5, 6, 7, 8).map(row::get) }.toList()
            }
            in dagestan -> {
                // The publisher uses a 365-day recurring month/day table. There is no February 29 row.
                // Verified against the publisher's 2026 calendar; an undated file is never carried forward.
                if (month.year != 2026) return emptyMap()
                val rows = JSONArray(get("https://muftiyatrd.ru/json/namaz/" + dagestan.getValue(city) + ".json"))
                require(rows.length() == 365)
                val start = LocalDate.of(2001, month.monthValue, 1).dayOfYear - 1
                (1..month.lengthOfMonth()).map { day ->
                    month.atDay(day) to fields(rows.getJSONObject(start + day - 1), dagestanFields)
                }
            }
            in bashkortostan -> {
                val name = bashkortostan.getValue(city)
                val encoded = URLEncoder.encode(name, "UTF-8")
                val response = json("https://api.dumrb.com/Prayer/GetByCity?cityName=" + encoded +
                    "&year=" + month.year + "&month=" + month.monthValue)
                require(response.getString("cityName") == name && response.matches(month))
                val rows = response.getJSONArray("prayerTimes")
                require(rows.length() == month.lengthOfMonth())
                (0 until rows.length()).map { day -> month.atDay(day + 1) to fields(rows.getJSONObject(day)) }
            }
            in kazakhstan -> {
                val place = kazakhstan.getValue(city)
                val response = json("https://api.muftyat.kz/prayer-times/" + month.year + "/" + place.point)
                require(response.getString("city") == place.name && response.getInt("year") == month.year)
                datedRows(response.getJSONArray("result"), "Date", DateTimeFormatter.ISO_LOCAL_DATE, month)
            }
            in kyrgyzstan -> {
                val id = kyrgyzstan.getValue(city)
                val range = "?start=" + month.atDay(1).format(dashed) + "&end=" + month.atEndOfMonth().format(dashed)
                val response = json("https://muftiyat.kg/ru/api/v1/calendar/" + id + "/" + range)
                require(response.getInt("id") == id)
                datedRows(response.getJSONArray("prayertimes"), "date", dashed, month)
            }
            else -> return emptyMap()
        }
        if (entries.isEmpty()) return emptyMap()
        val result = entries.associate { (date, times) ->
            val parsed = times.map { LocalTime.parse(it.trim().padStart(5, '0')) }
            date to parsed
        }
        require(entries.size == month.lengthOfMonth() && result.size == entries.size)
        require(result.values.all(RegionalTimes::valid))
        return result
    }

    private fun datedRows(rows: JSONArray, key: String, format: DateTimeFormatter, month: YearMonth) =
        (0 until rows.length()).map { rows.getJSONObject(it) }
            .map { row -> LocalDate.parse(row.getString(key), format) to row }
            .filter { (date, _) -> YearMonth.from(date) == month }
            .map { (date, row) -> date to fields(row) }

    private fun fields(row: JSONObject, keys: List<String> = commonFields) = keys.map { row.getString(it) }
    private fun JSONObject.matches(month: YearMonth) = getInt("year") == month.year && getInt("month") == month.monthValue
    private fun json(url: String) = JSONObject(get(url))

    private fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpsURLConnection
        try {
            connection.connectTimeout = 6_000
            connection.readTimeout = 8_000
            connection.instanceFollowRedirects = false
            require(connection.responseCode == HttpsURLConnection.HTTP_OK)
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}
