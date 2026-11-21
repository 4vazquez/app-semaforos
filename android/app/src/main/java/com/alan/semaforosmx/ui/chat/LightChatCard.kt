package com.alan.semaforosmx.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alan.semaforosmx.R
import com.alan.semaforosmx.data.model.AnswerOption
import com.alan.semaforosmx.data.model.LightChat
import com.alan.semaforosmx.data.model.LightChatRules

/**
 * Tarjeta de mensajería ligera de un semáforo: la pregunta «¿Ahí hay
 * algo?» y las 5 respuestas predefinidas con sus conteos. La respuesta
 * del usuario actual se resalta; tocar otra opción la reemplaza.
 *
 * Se usa en el widget (cuando el conductor está detenido en el
 * semáforo) y en el detalle del semáforo en el mapa. No hay texto
 * libre: solo opciones predefinidas (ver SPEC.md §12, v2).
 *
 * @param compact true para el widget (120 dp de ancho): las opciones
 *   se apilan en filas de ancho completo en vez de chips.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LightChatCard(
    chat: LightChat,
    myUserId: String?,
    onAnswer: (AnswerOption) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val counts = chat.countsByOption()
    val myAnswer = chat.myAnswer(myUserId)
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = LightChatRules.QUESTION_TEXT,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(R.string.chat_asked_by, chat.question.nickname),
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(modifier = Modifier.height(4.dp))
        if (chat.totalAnswers == 0) {
            Text(
                text = stringResource(R.string.chat_no_answers),
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            Text(
                text = stringResource(R.string.chat_answers_count, chat.totalAnswers),
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (compact) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AnswerOption.entries.forEach { option ->
                    val count = counts[option] ?: 0
                    val isMine = myAnswer?.option == option
                    TextButton(
                        onClick = { onAnswer(option) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (count > 0) {
                                "${option.displayName} · $count"
                            } else {
                                option.displayName
                            },
                            fontWeight = if (isMine) FontWeight.Bold else FontWeight.Normal,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AnswerOption.entries.forEach { option ->
                    val count = counts[option] ?: 0
                    val isMine = myAnswer?.option == option
                    AssistChip(
                        onClick = { onAnswer(option) },
                        label = {
                            Text(
                                text = if (count > 0) {
                                    "${option.displayName} · $count"
                                } else {
                                    option.displayName
                                }
                            )
                        },
                        colors = if (isMine) {
                            AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        } else {
                            AssistChipDefaults.assistChipColors()
                        },
                        modifier = Modifier.size(width = 160.dp, height = 36.dp)
                    )
                }
            }
        }
        if (myAnswer != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(
                    R.string.chat_your_answer, myAnswer.option.displayName
                ),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
