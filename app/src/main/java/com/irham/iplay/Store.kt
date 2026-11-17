package com.irham.iplay

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Simpan riwayat & video tersimpan di HP (SharedPreferences). */
class Store(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("iplay", Context.MODE_PRIVATE)

    fun list(key: String): List<Video> {
        val arr = runCatching { JSONArray(prefs.getString(key, "[]")) }.getOrDefault(JSONArray())
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Video(
                id = o.optString("id"),
                title = o.optString("title"),
                channel = o.optString("channel"),
                thumb = o.optString("thumb"),
                published = o.optString("published"),
                duration = o.optString("duration"),
                views = o.optString("views"),
            )
        }
    }

    fun add(key: String, v: Video, max: Int = 200) {
        val items = listOf(v) + list(key).filter { it.id != v.id }
        save(key, items.take(max))
    }

    fun remove(key: String, id: String) = save(key, list(key).filter { it.id != id })

    fun has(key: String, id: String) = list(key).any { it.id == id }

    fun clear(key: String) = save(key, emptyList())

    private fun save(key: String, items: List<Video>) {
        val arr = JSONArray()
        items.forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id).put("title", it.title).put("channel", it.channel)
                    .put("thumb", it.thumb).put("published", it.published)
                    .put("duration", it.duration).put("views", it.views)
            )
        }
        prefs.edit().putString(key, arr.toString()).apply()
    }

    companion object {
        const val HISTORY = "history"
        const val SAVED = "saved"
    }
}
