package com.irham.iplay

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.speech.tts.TextToSpeech
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** Bunyi peringatan (nada lembut buatan sendiri) + suara pengumuman (TTS Bahasa Indonesia). */
object Sounds {
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pending: String? = null

    fun init(ctx: Context) {
        if (tts != null) return
        tts = TextToSpeech(ctx.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val r = tts?.setLanguage(Locale("id", "ID"))
                ttsReady = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
                if (ttsReady) {
                    tts?.setSpeechRate(0.9f)
                    tts?.setPitch(1.15f)
                    pending?.let { speak(it) }
                }
                pending = null
            }
        }
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
        ttsReady = false
    }

    fun speak(text: String) {
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
