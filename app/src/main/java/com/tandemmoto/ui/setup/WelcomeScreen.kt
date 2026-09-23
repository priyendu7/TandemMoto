package com.tandemmoto.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tandemmoto.R
import com.tandemmoto.ui.theme.TandemMotoTheme

@Composable
fun WelcomeScreen(onGetStarted: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .safeDrawingPadding()
                .padding(24.dp)
        ) {
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.welcome_title),
                style = MaterialTheme.typography.headlineMedium
            )
            Text(stringResource(R.string.welcome_body), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.welcome_promise),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onGetStarted,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
            ) {
                Text(stringResource(R.string.welcome_get_started))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WelcomePreview() {
    TandemMotoTheme(dynamicColor = false) { WelcomeScreen(onGetStarted = {}) }
}
