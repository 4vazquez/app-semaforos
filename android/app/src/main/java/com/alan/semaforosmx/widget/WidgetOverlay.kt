package com.alan.semaforosmx.widget

import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Contenido del widget flotante (overlay).
 *
 * Espejo del prototipo web de Alan: ventana flotante arrastrable con
 * control de opacidad. El conductor lo mueve a donde no le estorbe y
 * solo necesita ver el color del borde LED y, si el tiempo falló,
 * apretar un botón enorme. Nada más.
 *
 * Layout: asa de arrastre arriba · borde LED en el contorno ·
 * cuenta regresiva al centro · botón "Sync verde" · slider de opacidad.
 */
@Composable
fun WidgetOverlay(
    ledState: LedState,
    secondsLeft: Int?,
    showCountdown: Boolean,
    opacity: Float,
    onOpacityChange: (Float) -> Unit,
    onSyncVerde: () -> Unit,
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
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Asa de arrastre (igual que el widget web: se mueve a donde
            // el conductor quiera) + botón de cerrar.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectDragGestures { _, dragAmount -> onDrag(dragAmount) }
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = "Arrastra para mover el widget",
                    tint = Color.White.copy(alpha = 0.8f)
                )
                IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cerrar widget",
                        tint = Color.White
                    )
                }
            }

            // Parte inferior: botón Sync verde + opacidad.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onSyncVerde,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2E7D32),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.size(104.dp) // Grande: un toque sin mirar.
                ) {
                    Text(
                        text = "SYNC\nVERDE",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Opacidad",
                    color = Color.White,
                    fontSize = 12.sp
                )
                Slider(
                    value = opacity,
                    onValueChange = onOpacityChange,
                    valueRange = 0.2f..1f,
                    modifier = Modifier.fillMaxWidth(0.8f)
                )
            }
        }
    }
}
