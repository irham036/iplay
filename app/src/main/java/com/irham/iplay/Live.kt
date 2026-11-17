package com.irham.iplay

import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Laporan "sedang menonton apa" ke VPS (untuk tampilan langsung di halaman orang tua)
 * dan menerima perintah jarak jauh: play, pause, seek, close, sync.
 */
object Live {
    private const val URL_LIVE = "https://motoris-ab2.irham.my.id/iplay-api/live"
    private const val APP_KEY = "iplay-7f3c9a"

    private var player: YouTubePlayer? = null
    private var video: Video? = null
    @Volatile private var cur = 0f
    @Volatile private var dur = 0f
    @Volatile private var playing = false

    /** Dipanggil saat orang tua menekan "Tutup video". */
    var onClose: (() -> Unit)? = null

    /** Listener yang dipasang ke pemutar untuk melacak posisi & status. */
    fun listener(v: Video) = object : AbstractYouTubePlayerListener() {
        override fun onReady(youTubePlayer: YouTubePlayer) {
            player = youTubePlayer
            video = v
            cur = 0f; dur = 0f; playing = false
        }

        override fun onStateChange(youTubePlayer: YouTubePlayer, state: PlayerConstants.PlayerState) {
            playing = state == PlayerConstants.PlayerState.PLAYING || state == PlayerConstants.PlayerState.BUFFERING
        }

        override fun onCurrentSecond(youTubePlayer: YouTubePlayer, second: Float) { cur = second }
        override fun onVideoDuration(youTubePlayer: YouTubePlayer, duration: Float) { dur = duration }
    }

    /** Dipanggil saat pemutar ditutup. */
    fun detach(id: String) {
        if (video?.id == id) { player = null; video = null; playing = false; cur = 0f; dur = 0f }
    }

    suspend fun loop() {
        while (true) {
            val v = video
            val body = JSONObject()
                .put("id", Parental.deviceId)
                .put("videoId", v?.id ?: "")
                .put("title", v?.title ?: "")
                .put("channel", v?.channel ?: "")
                .put("pos", cur.toDouble())
                .put("dur", dur.toDouble())
                .put("playing", playing)
                .put("locked", Parental.locked)
                .put("usedSec", Parental.usedSec)
            val res = withContext(Dispatchers.IO) {
                runCatching {
                    val c = URL(URL_LIVE).openConnection() as HttpURLConnection
                    c.connectTimeout = 8000
                    c.readTimeout = 8000
                    c.requestMethod = "POST"
                    c.doOutput = true
                    c.setRequestProperty("Content-Type", "application/json")
                    c.setRequestProperty("X-IPlay-Key", APP_KEY)
                    c.outputStream.use { it.write(body.toString().toByteArray()) }
                    val txt = if (c.responseCode == 200) c.inputStream.bufferedReader().readText() else null
                    c.disconnect()
                    txt?.let { JSONObject(it) }
                }.getOrNull()
            }
            val cmds = res?.optJSONArray("cmds")
            if (cmds != null) for (i in 0 until cmds.length()) {
                val o = cmds.optJSONObject(i) ?: continue
                val p = player
                when (o.optString("cmd")) {
                    "play" -> p?.play()
                    "pause" -> p?.pause()
                    "seek" -> p?.seekTo((cur + o.optDouble("arg", 0.0).toFloat()).coerceAtLeast(0f))
                    "close" -> onClose?.invoke()
                    "sync" -> Parental.sync(v?.title)
                }
            }
            delay(if (video != null) 2500 else 5000)
        }
    }
}
