package com.shilapi.xcertplay.openplay

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import org.json.JSONArray
import org.json.JSONObject

/*
 * The settings catalog: every user-visible setting of the app, by category and card.
 * Generated from design/ui-library/settings/catalog.json (build_catalog.py), the same file
 * the web walkthrough reads.
 *
 * Values are the app's real settings: RealSettings reads and writes them through
 * OpenPlayBindings, with the same follow-up the classic screen runs.
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

/**
 * The app's real settings, in the catalog's terms. [reload] reads every value through
 * OpenPlayBindings; [set] saves through them (with the classic follow-up) and reloads, so the
 * screen always shows what the app will actually use.
 */
internal class RealSettings(private val context: Context, private val catalog: Catalog) {
    val values = mutableStateMapOf<String, Any?>()

    init { reload() }

    fun reload() {
        catalog.rows.forEach { (key, pair) ->
            values[key] = runCatching { OpenPlayBindings.read(context, key, pair.first) }.getOrElse { pair.first.default }
        }
    }

    operator fun get(key: String): Any? = values[key]
    fun bool(key: String) = values[key] as? Boolean ?: false
    fun string(key: String) = values[key]?.toString() ?: ""
    fun number(key: String) = (values[key] as? Number)?.toDouble() ?: 0.0

    fun set(host: SettingsHost, key: String, value: Any?) {
        val row = catalog.rows[key]?.first ?: return
        if (values[key] == value) return
        OpenPlayBindings.write(host, key, row, value)
        reload()
    }

    /** A row shows when its 'when' matches and the classic screen would show it on this head unit. */
    fun visible(row: Row): Boolean {
        if (row.key != null && !OpenPlayBindings.available(context, row.key)) return false
        return row.condition.all { (k, allowed) ->
            val v = values[k]
            allowed.any { a -> a == v || (a is Number && v is Number && a.toDouble() == v.toDouble()) }
        }
    }
}
