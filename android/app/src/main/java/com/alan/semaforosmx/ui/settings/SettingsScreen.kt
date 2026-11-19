package com.alan.semaforosmx.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.alan.semaforosmx.BuildConfig
import com.alan.semaforosmx.R
import com.alan.semaforosmx.ui.contact.ContactEmail

/**
 * Contenido de Ajustes (una pestaña del menú inferior):
 * - Vibración al avisar del verde (interruptor).
 * - Términos y Condiciones (reabre el texto completo).
 * - Contacto: correo del equipo (tocar para copiar) + botón «Escríbenos»
 *   que abre la app de correo con el asunto precargado.
 * - Acerca de (qué es la app + atribución de OpenStreetMap).
 * - Versión de la app: 7 toques desbloquean las opciones de desarrollador
 *   (estilo Android). Solo tras el desbloqueo aparece el Modo prueba.
 *
 * Sin Scaffold ni barra superior propios: la barra superior y el menú
 * inferior los pone la actividad (el menú solo existe dentro de la app).
 */
@Composable
fun SettingsScreen(
    vibrationEnabled: Boolean,
    onVibrationChange: (Boolean) -> Unit,
    onTermsClick: () -> Unit,
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
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Vibración.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
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

        // Términos y Condiciones.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onTermsClick)
                .padding(vertical = 12.dp)
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

        Spacer(modifier = Modifier.height(16.dp))

        // Contacto.
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

        Spacer(modifier = Modifier.height(16.dp))

        // Acerca de.
        Text(
            text = stringResource(R.string.about),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.about_body),
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Versión de la app: 7 toques seguidos desbloquean las opciones de
        // desarrollador (estilo «opciones de desarrollador» de Android).
        // Sin login todavía, es la única forma de distinguir al Admin: en
        // v1, con login de Google y roles, el Modo prueba se limitará al
        // rol Admin y este desbloqueo desaparecerá.
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
                .padding(vertical = 12.dp)
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

        // Opciones de desarrollador: solo visibles tras el desbloqueo.
        if (devUnlocked) {
            Spacer(modifier = Modifier.height(8.dp))
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
        }
    }
}
