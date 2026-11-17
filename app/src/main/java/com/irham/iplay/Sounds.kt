package com.irham.iplay

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Bunyi peringatan (nada lembut buatan sendiri) + suara pengumuman.
 * Suara utama = file MP3 dari VPS (disimpan di HP), jadi tetap bersuara di perangkat
 * yang tidak punya mesin suara Bahasa Indonesia. TTS hanya cadangan.
 */
object Sounds {
    private const val MEDIA = "https://motoris-ab2.irham.my.id/iplay-api/media/"
    private val VOICES = listOf("lock_habis", "lock_kunci", "warn_5", "warn_1")
    private val main = Handler(Looper.getMainLooper())
    private var dir: File? = null
    private var player: MediaPlayer? = null

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pending: String? = null

    fun init(ctx: Context) {
        if (dir == null) {
            dir = File(ctx.filesDir, "voice").apply { mkdirs() }
            thread(isDaemon = true) { VOICES.forEach { runCatching { download(it) } } }
        }
        if (tts != null) return
        tts = TextToSpeech(ctx.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val r = tts?.setLanguage(Locale("id", "ID"))
                ttsReady = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
                if (ttsReady) {
                    tts?.setSpeechRate(0.9f)
                    tts?.setPitch(1.15f)
                    pending?.let { ttsSpeak(it) }
                }
                pending = null
            }
        }
    }

    private fun download(name: String): File? {
        val d = dir ?: return null
        val c = URL("$MEDIA$name.mp3").openConnection() as HttpURLConnection
        c.connectTimeout = 10000
        c.readTimeout = 15000
        return try {
            if (c.responseCode != 200) return null
            val tmp = File(d, "$name.tmp")
            c.inputStream.use { inp -> tmp.outputStream().use { inp.copyTo(it) } }
            val f = File(d, "$name.mp3")
            if (tmp.length() > 1000) { tmp.renameTo(f); f } else { tmp.delete(); null }
        } finally {
            c.disconnect()
        }
    }

    /** Putar suara rekaman [name]; kalau belum ada, pakai TTS [fallback]. */
    fun voice(name: String, fallback: String) {
        thread(isDaemon = true) {
            val f = File(dir ?: return@thread, "$name.mp3").takeIf { it.exists() && it.length() > 1000 }
                ?: runCatching { download(name) }.getOrNull()
            main.post {
                if (f == null) { ttsSpeak(fallback); return@post }
                runCatching {
                    player?.release()
                    player = MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build()
                        )
                        setDataSource(f.absolutePath)
                        setOnCompletionListener { mp -> mp.release(); if (player === mp) player = null }
                        prepare()
                        start()
                    }
                }.onFailure { ttsSpeak(fallback) }
            }
        }
    }

    fun shutdown() {
        player?.release()
        player = null
        tts?.shutdown()
        tts = null
        ttsReady = false
    }

    /** Pengumuman: pakai rekaman suara natural bila teksnya dikenali, selain itu TTS. */
    fun speak(text: String) {
        val t = text.lowercase()
        val name = when {
            "tinggal 5 menit" in t -> "warn_5"
            "tinggal 1 menit" in t -> "warn_1"
            "dikunci" in t -> "lock_kunci"
            "sudah habis" in t -> "lock_habis"
            else -> null
        }
        if (name != null) voice(name, text) else ttsSpeak(text)
    }

    private fun ttsSpeak(text: String) {
        val t = tts ?: return
        if (!ttsReady) { pending = text; return }
        t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tubeyou")
    }

    /** Nada naik lembut seperti kotak musik (C-E-G-C). */
    fun chime(down: Boolean = false) {
        thread(isDaemon = true) {
            runCatching {
                val rate = 22050
                val notes = listOf(523.25, 659.25, 783.99, 1046.5).let { if (down) it.reversed() else it }
                val noteLen = (rate * 0.32).toInt()
                val pcm = ShortArray(noteLen * notes.size + rate / 2)
                notes.forEachIndexed { n, f ->
                    for (i in 0 until noteLen + rate / 2) {
                        val idx = n * noteLen + i
                        if (idx >= pcm.size) break
                        val tt = i.toDouble() / rate
                        val env = exp(-tt * 4.0)
                        val v = (sin(2 * PI * f * tt) + 0.3 * sin(4 * PI * f * tt)) * env * 0.35
                        pcm[idx] = (pcm[idx] + (v * Short.MAX_VALUE)).toInt()
                            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                    }
                }
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(rate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(pcm.size * 2)
                    .build()
                track.write(pcm, 0, pcm.size)
                track.play()
                Thread.sleep((pcm.size * 1000L) / rate + 200)
                track.release()
            }
        }
    }
}
