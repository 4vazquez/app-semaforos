package com.alan.semaforosmx.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alan.semaforosmx.BuildConfig
import com.alan.semaforosmx.R
import com.alan.semaforosmx.data.PlusPrefs
import com.alan.semaforosmx.data.auth.AuthState
import com.alan.semaforosmx.data.auth.UserRole
import com.alan.semaforosmx.ui.RoleBadge
import com.alan.semaforosmx.ui.contact.ContactEmail
import com.alan.semaforosmx.ui.map.VehicleIcons
import com.alan.semaforosmx.ui.map.VehiclePrefs
import com.alan.semaforosmx.ui.map.VehicleType

/**
 * Contenido de Ajustes (una pestaña del menú inferior):
 * - Cuenta (v1.1): inicio de sesión con Google, rol, cierre de sesión
 *   y eliminación de cuenta.
 * - Vibración al avisar del verde (interruptor).
 * - Términos y Condiciones (reabre el texto completo).
 * - Aviso de Privacidad (reabre el texto completo).
 * - Contacto: correo del equipo (tocar para copiar) + botón «Escríbenos»
 *   que abre la app de correo con el asunto precargado.
 * - Acerca de (qué es la app + atribución de OpenStreetMap).
 * - Versión de la app: 7 toques desbloquean las opciones de desarrollador
 *   (estilo Android). Solo tras el desbloqueo aparece el Modo prueba.
 *   Con login, el Admin ve el Modo prueba sin desbloquear nada.
 *
 * Sin Scaffold ni barra superior propios: la barra superior y el menú
 * inferior los pone la actividad (el menú solo existe dentro de la app).
 *
 * v1.0: las secciones van agrupadas en tarjetas (misma información y
 * mismos gestos que antes, solo presentación).
 */
@Composable
fun SettingsScreen(
    vibrationEnabled: Boolean,
    onVibrationChange: (Boolean) -> Unit,
    onTermsClick: () -> Unit,
    onPrivacyClick: () -> Unit,
    onVerificationClick: () -> Unit = {},
    authState: AuthState,
    onSignInClick: () -> Unit,
    onSignOutClick: () -> Unit,
    onDeleteAccountClick: () -> Unit,
    devUnlocked: Boolean,
    onDevUnlock: () -> Unit,
    testModeEnabled: Boolean,
    onTestModeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    /** Toques seguidos en la versión (7 desbloquean las opciones de desarrollador). */
    var versionTaps by remember { mutableIntStateOf(0) }

    /** Confirmación de eliminación de cuenta. */
    var showDeleteDialog by remember { mutableStateOf(false) }

    /** Vehículo elegido para la miniatura de «Mi ubicación» en el mapa. */
    var vehicleType by remember { mutableStateOf(VehiclePrefs.load(context)) }

    /** Plus y su toggle de ubicación simulada (SPEC §9). */
    var plusEnabled by remember { mutableStateOf(PlusPrefs.isPlus(context)) }
    var fakeGpsEnabled by remember { mutableStateOf(PlusPrefs.isFakeGpsEnabled(context)) }

    /** Íconos de los vehículos (se dibujan una sola vez por composición). */
    val vehicleIcons = remember {
        VehicleType.entries.associateWith {
            VehicleIcons.createIconBitmap(context, it).asImageBitmap()
        }
    }

    /** El Modo prueba lo ve el Admin con login o quien desbloqueó las opciones. */
    val showDevOptions = devUnlocked || authState.isAdmin

    /** Copia el correo de contacto y confirma con un aviso. */
    fun copyEmail() {
        clipboardManager.setText(AnnotatedString(ContactEmail.ADDRESS))
        Toast.makeText(
            context,
            context.getString(R.string.contact_copied),
            Toast.LENGTH_SHORT
        ).show()
    }

    /** Abre la app de correo con el destinatario y el asunto precargados. */
    fun writeEmail() {
        val intent = Intent(
            Intent.ACTION_SENDTO,
            Uri.parse(ContactEmail.mailtoUriString())
        )
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // Sin app de correo instalada: el usuario puede copiar la
            // dirección y escribir desde su correo por otro medio.
            Toast.makeText(
                context,
                context.getString(R.string.contact_no_mail_app),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Cuenta: inicio de sesión con Google (v1.1). Sin login, el mapa
        // y la alerta funcionan igual; para aportar hay que iniciar sesión.
        Card(
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = stringResource(R.string.account_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                val user = authState.user
                if (user != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = user.displayName ?: user.email,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        RoleBadge(user.role)
                    }
                    Text(
                        text = user.email,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(
                            when (user.role) {
                                UserRole.ADMIN -> R.string.role_admin
                                UserRole.COLLABORATOR -> R.string.role_collaborator
                                UserRole.USER -> R.string.role_user
                            }
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onSignOutClick) {
                            Text(text = stringResource(R.string.account_sign_out))
                        }
                        TextButton(onClick = { showDeleteDialog = true }) {
                            Text(
                                text = stringResource(R.string.account_delete),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                } else {
                    Text(
                        text = stringResource(R.string.account_body),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    if (authState.loginAvailable) {
                        Button(
                            onClick = onSignInClick,
                            enabled = !authState.isLoading
                        ) {
                            if (authState.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.padding(end = 8.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                            Text(text = stringResource(R.string.account_sign_in))
                        }
                        authState.error?.let { error ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(
                                    when (error) {
                                        "google_sign_in_failed" ->
                                            R.string.account_error_google
                                        "supabase_sign_in_failed" ->
                                            R.string.account_error_supabase
                                        else -> R.string.account_error_generic
                                    }
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.account_not_configured),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Mi vehículo: miniatura en el mapa (decisión de Alan, 2026-11-20).
        Card(
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = stringResource(R.string.settings_vehicle_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.settings_vehicle_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    VehicleType.entries.forEach { type ->
                        VehicleOption(
                            type = type,
                            selected = type == vehicleType,
                            icon = vehicleIcons[type],
                            onClick = {
                                vehicleType = type
                                VehiclePrefs.save(context, type)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Verificación de conductor (sello Colaborador, SPEC §6.9).
        Card(
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = stringResource(R.string.verification_settings_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.verification_settings_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onVerificationClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.verification_settings_button))
                }
            }
        }

        // Plus (SPEC §9): sin anuncios + ubicación simulada para uso
        // personal. La compra (Play Billing) llega después; por ahora
        // la condición Plus se activa desde las opciones de desarrollador.
        Card(
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = stringResource(R.string.plus_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.plus_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (plusEnabled) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.plus_fake_gps_title),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = fakeGpsEnabled,
                            onCheckedChange = { enabled ->
                                PlusPrefs.setFakeGps(context, enabled)
                                fakeGpsEnabled = enabled
                            }
                        )
                    }
                    Text(
                        text = stringResource(R.string.plus_fake_gps_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.plus_not_active),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Preferencias: vibración + Términos y Condiciones.
        Card(
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.setting_vibration),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = vibrationEnabled,
                        onCheckedChange = onVibrationChange
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onTermsClick)
                        .padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.terms_title),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = stringResource(R.string.terms_version),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onPrivacyClick)
                        .padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.privacy_title),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = stringResource(R.string.privacy_version),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null
                    )
                }
            }
        }

        // Contacto.
        Card(
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = stringResource(R.string.contact_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.contact_body),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = ContactEmail.ADDRESS,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .weight(1f)
                            .clickable(onClick = ::copyEmail)
                            .padding(vertical = 8.dp)
                    )
                    TextButton(onClick = ::copyEmail) {
                        Text(text = stringResource(R.string.contact_copy))
                    }
                }
                Button(onClick = ::writeEmail) {
                    Text(text = stringResource(R.string.contact_write))
                }
            }
        }

        // Acerca de.
        Card(
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = stringResource(R.string.about),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.about_body),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // Versión de la app: 7 toques seguidos desbloquean las opciones de
        // desarrollador (estilo «opciones de desarrollador» de Android).
        // El Admin con login ve el Modo prueba sin desbloquear nada.
        Card(
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (devUnlocked) return@clickable
                            versionTaps++
                            when {
                                versionTaps >= 7 -> {
                                    onDevUnlock()
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.dev_options_unlocked),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                versionTaps >= 5 -> {
                                    Toast.makeText(
                                        context,
                                        context.getString(
                                            R.string.dev_options_taps_left,
                                            7 - versionTaps
                                        ),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.app_version),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = BuildConfig.VERSION_NAME,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Opciones de desarrollador: solo visibles tras el desbloqueo
                // o para el Admin con login.
                if (showDevOptions) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    Text(
                        text = stringResource(R.string.dev_options_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.test_mode_title),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = testModeEnabled,
                            onCheckedChange = onTestModeChange
                        )
                    }
                    Text(
                        text = stringResource(R.string.test_mode_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.dev_simulate_plus_title),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = plusEnabled,
                            onCheckedChange = { enabled ->
                                PlusPrefs.setPlus(context, enabled)
                                plusEnabled = enabled
                                if (!enabled) fakeGpsEnabled = false
                            }
                        )
                    }
                    Text(
                        text = stringResource(R.string.dev_simulate_plus_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Espacio final para que la última tarjeta no quede pegada al menú.
        Spacer(modifier = Modifier.height(8.dp))
    }

    // Confirmación de eliminación de cuenta (Play lo exige): se borran
    // los datos personales; los reportes quedan anonimizados.
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.account_delete_title)) },
            text = { Text(stringResource(R.string.account_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDeleteAccountClick()
                    }
                ) {
                    Text(
                        text = stringResource(R.string.account_delete_confirm),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.account_delete_cancel))
                }
            }
        )
    }
}

/** Opción del selector de vehículo: ícono cenital + etiqueta. */
@Composable
private fun VehicleOption(
    type: VehicleType,
    selected: Boolean,
    icon: ImageBitmap?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(shape)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = shape
            )
            .clickable(onClick = onClick)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            )
            .padding(vertical = 10.dp, horizontal = 4.dp)
    ) {
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = stringResource(type.labelRes),
                contentScale = ContentScale.Fit,
                modifier = Modifier.height(56.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(type.labelRes),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center
        )
    }
}
