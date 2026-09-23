package com.tandemmoto.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tandemmoto.R
import com.tandemmoto.ui.components.BackTopBar
import com.tandemmoto.ui.theme.TandemMotoTheme

// TODO(Phase 1): Wi-Fi Direct discovery + pairing replaces this placeholder.
@Composable
fun PairScreen(onSkip: () -> Unit, onBack: () -> Unit) {
    Scaffold(topBar = { BackTopBar(stringResource(R.string.pair_title), onBack) }) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
        ) {
            Text(stringResource(R.string.pair_body), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.weight(1f))
            OutlinedButton(
                onClick = onSkip,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
            ) {
                Text(stringResource(R.string.pair_skip))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PairPreview() {
    TandemMotoTheme(dynamicColor = false) { PairScreen(onSkip = {}, onBack = {}) }
}
