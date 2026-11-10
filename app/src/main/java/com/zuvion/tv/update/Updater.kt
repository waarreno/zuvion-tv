package com.zuvion.tv.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.zuvion.tv.BuildConfig
import com.zuvion.tv.source.Http
import com.zuvion.tv.source.arr
import com.zuvion.tv.source.obj
import com.zuvion.tv.source.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

data class UpdateInfo(
    val version: String,
    val notes: String,
    val apkUrl: String,
    val apkName: String,
    val sizeBytes: Long
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data class ReadyToInstall(val info: UpdateInfo, val file: File) : UpdateState
    data class Failed(val info: UpdateInfo?, val message: String) : UpdateState
    data object UpToDate : UpdateState
}

/**
 * Verifica o último Release do GitHub e, com confirmação do usuário,
 * baixa o APK da arquitetura do aparelho e abre o instalador do Android.
 */
class Updater(private val context: Context) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state

    val currentVersion: String = BuildConfig.VERSION_NAME.substringBefore("-")

    fun dismiss() { _state.value = UpdateState.Idle }

    suspend fun check(manual: Boolean = false): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val j = Http.getJson(
                "https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest",
                mapOf("Accept" to "application/vnd.github+json")
            ).obj() ?: error("Resposta inválida")
            val tag = j.str("tag_name") ?: error("Sem versão")
            val version = tag.removePrefix("v").removePrefix("V")
            if (compare(version, currentVersion) <= 0) {
                if (manual) _state.value = UpdateState.UpToDate
                return@withContext null
            }
            val assets = j["assets"].arr().orEmpty().mapNotNull { it.obj() }
                .filter { it.str("name")?.endsWith(".apk") == true }
            val abis = Build.SUPPORTED_ABIS.toList()
            val asset = abis.firstNotNullOfOrNull { abi -> assets.firstOrNull { it.str("name")!!.endsWith("-$abi.apk") } }
                ?: assets.firstOrNull { it.str("name")!!.endsWith("-universal.apk") }
                ?: assets.firstOrNull()
                ?: error("Release sem APK")
            val info = UpdateInfo(
                version = version,
                notes = j.str("body")?.trim().orEmpty(),
                apkUrl = asset.str("browser_download_url")!!,
                apkName = asset.str("name")!!,
                sizeBytes = asset.str("size")?.toLongOrNull() ?: 0
            )
            _state.value = UpdateState.Available(info)
            info
        } catch (e: Throwable) {
            if (manual) _state.value = UpdateState.Failed(null, "Não foi possível verificar: ${e.message}")
            null
        }
    }

    suspend fun download(info: UpdateInfo) = withContext(Dispatchers.IO) {
        try {
            _state.value = UpdateState.Downloading(info, 0f)
            val dir = File(context.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
            val out = File(dir, info.apkName)
            val req = Request.Builder().url(info.apkUrl).header("User-Agent", Http.USER_AGENT).build()
            Http.streamClient.newCall(req).execute().use { r ->
                if (!r.isSuccessful) error("HTTP ${r.code}")
                val body = r.body!!
                val total = body.contentLength().takeIf { it > 0 } ?: info.sizeBytes
                body.byteStream().use { input ->
                    out.outputStream().use { o ->
                        val buf = ByteArray(64 * 1024)
                        var read = 0L
                        var last = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            o.write(buf, 0, n)
                            read += n
                            if (total > 0 && read - last > 256 * 1024) {
                                last = read
                                _state.value = UpdateState.Downloading(info, read.toFloat() / total)
                            }
                        }
                    }
                }
            }
            _state.value = UpdateState.ReadyToInstall(info, out)
        } catch (e: Throwable) {
            _state.value = UpdateState.Failed(info, "Falha no download: ${e.message}")
        }
    }

    fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun installIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    companion object {
        fun compare(a: String, b: String): Int {
            val pa = a.split(".", "-").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
            val pb = b.split(".", "-").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(pa.size, pb.size)) {
                val d = (pa.getOrElse(i) { 0 }).compareTo(pb.getOrElse(i) { 0 })
                if (d != 0) return d
            }
            return 0
        }
    }
}
