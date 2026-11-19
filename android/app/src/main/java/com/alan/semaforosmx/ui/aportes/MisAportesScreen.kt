package com.alan.semaforosmx.ui.aportes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocation
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alan.semaforosmx.R
import com.alan.semaforosmx.data.model.ContributionHistoryEntry
import com.alan.semaforosmx.data.model.HistoryKind
import com.alan.semaforosmx.data.model.TrustLevel
import com.alan.semaforosmx.data.model.UserContribution
import com.alan.semaforosmx.data.model.UserTrust
import com.alan.semaforosmx.data.repository.InMemoryTimingRepository
import com.alan.semaforosmx.data.repository.RepositoryProvider
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Pantalla «Mis aportes»: el panel del colaborador.
 *
 * Muestra, todo en español y sin tecnicismos:
 * - Progreso del mes: semáforos distintos aportados, X/20, con barra.
 * - Estado Colaborador: activo o no, y hasta cuándo (fin del mes en curso).
 * - Nivel de confianza (trust): puntaje + nivel + conteos.
 * - Historial de aportes: syncs manuales, semáforos agregados y retiros
 *   reportados, del más reciente al más antiguo.
 *
 * Sin esta pantalla el programa Colaborador no tiene sentido: el conductor
 * necesita ver si ya ganó el modo sin anuncios. Los datos vienen del
 * repositorio activo (en memoria en el MVP, Supabase en v1).
 */
@Composable
fun MisAportesScreen(
    repoUserId: String?,
    supabaseConfigured: Boolean = false,
    isSignedIn: Boolean = true,
    onSignInClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Sin login (con Supabase) no hay aportes que mostrar: cada quien ve
    // solo los suyos (SPEC §6.9). Se invita a iniciar sesión.
    val needsLogin = supabaseConfigured && !isSignedIn
    val effectiveUserId = repoUserId ?: ""
    var contribution by remember {
        mutableStateOf(UserContribution(effectiveUserId, InMemoryTimingRepository.currentMonth()))
    }
    var trust by remember { mutableStateOf(UserTrust(effectiveUserId)) }
    var history by remember { mutableStateOf<List<ContributionHistoryEntry>>(emptyList()) }

    LaunchedEffect(repoUserId) {
        val uid = repoUserId ?: return@LaunchedEffect
        val repo = RepositoryProvider.repository
        val month = InMemoryTimingRepository.currentMonth()
        contribution = repo.contributionFor(uid, month)
        trust = repo.userTrust(uid)
        history = repo.contributionHistory(uid)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (needsLogin) {
            item {
                Card(
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = stringResource(R.string.aportes_login_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.aportes_login_body),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = onSignInClick) {
                            Text(text = stringResource(R.string.account_sign_in))
                        }
                    }
                }
            }
        }
        item {
            ProgressCard(contribution)
        }
        item {
            TrustCard(trust)
        }
        item {
            Text(
                text = stringResource(R.string.aportes_history_title),
                style = MaterialTheme.typography.titleMedium
            )
        }
        if (history.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.aportes_history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(history) { entry ->
                HistoryRow(entry)
            }
        }
        // Espacio final para que el último renglón no quede pegado al menú.
        item { Spacer(modifier = Modifier.height(8.dp)) }
    }
}

/** Tarjeta de progreso mensual + estado Colaborador. */
@Composable
private fun ProgressCard(contribution: UserContribution) {
    val progress = (contribution.updateCount / UserContribution.AD_FREE_THRESHOLD.toFloat())
        .coerceIn(0f, 1f)
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (contribution.isAdFree) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.aportes_progress_title),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(
                    R.string.aportes_progress,
                    contribution.updateCount
                ),
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (contribution.isAdFree) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.aportes_colaborador_active),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                Text(
                    text = stringResource(
                        R.string.aportes_colaborador_until,
                        monthEndLabel()
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                Text(
                    text = stringResource(
                        R.string.aportes_colaborador_missing,
                        UserContribution.AD_FREE_THRESHOLD - contribution.updateCount
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = stringResource(R.string.aportes_colaborador_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Tarjeta de nivel de confianza (trust). */
@Composable
private fun TrustCard(trust: UserTrust) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.aportes_trust_title),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = trustLevelLabel(trust.level()),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = stringResource(
                    R.string.aportes_trust_score,
                    (trust.trustScore * 100).toInt()
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(
                    R.string.aportes_trust_counts,
                    trust.confirmedCount,
                    trust.rejectedCount
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Nombre legible del nivel de confianza. */
@Composable
private fun trustLevelLabel(level: TrustLevel): String = stringResource(
    when (level) {
        TrustLevel.BUILDING -> R.string.trust_building
        TrustLevel.GOOD -> R.string.trust_good
        TrustLevel.RELIABLE -> R.string.trust_reliable
        TrustLevel.HIGHLY_RELIABLE -> R.string.trust_high
    }
)

/** Un renglón del historial: icono por tipo + texto + fecha. */
@Composable
private fun HistoryRow(entry: ContributionHistoryEntry) {
    val lightName = entry.lightName ?: stringResource(R.string.aportes_light_unnamed)
    val (icon, text) = when (entry.kind) {
        HistoryKind.SYNC -> Icons.Default.Refresh to
            stringResource(R.string.aportes_history_sync, lightName)
        HistoryKind.LIGHT_ADDED -> Icons.Default.AddLocation to
            stringResource(R.string.aportes_history_added, lightName)
        HistoryKind.LIGHT_REMOVED -> Icons.Default.LocationOff to
            stringResource(R.string.aportes_history_removed, lightName)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // Icono dentro de un círculo tintado: cada tipo de aporte se
        // distingue de un vistazo.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape
                )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(text = text, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = historyDateLabel(entry.reportedAtMillis),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Fecha legible de un aporte («18 nov 2026, 10:42»). */
private fun historyDateLabel(millis: Long): String {
    if (millis <= 0L) return ""
    val format = SimpleDateFormat("d MMM yyyy, HH:mm", Locale("es", "MX"))
    return format.format(Date(millis))
}

/**
 * Último día del mes en curso («30 de noviembre de 2026»): el estado
 * Colaborador es mensual, así que dura hasta fin de mes.
 */
private fun monthEndLabel(): String {
    val cal = Calendar.getInstance()
    cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
    val format = SimpleDateFormat("d 'de' MMMM 'de' yyyy", Locale("es", "MX"))
    return format.format(cal.time)
}
