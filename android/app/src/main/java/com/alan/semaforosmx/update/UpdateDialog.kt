package com.alan.semaforosmx.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alan.semaforosmx.R

/**
 * Estado del flujo de actualización en la UI. Lo posee MainActivity; este
 * archivo solo dibuja el diálogo correspondiente a cada estado.
 */
sealed interface UpdateUiState {
    /** Sin diálogo visible. */
    data object Hidden : UpdateUiState

    /** Hay versión nueva: ofrecer actualizar o posponer. */
    data class Available(val release: ReleaseInfo) : UpdateUiState

    /** Descargando el APK. `progress` es 0f..1f (-1f si no hay tamaño). */
    data class Downloading(val release: ReleaseInfo, val progress: Float) :
        UpdateUiState

    /** La descarga falló: reintentar o posponer. */
    data class Failed(val release: ReleaseInfo) : UpdateUiState

    /** Falta el permiso de instalar apps desconocidas: ir a Ajustes. */
    data class NeedPermission(val release: ReleaseInfo) : UpdateUiState
}

/**
 * Diálogo de actualización. Cerrar con el botón atrás o tocando fuera
 * equivale a «Más tarde» (el usuario no quiere actualizar ahora).
 */
@Composable
fun UpdateDialog(
    state: UpdateUiState,
    onUpdate: (ReleaseInfo) -> Unit,
    onLater: (ReleaseInfo) -> Unit,
    onRetry: (ReleaseInfo) -> Unit,
    onGrantPermission: () -> Unit
) {
    when (state) {
        UpdateUiState.Hidden -> Unit
        is UpdateUiState.Available -> AlertDialog(
            onDismissRequest = { onLater(state.release) },
            title = {
                Text(stringResource(R.string.update_title, state.release.tag))
            },
            text = {
                Column {
                    Text(stringResource(R.string.update_body))
                    state.release.notes?.let { notes ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.update_whats_new),
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = notes,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onUpdate(state.release) }) {
                    Text(stringResource(R.string.update_now))
                }
            },
            dismissButton = {
                TextButton(onClick = { onLater(state.release) }) {
                    Text(stringResource(R.string.update_later))
                }
            }
        )
        is UpdateUiState.Downloading -> AlertDialog(
            // Sin despido durante la descarga: si el usuario la
            // interrumpe a medias, el archivo parcial se borra solo.
            onDismissRequest = {},
            title = {
                Text(stringResource(R.string.update_title, state.release.tag))
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (state.progress >= 0f) {
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            stringResource(
                                R.string.update_downloading,
                                (state.progress * 100).toInt()
                            )
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(R.string.update_downloading_unknown))
                    }
                }
            },
            confirmButton = {},
            dismissButton = {}
        )
        is UpdateUiState.Failed -> AlertDialog(
            onDismissRequest = { onLater(state.release) },
            title = {
                Text(stringResource(R.string.update_title, state.release.tag))
            },
            text = { Text(stringResource(R.string.update_failed)) },
            confirmButton = {
                TextButton(onClick = { onRetry(state.release) }) {
                    Text(stringResource(R.string.update_retry))
                }
            },
            dismissButton = {
                TextButton(onClick = { onLater(state.release) }) {
                    Text(stringResource(R.string.update_later))
                }
            }
        )
        is UpdateUiState.NeedPermission -> AlertDialog(
            onDismissRequest = { onLater(state.release) },
            title = { Text(stringResource(R.string.update_permission_title)) },
            text = {
                Column(modifier = Modifier.padding(top = 4.dp)) {
                    Text(stringResource(R.string.update_permission_body))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.update_permission_steps),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = onGrantPermission) {
                    Text(stringResource(R.string.update_permission_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { onLater(state.release) }) {
                    Text(stringResource(R.string.update_later))
                }
            }
        )
    }
}
