package com.irham.iplay

import android.text.Html
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Duration
import java.time.Instant

data class Video(
    val id: String,
    val title: String,
    val channel: String,
    val thumb: String,
    val published: String,
    val duration: String = "",
    val views: String = "",
)

data class Page(val videos: List<Video>, val next: String?)

object YouTubeApi {
    private const val BASE = "https://www.googleapis.com/youtube/v3"

    /** Kata kunci wajib: video hanya tampil jika judul/deskripsi mengandung salah satunya. */
    private val HORROR = listOf(
        "horor", "horror", "seram", "serem", "hantu", "jump scare", "jumpscare", "scary",
        "creepy", "ghost", "mistis", "misteri", "kuntilanak", "pocong", "genderuwo", "setan",
        "iblis", "demon", "angker", "haunted", "teror", "terror", "paranormal", "scream",
        "mencekam", "merinding", "creepypasta", "possessed", "kesurupan", "exorcis", "nightmare"
    )

    fun isHorror(text: String): Boolean {
        val t = text.lowercase()
        return HORROR.any { t.contains(it) }
    }

    /** Pencarian pengguna selalu dipaksa ke ranah horor. */
    fun horrorQuery(q: String): String =
        if (isHorror(q)) q else "$q horor"

    suspend fun search(query: String, pageToken: String? = null): Page = withContext(Dispatchers.IO) {
        val key = BuildConfig.YT_API_KEY
        if (key.isBlank()) error("API key YouTube belum diisi saat build")
        val url = "$BASE/search?part=snippet&type=video&maxResults=25&regionCode=ID" +
            "&videoEmbeddable=true&q=${enc(query)}&key=$key" +
            (pageToken?.let { "&pageToken=$it" } ?: "")
        val json = JSONObject(get(url))
        val items = json.optJSONArray("items")
        val list = mutableListOf<Video>()
        if (items != null) for (i in 0 until items.length()) {
            val it = items.getJSONObject(i)
            val id = it.optJSONObject("id")?.optString("videoId").orEmpty()
            val sn = it.optJSONObject("snippet") ?: continue
            if (id.isEmpty()) continue
            val title = decode(sn.optString("title"))
            val desc = sn.optString("description")
            if (!isHorror("$title $desc ${sn.optString("channelTitle")}")) continue
            val th = sn.optJSONObject("thumbnails")
            val thumb = th?.optJSONObject("high")?.optString("url")
                ?: th?.optJSONObject("medium")?.optString("url")
                ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg"
            list += Video(
                id = id,
                title = title,
                channel = decode(sn.optString("channelTitle")),
                thumb = thumb,
                published = sn.optString("publishedAt"),
            )
        }
        Page(enrich(list, key), json.optString("nextPageToken").ifEmpty { null })
    }

    private fun enrich(list: List<Video>, key: String): List<Video> {
        if (list.isEmpty()) return list
        return try {
            val ids = list.joinToString(",") { it.id }
            val json = JSONObject(get("$BASE/videos?part=contentDetails,statistics&id=$ids&key=$key"))
            val items = json.optJSONArray("items")
            val map = HashMap<String, Pair<String, String>>()
            if (items != null) for (i in 0 until items.length()) {
                val it = items.getJSONObject(i)
                val dur = it.optJSONObject("contentDetails")?.optString("duration").orEmpty()
                val views = it.optJSONObject("statistics")?.optString("viewCount").orEmpty()
                map[it.optString("id")] = fmtDuration(dur) to fmtViews(views)
            }
            list.map { v -> map[v.id]?.let { v.copy(duration = it.first, views = it.second) } ?: v }
        } catch (e: Exception) {
            list
        }
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 15000
        return try {
            val code = c.responseCode
            if (code in 200..299) c.inputStream.bufferedReader().readText()
            else {
                val body = c.errorStream?.bufferedReader()?.readText().orEmpty()
                val msg = runCatching {
                    JSONObject(body).getJSONObject("error").getString("message")
                }.getOrDefault("HTTP $code")
                error(if (code == 403) "Kuota/izin API habis atau ditolak: $msg" else msg)
            }
        } finally {
            c.disconnect()
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    @Suppress("DEPRECATION")
    private fun decode(s: String): String = Html.fromHtml(s).toString()

    fun fmtDuration(iso: String): String = try {
        val d = Duration.parse(iso)
        val h = d.toHours()
        val m = d.toMinutes() % 60
        val s = d.seconds % 60
        if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    } catch (e: Exception) { "" }

    fun fmtViews(raw: String): String {
        val n = raw.toLongOrNull() ?: return ""
        return when {
            n >= 1_000_000_000 -> "%.1f M x ditonton".format(n / 1e9)
            n >= 1_000_000 -> "%.1f jt x ditonton".format(n / 1e6)
            n >= 1_000 -> "%.1f rb x ditonton".format(n / 1e3)
            else -> "$n x ditonton"
        }.replace(".0 ", " ")
    }

    fun ago(iso: String): String = try {
        val sec = Duration.between(Instant.parse(iso), Instant.now()).seconds
        when {
            sec < 3600 -> "${sec / 60} menit lalu"
            sec < 86400 -> "${sec / 3600} jam lalu"
            sec < 86400 * 30 -> "${sec / 86400} hari lalu"
            sec < 86400 * 365 -> "${sec / (86400 * 30)} bulan lalu"
            else -> "${sec / (86400 * 365)} tahun lalu"
        }
    } catch (e: Exception) { "" }
}
