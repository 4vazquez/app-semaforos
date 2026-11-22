package com.alan.semaforosmx.ui.verification

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alan.semaforosmx.R
import com.alan.semaforosmx.data.model.VerificationRequest
import com.alan.semaforosmx.data.model.VerificationStatus
import com.alan.semaforosmx.data.repository.RepositoryProvider
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pantalla «Verificación de conductor» (sello Colaborador, SPEC §6.9).
 *
 * El usuario sube una foto de su licencia de chofer (tipo C/D en
 * Nuevo León) con consentimiento expreso (casilla, LFPDPPP art. 9).
 * La foto vive en un bucket privado, se revisa una sola vez y se
 * borra tras la verificación; solo queda guardado el estado, la
 * fecha y el tipo de licencia. La revisión del Admin NO va en el
 * APK público: Alan resuelve desde el panel de Supabase.
 *
 * Sin sesión iniciada (con Supabase) no hay identidad que verificar:
 * se invita a iniciar sesión. En modo local la solicitud se guarda
 * en memoria (solo para probar el flujo).
 */
@Composable
fun VerificationScreen(
    userId: String?,
    isSignedIn: Boolean,
    supabaseConfigured: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var requests by remember { mutableStateOf<List<VerificationRequest>>(emptyList()) }
    var licenseType by remember { mutableStateOf("C") }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var photoBytes by remember { mutableStateOf<ByteArray?>(null) }
    var consentChecked by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var sentNotice by remember { mutableStateOf(false) }

    LaunchedEffect(userId) {
        val uid = userId ?: return@LaunchedEffect
        requests = RepositoryProvider.verification.myRequests(uid)
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val bytes = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()
            if (bytes != null && bytes.isNotEmpty()) {
                photoUri = uri
                photoBytes = bytes
                errorText = null
            }
        }
    }

    val latest = requests.firstOrNull()
    val needsLogin = supabaseConfigured && !isSignedIn

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onClose) {
                Text(text = stringResource(R.string.verification_close))
            }
        }

        Text(
            text = stringResource(R.string.verification_title),
            style = MaterialTheme.typography.titleLarge
        )

        when {
            needsLogin -> {
                Card(
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = stringResource(R.string.verification_login_required),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            latest?.status == VerificationStatus.PENDING -> {
                StatusCard(
                    title = stringResource(R.string.verification_pending),
                    body = stringResource(R.string.verification_pending_body),
                    submittedAtMillis = latest.submittedAtMillis
                )
            }

            latest?.status == VerificationStatus.APPROVED -> {
                StatusCard(
                    title = stringResource(R.string.verification_approved),
                    body = stringResource(R.string.verification_approved_body),
                    submittedAtMillis = latest.submittedAtMillis
                )
            }

            else -> {
                // Sin solicitud, o la última fue rechazada: formulario.
                if (latest?.status == VerificationStatus.REJECTED) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = stringResource(R.string.verification_rejected),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(
                                    R.string.verification_rejected_body,
                                    latest?.rejectionReason
                                        ?: stringResource(R.string.verification_rejected_generic)
                                ),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                if (sentNotice) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.verification_sent),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.verification_intro),
                    style = MaterialTheme.typography.bodyMedium
                )

                // Tipo de licencia (C / D en Nuevo León).
                Text(
                    text = stringResource(R.string.verification_license_type),
                    style = MaterialTheme.typography.titleSmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = licenseType == "C",
                            onClick = { licenseType = "C" }
                        )
                        Text(text = "C")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = licenseType == "D",
                            onClick = { licenseType = "D" }
                        )
                        Text(text = "D")
                    }
                }

                // Foto de la licencia.
                OutlinedButton(
                    onClick = { picker.launch("image/*") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (photoUri == null) {
                            stringResource(R.string.verification_pick_photo)
                        } else {
                            stringResource(R.string.verification_photo_ready)
                        }
                    )
                }
                photoBytes?.let { bytes ->
                    val bitmap = remember(bytes) {
                        runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
                            .getOrNull()
                    }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                        )
                    }
                }

                // Consentimiento expreso (LFPDPPP art. 9): sin la
                // casilla marcada, la foto no se envía.
                Row(verticalAlignment = Alignment.Top) {
                    Checkbox(
                        checked = consentChecked,
                        onCheckedChange = { consentChecked = it }
                    )
                    Text(
                        text = stringResource(R.string.verification_consent),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp, top = 12.dp)
                    )
                }

                Text(
                    text = stringResource(R.string.verification_privacy_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                errorText?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Button(
                    onClick = {
                        val uid = userId ?: return@Button
                        val bytes = photoBytes ?: return@Button
                        if (!consentChecked) return@Button
                        submitting = true
                        errorText = null
                        scope.launch {
                            try {
                                val fileName = "license_${System.currentTimeMillis()}.jpg"
                                RepositoryProvider.verification.submit(
                                    userId = uid,
                                    licenseType = licenseType,
                                    photoBytes = bytes,
                                    fileName = fileName,
                                    consentAtMillis = System.currentTimeMillis()
                                )
                                requests = RepositoryProvider.verification.myRequests(uid)
                                photoUri = null
                                photoBytes = null
                                consentChecked = false
                                sentNotice = true
                            } catch (e: Exception) {
                                errorText = context.getString(R.string.verification_error)
                            } finally {
                                submitting = false
                            }
                        }
                    },
                    enabled = photoBytes != null && consentChecked && !submitting && userId != null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (submitting) {
                            stringResource(R.string.verification_sending)
                        } else {
                            stringResource(R.string.verification_submit)
                        }
                    )
                }
            }
        }
    }
}

/** Tarjeta de estado (en revisión / verificado) con la fecha de envío. */
@Composable
private fun StatusCard(title: String, body: String, submittedAtMillis: Long) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = body, style = MaterialTheme.typography.bodyMedium)
            if (submittedAtMillis > 0L) {
                Spacer(modifier = Modifier.height(8.dp))
                val format = SimpleDateFormat("d MMM yyyy, HH:mm", Locale("es", "MX"))
                Text(
                    text = stringResource(
                        R.string.verification_submitted_on,
                        format.format(Date(submittedAtMillis))
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
