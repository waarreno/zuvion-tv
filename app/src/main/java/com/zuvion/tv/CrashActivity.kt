package com.zuvion.tv

import android.app.Activity
import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

/**
 * Tela simples (sem Compose, em processo separado) mostrada quando o app encontra um erro grave.
 * Mostra o detalhe técnico para o usuário copiar e enviar ao desenvolvedor.
 */
class CrashActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val report = runCatching { crashFile(this).readText() }.getOrDefault("(sem detalhes)")
        val pad = (24 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(6, 8, 20))
            setPadding(pad, pad, pad, pad)
        }
        root.addView(TextView(this).apply {
            text = "O Zuvion TV encontrou um erro"
            setTextColor(Color.WHITE); textSize = 22f; typeface = Typeface.DEFAULT_BOLD
        })
        root.addView(TextView(this).apply {
            text = "Copie os detalhes abaixo e envie ao desenvolvedor. Depois, tente abrir de novo."
            setTextColor(Color.rgb(189, 193, 227)); textSize = 15f
            setPadding(0, pad / 2, 0, pad / 2)
        })
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.START }
        buttons.addView(Button(this).apply {
            text = "Copiar detalhes"
            setOnClickListener {
                (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Zuvion TV", report))
                Toast.makeText(this@CrashActivity, "Detalhes copiados", Toast.LENGTH_SHORT).show()
            }
        })
        buttons.addView(Button(this).apply {
            text = "Abrir de novo"
            setOnClickListener {
                startActivity(Intent(this@CrashActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                finish()
            }
        })
        root.addView(buttons)
        root.addView(ScrollView(this).apply {
            addView(TextView(this@CrashActivity).apply {
                text = report
                setTextColor(Color.rgb(140, 144, 179)); textSize = 12f; typeface = Typeface.MONOSPACE
                setTextIsSelectable(true)
                setPadding(0, pad / 2, 0, 0)
            })
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        root.getChildAt(2).requestFocus()
    }

    companion object {
        fun crashFile(ctx: Context) = File(ctx.filesDir, "last_crash.txt")

        fun isCrashProcess(ctx: Context): Boolean {
            val name = if (Build.VERSION.SDK_INT >= 28) android.app.Application.getProcessName()
            else (ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).runningAppProcesses
                ?.firstOrNull { it.pid == Process.myPid() }?.processName
            return name?.endsWith(":crash") == true
        }

        /** Instala o tratador: salva o erro e abre esta tela. */
        fun install(app: android.app.Application) {
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, e ->
                try {
                    val sw = StringWriter()
                    e.printStackTrace(PrintWriter(sw))
                    val info = "Zuvion TV ${BuildConfig.VERSION_NAME} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · " +
                        "${Build.MANUFACTURER} ${Build.MODEL}\n\n$sw"
                    crashFile(app).writeText(info.take(60_000))
                    app.startActivity(Intent(app, CrashActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                    Process.killProcess(Process.myPid())
                    exitProcess(10)
                } catch (_: Throwable) {
                    previous?.uncaughtException(thread, e)
                }
            }
        }
    }
}
