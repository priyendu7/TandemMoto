package com.tandemmoto.ui.components

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.tandemmoto.R
import com.tandemmoto.permissions.AppPermission
import com.tandemmoto.permissions.PermissionStatus
import com.tandemmoto.permissions.PermissionsState
import com.tandemmoto.permissions.isPermissionGranted
import com.tandemmoto.ui.theme.TandemMotoTheme

/** Current permission state plus the one action a section needs: ask, or open settings. */
@Stable
class PermissionRequester(val state: PermissionsState, val request: (AppPermission) -> Unit)

/**
 * Tracks permissions for the Home screen and re-checks them on resume, so a permission granted in
 * system settings shows up as soon as the user comes back.
 */
@Composable
fun rememberPermissionRequester(): PermissionRequester {
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
    return PermissionRequester(state) { permission ->
        if (state.status(permission) == PermissionStatus.PermanentlyDenied) {
            // The system dialog won't show again; only app settings can grant it now.
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null)
                )
            )
        } else {
            launcher.launch(permission.permissionsFor(sdk).toTypedArray())
        }
    }
}

/**
 * Shown in place of a Home section whose permission is missing: one line saying what to allow and
 * why, and a button, "Allow" or (once permanently denied) "Open settings". It uses the same strip
 * as the connection bar, so both are the same height. The rest of the screen keeps working.
 */
@Composable
fun PermissionPrompt(
    permission: AppPermission,
    state: PermissionsState,
    onRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val permanentlyDenied = state.status(permission) == PermissionStatus.PermanentlyDenied
    StatusStrip(
        container = MaterialTheme.colorScheme.secondaryContainer,
        content = MaterialTheme.colorScheme.onSecondaryContainer,
        icon = Icons.Filled.Info,
        text = stringResource(permission.prompt(state.sdk, state.approximateLocationOnly)),
        modifier = modifier,
        trailing = {
            TextButton(
                onClick = onRequest,
                colors = ButtonDefaults.textButtonColors(contentColor = LocalContentColor.current)
            ) {
                Text(
                    stringResource(
                        if (permanentlyDenied) {
                            R.string.permission_open_settings
                        } else {
                            R.string.permission_allow
                        }
                    ),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    )
}

@Preview(name = "Prompt and the bar it replaces", showBackground = true)
@Composable
private fun PermissionPromptPreview() {
    TandemMotoTheme(dynamicColor = false) {
        Column {
            PermissionPrompt(
                permission = AppPermission.NEARBY,
                state = PermissionsState(
                    34,
                    mapOf(AppPermission.NEARBY to PermissionStatus.Denied)
                ),
                onRequest = {}
            )
            ConnectionStatusBar(ConnectionStatus.NotPaired)
        }
    }
}
