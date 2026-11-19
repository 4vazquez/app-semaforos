package com.alan.semaforosmx.ui.alertas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alan.semaforosmx.R
import com.alan.semaforosmx.ads.AdsManager
import com.alan.semaforosmx.data.repository.InMemoryTimingRepository
import com.alan.semaforosmx.data.repository.RepositoryProvider
import com.alan.semaforosmx.widget.LedBorder
import com.alan.semaforosmx.widget.LedState
import com.alan.semaforosmx.widget.WidgetMode

/**
 * Pantalla «Alertas»: el centro de control del widget en segundo plano.
 *
 * - Activar/desactivar el widget flotante (requiere permiso de
 *   superposición; el botón lo pide si falta).
 * - Tu modo de widget: completo (Colaborador/Plus: botones + opacidad) o
 *   solo LED (usuario normal: solo la alerta luminosa). Con la explicación
 *   de cómo desbloquear el completo.
 * - Vista previa del LED: así se ve la alerta cuando faltan 3 s para el
 *   verde.
 *
 * El widget aparece solo cuando hay un semáforo cerca y se oculta al
 * alejarte; nunca se muestra mientras la app está abierta.
 */
@Composable
fun AlertasScreen(
    userId: String,
    widgetActive: Boolean,
    hasBackgroundLocation: Boolean = true,
    onToggleWidget: () -> Unit,
    modifier: Modifier = Modifier
) {
    var mode by remember { mutableStateOf(WidgetMode.LED_ONLY) }

    LaunchedEffect(userId) {
        val contribution = RepositoryProvider.repository.contributionFor(
            userId, InMemoryTimingRepository.currentMonth()
        )
        mode = AdsManager.widgetMode(contribution)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Widget en segundo plano: activar/desactivar.
        Card(
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = stringResource(R.string.alertas_widget_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.alertas_widget_body),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onToggleWidget,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        stringResource(
                            if (widgetActive) R.string.widget_deactivate
                            else R.string.widget_activate
                        )
                    )
                }
                // Sin ubicación en segundo plano el widget no puede
                // detectar semáforos con la app cerrada: indicar cómo
                // activarla en Ajustes del sistema (no se re-pregunta).
                if (!hasBackgroundLocation) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.alertas_background_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Tu modo de widget (según tu nivel).
        Card(
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = stringResource(R.string.alertas_mode_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(
                        if (mode == WidgetMode.FULL) {
                            R.string.alertas_mode_full
                        } else {
                            R.string.alertas_mode_led
                        }
                    ),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = stringResource(
                        if (mode == WidgetMode.FULL) {
                            R.string.alertas_mode_full_body
                        } else {
                            R.string.alertas_mode_led_body
                        }
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Vista previa del LED (alerta a 3 s del verde), enmarcada en
        // una tarjeta para que se distinga del fondo.
        Text(
            text = stringResource(R.string.alertas_preview_title),
            style = MaterialTheme.typography.titleMedium
        )
        Card(
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            ) {
                LedBorder(
                    state = LedState.ALERT,
                    secondsLeft = 3,
                    opacity = 0.9f,
                    showCountdown = true,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
