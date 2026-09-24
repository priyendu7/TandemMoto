package com.tandemmoto.ui.setup

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.tandemmoto.R
import com.tandemmoto.permissions.AppPermission
import com.tandemmoto.permissions.PermissionStatus
import com.tandemmoto.permissions.PermissionsState
import com.tandemmoto.permissions.isPermissionGranted
import com.tandemmoto.ui.components.BackTopBar
import com.tandemmoto.ui.theme.TandemMotoTheme

/** Requests permissions and re-checks them on resume (e.g. after returning from settings). */
@Composable
fun PermissionsRoute(onContinue: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val sdk = Build.VERSION.SDK_INT
    var askedBefore by rememberSaveable { mutableStateOf(setOf<AppPermission>()) }
    var refresh by remember { mutableIntStateOf(0) }
    val state = remember(askedBefore, refresh) {
        PermissionsState.from(
            sdk = sdk,
            isGranted = context::isPermissionGranted,
            askedBefore = askedBefore,
            shouldShowRationale = { activity?.shouldShowRequestPermissionRationale(it) == true }
        )
    }
    val launcher = rememberLauncherForActivityResult(RequestMultiplePermissions()) { result ->
        askedBefore = askedBefore + AppPermission.applicable(sdk).filter { permission ->
            permission.permissionsFor(sdk).any(result::containsKey)
        }
        refresh++
    }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    PermissionsScreen(
        state = state,
        onAllow = { launcher.launch(state.requestable.toTypedArray()) },
        onOpenSettings = {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null)
                )
            )
        },
        onContinue = onContinue,
        onBack = onBack
    )
}

@Composable
fun PermissionsScreen(
    state: PermissionsState,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit
) {
    val canRequest = state.requestable.isNotEmpty()
    Scaffold(
        topBar = { BackTopBar(stringResource(R.string.permissions_title), onBack) }
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    stringResource(R.string.permissions_intro),
                    style = MaterialTheme.typography.bodyLarge
                )
                state.statuses.forEach { (permission, status) ->
                    PermissionRow(permission, status, state.sdk, onOpenSettings)
                }
                if (state.approximateLocationOnly) {
                    Text(
                        stringResource(R.string.permissions_precise_location_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            state.missing.firstOrNull { it.required }?.let { blocking ->
                Text(
                    stringResource(
                        R.string.permissions_required_missing,
                        stringResource(blocking.title(state.sdk))
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            val buttonModifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
            if (canRequest) {
                Button(onClick = onAllow, modifier = buttonModifier) {
                    Text(stringResource(R.string.permissions_allow))
                }
                OutlinedButton(
                    onClick = onContinue,
                    enabled = state.canContinue,
                    modifier = buttonModifier
                ) {
                    Text(stringResource(R.string.permissions_continue))
                }
            } else {
                Button(
                    onClick = onContinue,
                    enabled = state.canContinue,
                    modifier = buttonModifier
                ) {
                    Text(stringResource(R.string.permissions_continue))
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(
    permission: AppPermission,
    status: PermissionStatus,
    sdk: Int,
    onOpenSettings: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val granted = status == PermissionStatus.Granted
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
    ) {
        Icon(
            imageVector = when {
                granted -> Icons.Filled.CheckCircle
                permission.required -> Icons.Filled.Warning
                else -> Icons.Filled.Info
            },
            contentDescription = null,
            tint = when {
                granted -> colors.primary
                permission.required -> colors.error
                else -> colors.onSurfaceVariant
            }
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(permission.title(sdk)),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                stringResource(permission.reason(sdk)),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant
            )
            Text(
                stringResource(
                    when {
                        granted -> R.string.permission_status_granted
                        permission.required -> R.string.permission_status_required
                        else -> R.string.permission_status_optional
                    }
                ),
                style = MaterialTheme.typography.labelLarge,
                color = if (granted) colors.primary else colors.onSurfaceVariant
            )
            if (status == PermissionStatus.PermanentlyDenied) {
                TextButton(onClick = onOpenSettings) {
                    Text(stringResource(R.string.permission_open_settings))
                }
            }
        }
    }
}

@Preview(name = "Nothing granted", showBackground = true)
@Composable
private fun PermissionsDeniedPreview() {
    TandemMotoTheme(dynamicColor = false) {
        PermissionsScreen(
            state = PermissionsState(
                sdk = 34,
                statuses = AppPermission.applicable(34).associateWith { PermissionStatus.Denied }
            ),
            onAllow = {},
            onOpenSettings = {},
            onContinue = {},
            onBack = {}
        )
    }
}

@Preview(name = "Permanently denied · dark", showBackground = true)
@Composable
private fun PermissionsPermanentlyDeniedPreview() {
    TandemMotoTheme(darkTheme = true, dynamicColor = false) {
        PermissionsScreen(
            state = PermissionsState(
                sdk = 34,
                statuses = mapOf(AppPermission.NEARBY to PermissionStatus.PermanentlyDenied)
            ),
            onAllow = {},
            onOpenSettings = {},
            onContinue = {},
            onBack = {}
        )
    }
}
