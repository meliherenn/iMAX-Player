package com.imax.player.ui.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.imax.player.R
import com.imax.player.core.designsystem.theme.ImaxColors
import kotlinx.coroutines.flow.collectLatest

@Composable
fun AppUpdateHost(
    isTv: Boolean,
    viewModel: AppUpdateViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.checkForUpdate()
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is AppUpdateEvent.OpenIntent -> context.startActivity(event.intent)
            }
        }
    }

    val update = state.update ?: return
    if (!state.isVisible) return

    AppUpdateDialog(
        state = state,
        isTv = isTv,
        currentVersionLabel = stringResource(R.string.update_current_version, com.imax.player.BuildConfig.VERSION_NAME),
        targetVersionLabel = stringResource(R.string.update_target_version, update.versionName),
        onDismiss = viewModel::dismiss,
        onUpdate = viewModel::downloadAndInstall,
        onRetryInstall = viewModel::retryInstallAfterPermission
    )
}

@Composable
private fun AppUpdateDialog(
    state: AppUpdateUiState,
    isTv: Boolean,
    currentVersionLabel: String,
    targetVersionLabel: String,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit,
    onRetryInstall: () -> Unit
) {
    val update = state.update ?: return
    val updateButtonFocusRequester = remember { FocusRequester() }

    LaunchedEffect(update.versionCode) {
        runCatching { updateButtonFocusRequester.requestFocus() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = !update.isMandatory && !state.isDownloading,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
                .padding(if (isTv) 48.dp else 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = if (isTv) 720.dp else 420.dp)
                    .fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = ImaxColors.SurfaceElevated,
                tonalElevation = 8.dp,
                shadowElevation = 12.dp
            ) {
                Column(
                    modifier = Modifier.padding(if (isTv) 32.dp else 22.dp),
                    verticalArrangement = Arrangement.spacedBy(if (isTv) 22.dp else 16.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SystemUpdate,
                            contentDescription = null,
                            tint = ImaxColors.Primary
                        )
                        Column {
                            Text(
                                text = if (update.isMandatory) {
                                    stringResource(R.string.update_required_title)
                                } else {
                                    stringResource(R.string.update_available_title)
                                },
                                style = if (isTv) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "$currentVersionLabel  •  $targetVersionLabel",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ImaxColors.TextSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (update.releaseNotes.isNotBlank()) {
                        Text(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            text = update.releaseNotes,
                            style = MaterialTheme.typography.bodyMedium,
                            color = ImaxColors.TextSecondary,
                            maxLines = if (isTv) 8 else 6
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.update_default_message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = ImaxColors.TextSecondary
                        )
                    }

                    if (state.isDownloading) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            LinearProgressIndicator(
                                progress = { state.downloadProgress / 100f },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = stringResource(R.string.update_downloading, state.downloadProgress),
                                style = MaterialTheme.typography.bodySmall,
                                color = ImaxColors.TextSecondary
                            )
                        }
                    }

                    state.errorMessage?.let { error ->
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = ImaxColors.Error
                        )
                    }

                    if (state.needsInstallPermission) {
                        Text(
                            text = stringResource(R.string.update_install_permission_message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = ImaxColors.Warning
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!update.isMandatory) {
                            OutlinedButton(
                                onClick = onDismiss,
                                enabled = !state.isDownloading
                            ) {
                                Text(stringResource(R.string.update_later))
                            }
                        }

                        Button(
                            modifier = Modifier.focusRequester(updateButtonFocusRequester),
                            onClick = if (state.needsInstallPermission) onRetryInstall else onUpdate,
                            enabled = !state.isDownloading
                        ) {
                            if (state.isDownloading) {
                                CircularProgressIndicator(
                                    color = ImaxColors.TextOnPrimary,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier
                                        .padding(end = 10.dp)
                                        .size(18.dp)
                                )
                            }
                            Text(
                                if (state.needsInstallPermission) {
                                    stringResource(R.string.update_retry_install)
                                } else {
                                    stringResource(R.string.update_now)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
