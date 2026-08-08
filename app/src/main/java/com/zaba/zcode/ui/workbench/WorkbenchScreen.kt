package com.zaba.zcode.ui.workbench

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.zaba.zcode.WorkspaceViewModel

// TEST D5 marker
@Composable
fun WorkbenchScreen(
    vm: WorkspaceViewModel,
    onRun: (String) -> Unit,
    onNavigateToPip: () -> Unit,
    onNavigateToAbout: () -> Unit
) {
    Scaffold { padding ->
        Surface(modifier = Modifier.fillMaxSize().padding(padding), color = MaterialTheme.colorScheme.background) {
            Text("stub WorkbenchScreen", fontSize = 14.sp)
        }
    }
}
