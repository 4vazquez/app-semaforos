package com.alan.semaforosmx.widget

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Contenido del widget flotante (overlay).
 *
 * Tamaño moderado y compacto (decisión de Alan): el widget es una barra o
 * píldora pequeña en un borde de la pantalla, de como máximo un tercio del
 * ancho, arrastrable a donde no estorbe. Nunca cubre toda la pantalla.
 *
 * Dos modos según el nivel del usuario (ver [WidgetMode]):
 * - FULL (Colaborador/Plus): barra vertical en el borde derecho con el
 *   borde LED + cuenta regresiva, botones «Sync verde» y «Aquí hay
 *   semáforo» apilados y slider manual de opacidad de los botones.
 * - LED_ONLY (usuario normal): solo una píldora con el borde LED y la
 *   cuenta regresiva. Sin botones, sin control de opacidad, sin asa: el
 *   LED es el servicio; los botones son el premio por aportar.
 *
 * El widget solo se muestra cuando hay un semáforo cerca (el servicio lo
 * oculta en cuanto te alejas) y nunca se muestra mientras la app está
 * abierta: dentro de la app está el menú; el widget trabaja en segundo
 * plano cuando la app está cerrada.
 */
@Composable
fun WidgetOverlay(
    ledState: LedState,
    secondsLeft: Int?,
    showCountdown: Boolean,
    opacity: Float,
    mode: WidgetMode,
    onOpacityChange: (Float) -> Unit,
    onSyncVerde: () -> Unit,
    onAquiHaySemaforo: () -> Unit,
    onDrag: (Offset) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (mode) {
        WidgetMode.FULL -> FullWidgetOverlay(
            ledState = ledState,
            secondsLeft = secondsLeft,
            showCountdown = showCountdown,
            opacity = opacity,
            onOpacityChange = onOpacityChange,
            onSyncVerde = onSyncVerde,
            onAquiHaySemaforo = onAquiHaySemaforo,
            onDrag = onDrag,
            onClose = onClose,
            modifier = modifier
        )
        WidgetMode.LED_ONLY -> LedOnlyWidgetOverlay(
            ledState = ledState,
            secondsLeft = secondsLeft,
            showCountdown = showCountdown,
            modifier = modifier
        )
    }
}

/** Barra vertical completa (Colaborador/Plus): LED + botones + opacidad. */
@Composable
private fun FullWidgetOverlay(
    ledState: LedState,
    secondsLeft: Int?,
    showCountdown: Boolean,
    opacity: Float,
    onOpacityChange: (Float) -> Unit,
    onSyncVerde: () -> Unit,
    onAquiHaySemaforo: () -> Unit,
    onDrag: (Offset) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        LedBorder(
            state = ledState,
            secondsLeft = secondsLeft,
            opacity = opacity,
            showCountdown = showCountdown,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Asa de arrastre + cerrar (la barra se mueve a donde no estorbe).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectDragGestures { _, dragAmount -> onDrag(dragAmount) }
                    }
            ) {
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = "Arrastra para mover el widget",
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.align(Alignment.Center)
                )
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cerrar widget",
                        tint = Color.White
                    )
                }
            }

            // Botones apilados: grandes, de un toque, sin mirar.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onSyncVerde,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2E7D32),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.size(96.dp)
                ) {
                    Text(
                        text = "SYNC\nVERDE",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
                Button(
                    onClick = onAquiHaySemaforo,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE65100),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.size(96.dp)
                ) {
                    Text(
                        text = "AQUÍ HAY\nSEMÁFORO",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Opacidad manual de los botones (solo Colaborador/Plus).
                Text(
                    text = "Opacidad",
                    color = Color.White,
                    fontSize = 11.sp
                )
                Slider(
                    value = opacity,
                    onValueChange = onOpacityChange,
                    valueRange = 0.2f..1f,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/** Píldora solo-LED (usuario normal): solo el borde y la cuenta regresiva. */
@Composable
private fun LedOnlyWidgetOverlay(
    ledState: LedState,
    secondsLeft: Int?,
    showCountdown: Boolean,
    modifier: Modifier = Modifier
) {
    LedBorder(
        state = ledState,
        secondsLeft = secondsLeft,
        opacity = LED_ONLY_OPACITY,
        showCountdown = showCountdown,
        modifier = modifier.fillMaxSize(),
        compact = true
    )
}

/** Opacidad fija de la píldora solo-LED (no hay slider para cambiarla). */
private const val LED_ONLY_OPACITY = 0.9f
