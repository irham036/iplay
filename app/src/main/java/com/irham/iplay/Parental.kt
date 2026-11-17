package com.irham.iplay

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * Kontrol orang tua. Pengaturan diambil dari VPS (halaman motoris /admin/iplay),
 * tidak ada PIN di aplikasi.
 */
object Parental {
    private const val SYNC_URL = "https://motoris-ab2.irham.my.id/iplay-api/sync"
    private const val APP_KEY = "iplay-7f3c9a"
    private val ZONE = ZoneId.of("Asia/Jakarta")

    var groups by mutableStateOf(setOf("anak"))
        private set
    var limitMin by mutableIntStateOf(60)
        private set
    var extraMin by mutableIntStateOf(0)
        private set
    var unlockUntil by mutableLongStateOf(0L)
        private set
    var lockNow by mutableStateOf(false)
        private set
    var usedSec by mutableIntStateOf(0)
        private set
    var online by mutableStateOf(false)
        private set

    private lateinit var prefs: SharedPreferences
    private var date = ""
    private var resetAt = 0L
    private var offset = 0L // selisih jam server - jam HP
    lateinit var deviceId: String
        private set
    var isTv = false

    fun init(ctx: Context, tv: Boolean) {
        isTv = tv
        prefs = ctx.getSharedPreferences("parental", Context.MODE_PRIVATE)
        deviceId = prefs.getString("id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("id", it).apply()
        }
        groups = prefs.getStringSet("groups", null)?.toSet()?.ifEmpty { null } ?: setOf("anak")
        limitMin = prefs.getInt("limit", 60)
        extraMin = prefs.getInt("extra", 0)
        unlockUntil = prefs.getLong("unlock", 0L)
        lockNow = prefs.getBoolean("lock", false)
        offset = prefs.getLong("offset", 0L)
        resetAt = prefs.getLong("resetAt", 0L)
        date = prefs.getString("date", "") ?: ""
        usedSec = prefs.getInt("used", 0)
        rollover()
    }

    fun now() = System.currentTimeMillis() + offset

    private fun today(): String = LocalDate.now(ZONE).toString()

    private fun rollover() {
        val d = today()
        if (d != date) {
            date = d
            usedSec = 0
            extraMin = 0
            persist()
        }
    }

    val allowedSec: Int get() = (limitMin + extraMin) * 60

    val unlimited: Boolean get() = limitMin <= 0 || unlockUntil > now()

    val locked: Boolean get() = lockNow || (!unlimited && usedSec >= allowedSec)

    val remainingSec: Int get() = if (unlimited) Int.MAX_VALUE else (allowedSec - usedSec).coerceAtLeast(0)

    fun tick(sec: Int) {
        rollover()
        if (lockNow) return
        usedSec += sec
        if (usedSec % 15 == 0) persist()
    }

    private fun persist() {
        if (!::prefs.isInitialized) return
        prefs.edit()
            .putStringSet("groups", groups)
            .putInt("limit", limitMin).putInt("extra", extraMin)
            .putLong("unlock", unlockUntil).putBoolean("lock", lockNow)
            .putLong("offset", offset).putLong("resetAt", resetAt)
            .putString("date", date).putInt("used", usedSec)
            .apply()
    }

    @SuppressLint("HardwareIds")
    suspend fun sync(watching: String?) {
        rollover()
        val body = JSONObject()
            .put("id", deviceId)
            .put("name", (Build.MANUFACTURER + " " + Build.MODEL).trim())
            .put("model", "Android " + Build.VERSION.RELEASE)
            .put("tv", isTv)
            .put("date", date)
            .put("usedSec", usedSec)
            .put("resetAt", resetAt)
            .put("watching", watching ?: "")
        val res = withContext(Dispatchers.IO) {
            runCatching {
                val c = URL(SYNC_URL).openConnection() as HttpURLConnection
                c.connectTimeout = 10000
                c.readTimeout = 10000
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
        if (res == null) {
            online = false
            return
        }
        online = true
        offset = res.optLong("serverTime", System.currentTimeMillis()) - System.currentTimeMillis()
        val g = res.optJSONArray("groups")
        if (g != null) {
            val set = (0 until g.length()).map { g.getString(it) }.toSet()
            groups = set.ifEmpty { setOf("anak") }
        }
        limitMin = res.optInt("limitMin", limitMin)
        extraMin = res.optInt("extraMin", 0)
        unlockUntil = res.optLong("unlockUntil", 0L)
        lockNow = res.optBoolean("lockNow", false)
        val serverReset = res.optLong("resetAt", 0L)
        val serverUsed = res.optInt("usedSec", 0)
        if (res.optString("date") == date) {
            if (serverReset > resetAt) {
                resetAt = serverReset
                usedSec = serverUsed
            } else {
                usedSec = maxOf(usedSec, serverUsed)
            }
        }
        persist()
    }
}
