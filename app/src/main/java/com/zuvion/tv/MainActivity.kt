package com.zuvion.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.zuvion.tv.ui.AppState
import com.zuvion.tv.ui.Root
import com.zuvion.tv.ui.theme.LocalIsTv
import com.zuvion.tv.ui.theme.ZuvionTheme
import com.zuvion.tv.ui.theme.isTvDevice

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as ZuvionApp
        val tv = isTvDevice(this)
        setContent {
            val state = remember { AppState(app) }
            // Na TV a interface é desenhada numa tela lógica de 1280×720 (como o protótipo),
            // seja a TV 720p, 1080p ou 4K. Sem isso, Google TV (960×540 dp) corta as telas.
            val base = LocalDensity.current
            val conf = LocalConfiguration.current
            val density = if (tv) {
                val wPx = conf.screenWidthDp * base.density
                val hPx = conf.screenHeightDp * base.density
                Density(minOf(wPx / 1280f, hPx / 720f).coerceAtLeast(0.5f), 1f)
            } else base
            CompositionLocalProvider(LocalIsTv provides tv, LocalDensity provides density) {
                ZuvionTheme { Root(state) }
            }
        }
        // Algumas Google TV desenham o realce de foco padrão do Android por cima do app.
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            findViewById<android.view.ViewGroup>(android.R.id.content)?.let { root ->
                root.defaultFocusHighlightEnabled = false
                (0 until root.childCount).forEach { root.getChildAt(it).defaultFocusHighlightEnabled = false }
            }
        }
    }
}
