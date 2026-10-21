package com.xcwk.scalehub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.xcwk.scalehub.ui.shell.MainShell
import com.xcwk.scalehub.ui.theme.QkTheme

/** 宿主 — Activity 只做一件事：把导航壳交给 Compose。 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { QkTheme { MainShell() } }
    }
}
