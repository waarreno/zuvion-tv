package com.zuvion.tv.recording

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Environment
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.zuvion.tv.R
import com.zuvion.tv.source.Http
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Grava streams ao vivo (TS direto ou HLS) para a pasta do app. */
class RecordingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = HashMap<String, Job>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                val id = intent.getStringExtra(EXTRA_ID)
                if (id == null) jobs.values.forEach { it.cancel() } else jobs[id]?.cancel()
            }
            ACTION_START -> {
                val url = intent.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
                val name = intent.getStringExtra(EXTRA_NAME) ?: "Gravação"
                val minutes = intent.getIntExtra(EXTRA_MINUTES, 120)
                startForegroundCompat(name)
                val id = System.currentTimeMillis().toString()
                val job = scope.launch { record(id, url, name, minutes) }
                jobs[id] = job
                _active.value = _active.value + (id to name)
                job.invokeOnCompletion {
                    jobs.remove(id)
                    _active.value = _active.value - id
                    if (jobs.isEmpty()) {
                        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundCompat(name: String) {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Gravações", NotificationManager.IMPORTANCE_LOW))
        }
        val stop = PendingIntent.getService(
            this, 1, Intent(this, RecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_logo_fg)
            .setContentTitle("Gravando")
            .setContentText(name)
            .setOngoing(true)
            .addAction(0, "Parar", stop)
            .build()
        ServiceCompat.startForeground(
            this, 42, n,
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        )
    }

    private suspend fun record(id: String, url: String, name: String, minutes: Int) {
        val dir = recordingsDir(this)
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date())
        val safe = name.replace(Regex("[^\\p{L}\\p{N} ._-]"), "").trim().take(60).ifBlank { "gravacao" }
        val file = File(dir, "${safe}_$stamp.ts")
        val until = System.currentTimeMillis() + minutes * 60_000L
        file.outputStream().buffered().use { out ->
            if (url.contains(".m3u8", ignoreCase = true)) recordHls(url, out, until)
            else recordDirect(url, out, until)
        }
        if (file.length() < 1024) file.delete()
    }

    private suspend fun recordDirect(url: String, out: OutputStream, until: Long) {
        val req = Request.Builder().url(url).header("User-Agent", Http.USER_AGENT).build()
        val call = Http.streamClient.newCall(req)
        val watchdog = scope.launch {
            while (isActive && System.currentTimeMillis() < until) delay(1000)
            call.cancel()
        }
        try {
            call.execute().use { r ->
                val input = r.body?.byteStream() ?: return
                val buf = ByteArray(128 * 1024)
                while (System.currentTimeMillis() < until) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                }
            }
        } catch (_: Throwable) {
        } finally {
            watchdog.cancel()
            call.cancel()
        }
    }

    private suspend fun recordHls(url: String, out: OutputStream, until: Long) {
        val seen = LinkedHashSet<String>()
        var playlistUrl = url
        while (System.currentTimeMillis() < until) {
            val text = runCatching { Http.get(playlistUrl) }.getOrNull() ?: run { delay(3000); null } ?: continue
            val base = playlistUrl.toHttpUrlOrNull()
            val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
            // Playlist mestre: escolhe a primeira variante
            if (lines.any { it.startsWith("#EXT-X-STREAM-INF") }) {
                val v = lines.firstOrNull { !it.startsWith("#") } ?: return
                playlistUrl = base?.resolve(v)?.toString() ?: v
                continue
            }
            for (seg in lines.filter { !it.startsWith("#") }) {
                val segUrl = base?.resolve(seg)?.toString() ?: seg
                if (!seen.add(segUrl)) continue
                runCatching {
                    Http.streamClient.newCall(Request.Builder().url(segUrl).header("User-Agent", Http.USER_AGENT).build())
                        .execute().use { r -> r.body?.byteStream()?.copyTo(out) }
                }
                if (System.currentTimeMillis() >= until) return
            }
            if (lines.any { it.startsWith("#EXT-X-ENDLIST") }) return
            while (seen.size > 500) seen.remove(seen.first())
            delay(4000)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        _active.value = emptyMap()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.zuvion.tv.REC_START"
        const val ACTION_STOP = "com.zuvion.tv.REC_STOP"
        const val EXTRA_URL = "url"
        const val EXTRA_NAME = "name"
        const val EXTRA_MINUTES = "minutes"
        const val EXTRA_ID = "id"
        private const val CHANNEL = "recordings"

        private val _active = MutableStateFlow<Map<String, String>>(emptyMap())
        val active: StateFlow<Map<String, String>> = _active

        fun recordingsDir(ctx: Context): File =
            (ctx.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: File(ctx.filesDir, "Movies")).apply { mkdirs() }

        fun start(ctx: Context, url: String, name: String, minutes: Int) {
            val i = Intent(ctx, RecordingService::class.java).setAction(ACTION_START)
                .putExtra(EXTRA_URL, url).putExtra(EXTRA_NAME, name).putExtra(EXTRA_MINUTES, minutes)
            ContextCompat.startForegroundService(ctx, i)
        }

        fun stopAll(ctx: Context) {
            ctx.startService(Intent(ctx, RecordingService::class.java).setAction(ACTION_STOP))
        }
    }
}
