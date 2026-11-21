package com.alan.semaforosmx.widget

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alan.semaforosmx.R
import com.alan.semaforosmx.data.model.AnswerOption
import com.alan.semaforosmx.data.model.LightChat
import com.alan.semaforosmx.ui.chat.LightChatCard

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
 *
 * En modo prueba ([TestModeSimulator]) el widget lleva la marca «PRUEBA»
 * en la esquina y los botones están desactivados: nada de lo simulado se
 * guarda como aporte real.
 *
 * Modo «listo para sync» ([readyToSync]): el conductor está detenido en
 * rojo en un semáforo que la app no conoce y ya presionó «Aquí hay un
 * semáforo». El widget muestra la leyenda «LISTO PARA SYNC», el botón
 * SYNC VERDE late para llamar la atención y el segundo botón pasa a
 * «CANCELAR». El semáforo se crea recién al presionar SYNC VERDE cuando
 * cambia a verde.
 *
 * Leyenda transitoria ([statusMessageResId]): confirmaciones y errores
 * («Sync registrado, gracias», «Semáforo agregado…», «Este semáforo ya se
 * envió a revisión, ¡manda otro!») mostradas DENTRO del widget. Los Toast
 * desde segundo plano están bloqueados en Android 11+, así que el widget
 * es el único lugar donde el conductor las puede ver.
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
    modifier: Modifier = Modifier,
    isTestMode: Boolean = false,
    readyToSync: Boolean = false,
    statusMessageResId: Int? = null,
    statusIsError: Boolean = false,
    showAskButton: Boolean = false,
    chatOpen: Boolean = false,
    lightChat: LightChat? = null,
    chatUserId: String? = null,
    onAsk: () -> Unit = {},
    onAnswer: (AnswerOption) -> Unit = {},
    onCloseChat: () -> Unit = {}
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
            modifier = modifier,
            isTestMode = isTestMode,
            readyToSync = readyToSync,
            statusMessageResId = statusMessageResId,
            statusIsError = statusIsError,
            showAskButton = showAskButton,
            chatOpen = chatOpen,
            lightChat = lightChat,
            chatUserId = chatUserId,
            onAsk = onAsk,
            onAnswer = onAnswer,
            onCloseChat = onCloseChat
        )
        WidgetMode.LED_ONLY -> LedOnlyWidgetOverlay(
            ledState = ledState,
            secondsLeft = secondsLeft,
            showCountdown = showCountdown,
            modifier = modifier,
            isTestMode = isTestMode
        )
    }
}

/**
 * Marca «PRUEBA» en la esquina superior izquierda del widget (modo
 * prueba): que a nadie se le olvide que lo que ve es simulado.
 */
@Composable
private fun TestModeBadge(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.test_mode_badge),
        color = Color.Black,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFFFFC107))
            .padding(horizontal = 4.dp, vertical = 1.dp)
    )
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
    modifier: Modifier = Modifier,
    isTestMode: Boolean = false,
    readyToSync: Boolean = false,
    statusMessageResId: Int? = null,
    statusIsError: Boolean = false,
    showAskButton: Boolean = false,
    chatOpen: Boolean = false,
    lightChat: LightChat? = null,
    chatUserId: String? = null,
    onAsk: () -> Unit = {},
    onAnswer: (AnswerOption) -> Unit = {},
    onCloseChat: () -> Unit = {}
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

            // Leyenda transitoria (confirmación/error) dentro del widget:
            // los Toast desde segundo plano están bloqueados en Android 11+.
            if (statusMessageResId != null) {
                StatusLegend(
                    resId = statusMessageResId,
                    isError = statusIsError
                )
            }

            // Panel de mensajería ligera («¿Ahí hay algo?»): reemplaza
            // los botones mientras está abierto. Solo se abre detenido
            // en un semáforo verificado (el servicio lo garantiza).
            if (chatOpen && lightChat != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                ) {
                    LightChatCard(
                        chat = lightChat,
                        myUserId = chatUserId,
                        onAnswer = onAnswer,
                        compact = true
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = onCloseChat,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF37474F),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.close_button),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else if (readyToSync) {
                ReadyToSyncContent(
                    onSyncVerde = onSyncVerde,
                    onCancel = onAquiHaySemaforo
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onSyncVerde,
                        enabled = !isTestMode,
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
                        enabled = !isTestMode,
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

                    // Mensajería ligera: solo detenido en un semáforo
                    // verificado cercano (el servicio decide cuándo).
                    if (showAskButton) {
                        Button(
                            onClick = onAsk,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF1565C0),
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.chat_ask_button),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
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

        // Marca «PRUEBA» en la esquina superior izquierda (el asa está al
        // centro y el cierre a la derecha: la esquina queda libre).
        if (isTestMode) {
            TestModeBadge(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
            )
        }
    }
}

/**
 * Leyenda transitoria dentro del widget: confirmaciones («Sync
 * registrado, gracias», «Semáforo agregado…») en verde brillante y
 * errores («Reporte no registrado…») en rojo brillante. Llamativa pero
 * compacta y no invasiva: aparece ~1.5 s con un leve «pop» y se limpia
 * sola (decisión de Alan, 2026-11-20).
 */
@Composable
private fun StatusLegend(
    resId: Int,
    isError: Boolean,
    modifier: Modifier = Modifier
) {
    // «Pop» de entrada: entra un poco más grande y se asienta, para que
    // llame la atención sin ocupar más espacio ni estorbar.
    val scale = remember { Animatable(0.85f) }
    LaunchedEffect(resId) {
        scale.snapTo(0.85f)
        scale.animateTo(1f, animationSpec = tween(durationMillis = 160))
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .scale(scale.value)
            .clip(RoundedCornerShape(8.dp))
            .background(
                (if (isError) Color(0xFFD50000) else Color(0xFF00C853))
                    .copy(alpha = 0.95f)
            )
            .padding(horizontal = 6.dp, vertical = 6.dp)
    ) {
        Text(
            text = stringResource(resId),
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Contenido del widget en modo «listo para sync»: leyenda visible para
 * que el conductor sepa que el reporte quedó armado, botón SYNC VERDE
 * latiendo (se presiona cuando el semáforo cambie a verde) y botón
 * CANCELAR para abortar sin crear nada.
 */
@Composable
private fun ReadyToSyncContent(
    onSyncVerde: () -> Unit,
    onCancel: () -> Unit
) {
    // El botón SYNC VERDE late suavemente para llamar la atención sin
    // mirar la pantalla: el conductor está detenido en rojo.
    val transition = rememberInfiniteTransition(label = "readySyncPulse")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "readySyncScale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Leyenda de confirmación: el reporte quedó armado.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF2E7D32).copy(alpha = 0.85f))
                .padding(horizontal = 6.dp, vertical = 6.dp)
        ) {
            Text(
                text = stringResource(R.string.ready_to_sync_title),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.ready_to_sync_hint),
                color = Color.White,
                fontSize = 10.sp,
                textAlign = TextAlign.Center
            )
        }

        Button(
            onClick = onSyncVerde,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2E7D32),
                contentColor = Color.White
            ),
            modifier = Modifier
                .size(96.dp)
                .scale(scale)
        ) {
            Text(
                text = "SYNC\nVERDE",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }

        Button(
            onClick = onCancel,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF616161),
                contentColor = Color.White
            ),
            modifier = Modifier.size(96.dp, 40.dp)
        ) {
            Text(
                text = stringResource(R.string.cancel_new_light),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Píldora solo-LED (usuario normal): solo el borde y la cuenta regresiva. */
@Composable
private fun LedOnlyWidgetOverlay(
    ledState: LedState,
    secondsLeft: Int?,
    showCountdown: Boolean,
    modifier: Modifier = Modifier,
    isTestMode: Boolean = false
) {
    Box(modifier = modifier.fillMaxSize()) {
        LedBorder(
            state = ledState,
            secondsLeft = secondsLeft,
            opacity = LED_ONLY_OPACITY,
            showCountdown = showCountdown,
            modifier = Modifier.fillMaxSize(),
            compact = true
        )
        if (isTestMode) {
            TestModeBadge(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
            )
        }
    }
}

/** Opacidad fija de la píldora solo-LED (no hay slider para cambiarla). */
private const val LED_ONLY_OPACITY = 0.9f
