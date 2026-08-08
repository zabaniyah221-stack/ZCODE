package com.zaba.zcode.ui.editor

import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp

// TEST D2 marker
@Composable
fun EditorScreen(
    code: String,
    onCodeChange: (String) -> Unit,
    webViewRef: MutableState<WebView?> = remember { mutableStateOf(null) }
) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Text("stub EditorScreen", fontSize = 14.sp)
    }
}
