package com.alan.semaforosmx.widget

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Estado del borde LED del widget flotante.
 */
enum class LedState {
    /** Esperando: el semáforo está en rojo, cuenta regresiva activa. */
    WAITING,

    /** Alerta: faltan 3 segundos o menos para el verde. Parpadea. */
    ALERT,

    /** Verde: el semáforo acaba de cambiar. Verde fijo. */
    GREEN,

    /** Sin datos de tiempo para este semáforo. */
    UNKNOWN
}

/**
 * Borde luminoso estilo LED en el contorno del widget flotante.
 *
 * Diseño de seguridad vial: solo color y un número grande. Nada que invite
 * a leer o mirar la pantalla más de lo necesario.
 *
 * @param state estado actual del semáforo.
 * @param secondsLeft segundos estimados hasta el verde (null si no hay dato).
 * @param opacity opacidad manual del widget (0.2–1.0), controlada por slider.
 * @param showCountdown true si la confianza permite cuenta regresiva.
 * @param compact true para la píldora solo-LED (usuarios normales): borde más
 * delgado, texto más chico y esquinas de píldora.
 */
@Composable
fun LedBorder(
    state: LedState,
    secondsLeft: Int?,
    opacity: Float,
    showCountdown: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    // Parpadeo en ALERT: el borde pulsa entre visible e invisible.
    val blinkAlpha by animateFloatAsState(
        targetValue = if (state == LedState.ALERT) 0.25f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 350),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blink"
    )
    val effectiveAlpha = (if (state == LedState.ALERT) blinkAlpha else 1f) * opacity

    val borderColor = when (state) {
        LedState.WAITING -> Color(0xFFFFC107) // Ámbar: espera.
        LedState.ALERT -> Color(0xFF4CAF50) // Verde: prepárate.
        LedState.GREEN -> Color(0xFF00E676) // Verde brillante: ¡ya!
        LedState.UNKNOWN -> Color(0xFF9E9E9E) // Gris: sin datos.
    }
    // La píldora solo-LED es más chica: borde delgado y esquinas redondas.
    val shape = RoundedCornerShape(if (compact) 36.dp else 24.dp)
    val borderWidth = if (compact) 4.dp else 6.dp
    val fontSize = if (compact) 20.sp else 28.sp

    Box(
        modifier = modifier
            .fillMaxSize()
            .alpha(effectiveAlpha)
            .clip(shape)
            .border(
                width = borderWidth,
                brush = Brush.linearGradient(
                    listOf(borderColor, borderColor.copy(alpha = 0.6f))
                ),
                shape = shape
            )
            .background(Color.Black.copy(alpha = 0.15f * opacity)),
        contentAlignment = Alignment.Center
    ) {
        val label = when {
            state == LedState.GREEN -> "¡Verde!"
            state == LedState.UNKNOWN -> "Sin datos"
            showCountdown && secondsLeft != null -> "$secondsLeft s"
            state == LedState.ALERT -> "Ya casi…"
            else -> "Rojo"
        }
        Text(
            text = label,
            color = Color.White,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(8.dp)
        )
    }
}
