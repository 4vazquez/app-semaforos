package com.alan.semaforosmx.ui.sync

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Botón «Sync verde»: grande, de un toque, sin confirmaciones.
 *
 * El conductor lo presiona en el momento exacto en que el semáforo cambia
 * a verde, si la cuenta regresiva de la app estaba desincronizada. Cada
 * presión es un reporte de tiempo que corrige la base de datos.
 */
@Composable
fun SyncVerdeButton(
    onSync: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    BigActionButton(
        text = "SYNC\nVERDE",
        containerColor = Color(0xFF2E7D32),
        onClick = onSync,
        enabled = enabled,
        modifier = modifier
    )
}

/**
 * Botón «Aquí hay un semáforo»: igual de grande y de un toque que el Sync
 * verde, mismo lenguaje visual, color distinto (naranja).
 *
 * El conductor lo presiona cuando está detenido en un semáforo que la app
 * no conoce: la app crea el semáforo en la ubicación GPS actual Y registra
 * el sync de verde de ese momento. El mapa se autocompleta solo.
 */
@Composable
fun AquiHaySemaforoButton(
    onReport: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    BigActionButton(
        text = "AQUÍ HAY\nSEMÁFORO",
        fontSize = 18.sp,
        containerColor = Color(0xFFE65100),
        onClick = onReport,
        enabled = enabled,
        modifier = modifier
    )
}

/** Botón de acción grande compartido por Sync verde y Aquí hay semáforo. */
@Composable
private fun BigActionButton(
    text: String,
    containerColor: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
    fontSize: androidx.compose.ui.unit.TextUnit = 22.sp,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = Color.White,
            disabledContainerColor = Color(0xFF9E9E9E)
        ),
        modifier = modifier.size(140.dp)
    ) {
        Text(
            text = text,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}
