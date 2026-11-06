package com.zikrtime.app.data

import android.content.Context
import com.zikrtime.app.i18n.Lang
import org.json.JSONObject
import java.time.ZoneId

/** Выбранное место: город из списка (id) или точка по геолокации (id = null). */
data class City(val id: String?, val name: String, val latitude: Double, val longitude: Double)

/** Список городов с названиями на всех языках приложения (assets/cities.json, общий с iOS). */
object Cities {

    class Group(val id: String, val names: Map<String, String>)
    class Entry(val id: String, val group: String, val lat: Double, val lon: Double, val names: Map<String, String>, val zone: ZoneId? = null) {
        fun toCity(lang: String) = City(id, names[lang] ?: names.getValue("en"), lat, lon)
    }

    @Volatile
    private var data: Pair<List<Group>, List<Entry>>? = null

    fun load(context: Context): Pair<List<Group>, List<Entry>> {
        data?.let { return it }
        val json = context.assets.open("cities.json").bufferedReader().use { it.readText() }
        val root = JSONObject(json)
        fun names(o: JSONObject): Map<String, String> = o.keys().asSequence().associateWith { o.getString(it) }
        val groups = root.getJSONArray("groups").let { a ->
            (0 until a.length()).map { a.getJSONObject(it).let { g -> Group(g.getString("id"), names(g.getJSONObject("names"))) } }
        }
        val cities = root.getJSONArray("cities").let { a ->
            (0 until a.length()).map {
                val c = a.getJSONObject(it)
                Entry(c.getString("id"), c.getString("group"), c.getDouble("lat"), c.getDouble("lon"), names(c.getJSONObject("names")))
            }
        }
        val regional = context.assets.open("cities_ru_regions.tsv").bufferedReader().useLines(::parseRegions)
        val existing = cities.mapTo(mutableSetOf()) { it.id }
        return (groups to (cities + regional.filter { existing.add(it.id) })).also { data = it }
    }

    /** Additional Russian regional centres have their own offline IANA zone. */
    fun regionZone(id: String?): ZoneId? = if (id == null) null else data?.second?.firstOrNull { it.id == id }?.zone

    internal fun parseRegions(lines: Sequence<String>): List<Entry> {
        val languages = listOf("ru", "en", "az", "kk", "ky", "tg", "tr", "uz")
        return lines.filter { it.isNotBlank() && !it.startsWith("#") }.map { line ->
            // id, GeoNames id, WGS84 latitude/longitude, IANA zone, local names
            val fields = line.split('\t', limit = 13)
            require(fields.size == 13 && fields[5].isNotBlank() && fields[6].isNotBlank())
            val names = languages.zip(fields.drop(5)).filter { it.second.isNotBlank() }.toMap()
            Entry(fields[0], "russia", fields[2].toDouble(), fields[3].toDouble(), names, ZoneId.of(fields[4]))
        }.toList()
    }

    val DEFAULT = City("moscow", "Moscow", 55.7558, 37.6173)

    /** Название места на языке приложения. */
    fun displayName(context: Context, city: City): String {
        val id = city.id ?: return city.name
        val entry = load(context).second.firstOrNull { it.id == id } ?: return city.name
        val lang = Lang.content(context)
        return entry.names[lang] ?: entry.names["en"] ?: city.name
    }
}
