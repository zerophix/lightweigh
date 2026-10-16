package com.xcwk.scalehub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import com.xcwk.scalehub.feature.measure.MeasureScreen

/** 宿主 — Activity 只做一件事：把首屏交给 Compose。 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { MeasureScreen() } }
    }
}
