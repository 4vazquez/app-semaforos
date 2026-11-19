package com.alan.semaforosmx.widget

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
 * El brillo es intenso en la orilla y se difumina en degradado hacia el
 * centro (v1.1.2, decisión de Alan): una línea nítida de color en el borde
 * exterior y una banda de luz que se apaga poco a poco hacia adentro, como
 * un LED real. El centro queda libre para el número y los botones.
 *
 * @param state estado actual del semáforo.
 * @param secondsLeft segundos estimados hasta el verde (null si no hay dato).
 * @param opacity opacidad manual del widget (0.2–1.0), controlada por slider.
 * @param showCountdown true si la confianza permite cuenta regresiva.
 * @param compact true para la píldora solo-LED (usuarios normales): banda
 * de luz más angosta, texto más chico y esquinas de píldora.
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
    // La píldora solo-LED es más chica: banda de luz más angosta y
    // esquinas más redondas.
    val shape = RoundedCornerShape(if (compact) 36.dp else 24.dp)
    val cornerRadius = if (compact) 36.dp else 24.dp
    val glowWidth = if (compact) 16.dp else 28.dp
    val fontSize = if (compact) 20.sp else 28.sp

    Box(
        modifier = modifier
            .fillMaxSize()
            .alpha(effectiveAlpha)
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.15f * opacity)),
        contentAlignment = Alignment.Center
    ) {
        // Degradado del LED: intenso en la orilla, transparente al centro.
        // Se dibuja con trazos concéntricos (cada uno más tenue que el
        // anterior) para que el degradado siga la forma redondeada del
        // widget sin artefactos en las esquinas.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val glowPx = minOf(glowWidth.toPx(), minOf(size.width, size.height) / 2f)
            val cornerPx = cornerRadius.toPx()
            val steps = LED_GLOW_STEPS
            val stepPx = glowPx / steps
            for (k in 0 until steps) {
                val inset = stepPx * (k + 0.5f)
                drawRoundRect(
                    color = borderColor.copy(alpha = glowStepAlpha(k, steps)),
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - 2 * inset, size.height - 2 * inset),
                    cornerRadius = CornerRadius((cornerPx - inset).coerceAtLeast(0f)),
                    style = Stroke(width = stepPx + 1f)
                )
            }
            // Línea nítida en la orilla: el borde del LED bien definido.
            drawRoundRect(
                color = borderColor,
                topLeft = Offset(1f, 1f),
                size = Size(size.width - 2f, size.height - 2f),
                cornerRadius = CornerRadius((cornerPx - 1f).coerceAtLeast(0f)),
                style = Stroke(width = 2f)
            )
        }
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

/** Trazos concéntricos que forman el degradado del LED. */
private const val LED_GLOW_STEPS = 14

/**
 * Alfa del paso `index` (0 = orilla) de un degradado de `steps` pasos:
 * 1.0 en la orilla y casi 0 en el paso más interior.
 */
internal fun glowStepAlpha(index: Int, steps: Int): Float =
    (1f - index / steps.toFloat()).coerceIn(0f, 1f)
