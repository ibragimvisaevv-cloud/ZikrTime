package com.zikrtime.app.data

import android.content.Context
import com.zikrtime.app.i18n.Lang
import org.json.JSONObject

/** Выбранное место: город из списка (id) или точка по геолокации (id = null). */
data class City(val id: String?, val name: String, val latitude: Double, val longitude: Double)

/** Список городов с названиями на всех языках приложения (assets/cities.json, общий с iOS). */
object Cities {

    class Group(val id: String, val names: Map<String, String>)
    class Entry(val id: String, val group: String, val lat: Double, val lon: Double, val names: Map<String, String>) {
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
        return (groups to cities).also { data = it }
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
