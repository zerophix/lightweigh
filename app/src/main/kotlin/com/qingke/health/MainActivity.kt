package com.qingke.health

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import com.qingke.health.trigger.ui.MeasureScreen

/** trigger — Activity 只做一件事：把首屏交给 Compose。 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { MeasureScreen() } }
    }
}
