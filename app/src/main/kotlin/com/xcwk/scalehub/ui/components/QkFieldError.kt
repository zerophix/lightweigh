package com.xcwk.scalehub.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.xcwk.scalehub.ui.theme.QkColor
import com.xcwk.scalehub.ui.theme.QkSpacing

@Composable
fun QkFieldError(text: String) {
    Text(
        text,
        modifier = Modifier.padding(top = QkSpacing.Xs),
        style = MaterialTheme.typography.labelSmall,
        color = QkColor.Danger,
    )
}
