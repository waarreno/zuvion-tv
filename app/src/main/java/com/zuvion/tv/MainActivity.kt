package com.zuvion.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
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
            CompositionLocalProvider(LocalIsTv provides tv) {
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
