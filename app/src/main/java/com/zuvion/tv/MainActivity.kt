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
    }
}
