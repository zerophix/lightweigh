package com.xcwk.scalehub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.xcwk.scalehub.ui.debug.DebugOverlay
import com.xcwk.scalehub.ui.shell.MainShell
import com.xcwk.scalehub.ui.theme.QkTheme

/** 宿主 — Activity 只做一件事：把导航壳交给 Compose（调试悬浮球同层叠加）。 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            QkTheme {
                Box(Modifier.fillMaxSize()) {
                    MainShell()
                    DebugOverlay()
                }
            }
        }
    }
}
