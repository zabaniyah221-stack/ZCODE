package com.zaba.zcode.ui.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp

// TEST B2 stub
@Composable
fun PipScreen(
    context: android.content.Context,
    onBack: () -> Unit
) {
    Scaffold { padding ->
        Surface(modifier = Modifier.fillMaxSize().padding(padding), color = MaterialTheme.colorScheme.background) {
            Text("stub PipScreen", fontSize = 14.sp)
        }
    }
}
