package com.shilapi.xcertplay.openplay

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import org.json.JSONArray
import org.json.JSONObject

/*
 * The settings catalog: every user-visible setting of the app, by category and card.
 * Generated from design/ui-library/settings/catalog.json (build_catalog.py), the same file
 * the web prototype reads, so the two previews cannot drift apart.
 *
 * Values live in their own preferences file ("openplay_preview"); the preview never reads or
 * writes the app's real settings.
 */

class Row(
    val kind: String,
    val key: String?,
    val label: String,
    val hint: String?,
    val requires: String?,
    val options: List<String>,
    val default: Any?,
    val reconnect: String?,
    val condition: Map<String, List<Any>>,
    val min: Double,
    val max: Double,
    val step: Double,
    val unit: String,
    val text: String,
    val tone: String?,
    val page: String?,
    val category: String?,
    val toast: String?,
    val icon: String?,
    val primary: Boolean,
    val valueFrom: String?,
    val count: Int,
    val value: String?,
)

class Card(val kind: String?, val title: String?, val icon: String?, val requires: String?, val text: String?, val rows: List<Row>)
class Category(val id: String, val title: String, val icon: String, val subtitle: String, val cards: List<Card>)
class Page(val title: String, val icon: String, val note: String, val rows: List<Row>)
class Catalog(val categories: List<Category>, val pages: Map<String, Page>) {
    /** Each keyed row once, by key, with its card's icon (picker pages show it). */
    val rows: Map<String, Pair<Row, String>> = buildMap {
        categories.forEach { cat -> cat.cards.forEach { card -> card.rows.forEach { r -> if (r.key != null && r.key !in this) put(r.key, r to (r.icon ?: card.icon ?: cat.icon)) } } }
        pages.values.forEach { p -> p.rows.forEach { r -> if (r.key != null && r.key !in this) put(r.key, r to (r.icon ?: p.icon)) } }
    }
}

private fun JSONObject.str(name: String): String? = if (has(name) && !isNull(name)) getString(name) else null

private fun jsonValue(v: Any?): Any? = when (v) {
    is JSONArray -> (0 until v.length()).map { v.get(it) }
    JSONObject.NULL -> null
    else -> v
}

private fun parseRow(o: JSONObject): Row {
    val condition = buildMap {
        o.optJSONObject("when")?.let { w ->
            w.keys().forEach { k ->
                val v = w.get(k)
                put(k, if (v is JSONArray) (0 until v.length()).map { v.get(it) } else listOf(v))
            }
        }
    }
    return Row(
        kind = o.getString("kind"),
        key = o.str("key"),
        label = o.str("label") ?: "",
        hint = o.str("hint"),
        requires = o.str("requires"),
        options = o.optJSONArray("options")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
        default = jsonValue(o.opt("default")),
        reconnect = o.str("reconnect"),
        condition = condition,
        min = o.optDouble("min", 0.0),
        max = o.optDouble("max", 100.0),
        step = o.optDouble("step", 1.0),
        unit = o.str("unit") ?: "",
        text = o.str("text") ?: "",
        tone = o.str("tone"),
        page = o.str("page"),
        category = o.str("category"),
        toast = o.str("toast"),
        icon = o.str("icon"),
        primary = o.optBoolean("primary"),
        valueFrom = o.str("valueFrom"),
        count = o.optInt("count"),
        value = o.str("value"),
    )
}

private fun rowsOf(a: JSONArray?) = if (a == null) emptyList() else (0 until a.length()).map { parseRow(a.getJSONObject(it)) }

fun loadCatalog(context: Context): Catalog {
    val root = JSONObject(context.assets.open("openplay_settings.json").bufferedReader().use { it.readText() })
    val cats = root.getJSONArray("categories")
    val categories = (0 until cats.length()).map { i ->
        val c = cats.getJSONObject(i)
        val cards = c.getJSONArray("cards")
        Category(
            id = c.getString("id"),
            title = c.getString("title"),
            icon = c.getString("icon"),
            subtitle = c.getString("subtitle"),
            cards = (0 until cards.length()).map { j ->
                val card = cards.getJSONObject(j)
                Card(card.str("kind"), card.str("title"), card.str("icon"), card.str("requires"), card.str("text"), rowsOf(card.optJSONArray("rows")))
            },
        )
    }
    val pagesJson = root.getJSONObject("pages")
    val pages = pagesJson.keys().asSequence().associateWith { k ->
        val p = pagesJson.getJSONObject(k)
        Page(p.getString("title"), p.getString("icon"), p.getString("note"), rowsOf(p.getJSONArray("rows")))
    }
    return Catalog(categories, pages)
}

/** Values for every setting, saved to the preview's own preferences. */
class PreviewSettings(context: Context, private val catalog: Catalog) {
    private val prefs = context.getSharedPreferences("openplay_preview", Context.MODE_PRIVATE)
    val values = mutableStateMapOf<String, Any?>()

    init {
        catalog.rows.forEach { (key, pair) ->
            val def = pair.first.default
            values[key] = when (def) {
                is Boolean -> prefs.getBoolean(key, def)
                is Number -> if (prefs.contains(key)) prefs.getFloat(key, def.toFloat()).toDouble() else def.toDouble()
                is String -> prefs.getString(key, def)
                is List<*> -> prefs.getString(key, null)?.let { s -> JSONArray(s).let { a -> (0 until a.length()).map { a.getInt(it) } } } ?: def.map { (it as Number).toInt() }
                else -> def
            }
        }
    }

    operator fun get(key: String): Any? = values[key]
    fun bool(key: String) = values[key] as? Boolean ?: false
    fun string(key: String) = values[key]?.toString() ?: ""
    fun number(key: String) = (values[key] as? Number)?.toDouble() ?: 0.0

    fun set(key: String, value: Any?) {
        values[key] = value
        prefs.edit().apply {
            when (value) {
                is Boolean -> putBoolean(key, value)
                is Number -> putFloat(key, value.toFloat())
                is String -> putString(key, value)
                is List<*> -> putString(key, JSONArray(value).toString())
            }
        }.apply()
    }

    fun visible(row: Row) = row.condition.all { (k, allowed) ->
        val v = values[k]
        allowed.any { a -> a == v || (a is Number && v is Number && a.toDouble() == v.toDouble()) }
    }
}
