package com.alan.semaforosmx.data.repository

import com.alan.semaforosmx.data.model.AnswerOption
import com.alan.semaforosmx.data.model.LightAnswer
import com.alan.semaforosmx.data.model.LightChat
import com.alan.semaforosmx.data.model.LightChatRules
import com.alan.semaforosmx.data.model.LightQuestion
import java.util.concurrent.ConcurrentHashMap

/**
 * Implementación en memoria de la mensajería ligera. Es la activa sin
 * servidor (MVP): la app funciona completa sin backend (los datos se
 * pierden al cerrar la app; en v1 se sustituye por
 * [SupabaseLightMessagesRepository]).
 *
 * Las preguntas expiradas se filtran al leer (no se borran: el mapa en
 * memoria es efímero de todas formas).
 */
class InMemoryLightMessagesRepository : LightMessagesRepository {

    private val questions = ConcurrentHashMap<String, LightQuestion>()
    private val answers = ConcurrentHashMap<String, MutableList<LightAnswer>>()

    override suspend fun activeChat(lightId: String): LightChat? {
        val now = System.currentTimeMillis()
        val question = questions.values
            .filter { it.lightId == lightId && it.isActive(now) }
            .maxByOrNull { it.askedAtMillis }
            ?: return null
        return LightChat(question, answers[question.id].orEmpty().toList())
    }

    override suspend fun ask(
        lightId: String,
        userId: String?,
        nickname: String
    ): LightQuestion {
        // Una sola pregunta vigente por semáforo: si ya hay una activa,
        // se devuelve tal cual (no se duplica).
        activeChat(lightId)?.let { return it.question }
        val question = LightQuestion(
            lightId = lightId,
            askedBy = userId,
            nickname = nickname.ifBlank { LightChatRules.DEFAULT_NICKNAME }
        )
        questions[question.id] = question
        return question
    }

    override suspend fun answer(
        questionId: String,
        userId: String?,
        option: AnswerOption
    ): LightAnswer {
        val list = answers.getOrPut(questionId) { mutableListOf() }
        synchronized(list) {
            // Una respuesta por usuario por pregunta: responder de nuevo
            // reemplaza la anterior (cambiar de opinión es válido).
            if (userId != null) {
                list.removeAll { it.answeredBy == userId }
            }
            val answer = LightAnswer(
                questionId = questionId,
                answeredBy = userId,
                option = option
            )
            list.add(answer)
            return answer
        }
    }
}
