package com.tandemmoto.ui.playlist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tandemmoto.R
import com.tandemmoto.ui.components.BackTopBar
import com.tandemmoto.ui.theme.TandemMotoTheme

// TODO(Phase 2): shared playlist, local library, and file-transfer progress.
@Composable
fun PlaylistScreen(onBack: () -> Unit) {
    Scaffold(topBar = { BackTopBar(stringResource(R.string.playlist_title), onBack) }) { padding ->
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
        ) {
            Text(
                text = stringResource(R.string.playlist_empty),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PlaylistPreview() {
    TandemMotoTheme(dynamicColor = false) { PlaylistScreen(onBack = {}) }
}
