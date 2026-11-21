package com.alan.semaforosmx.data.repository

import com.alan.semaforosmx.data.model.AnswerOption
import com.alan.semaforosmx.data.model.LightChatRules
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas de la mensajería ligera por semáforo («¿Ahí hay algo?»):
 * una sola pregunta vigente por semáforo, expiración, una respuesta
 * por usuario (reemplazable) y conteos por opción.
 */
class LightMessagesTest {

    private val repo = InMemoryLightMessagesRepository()

    @Test
    fun `sin pregunta no hay chat`() = runBlocking {
        assertNull(repo.activeChat("light-1"))
    }

    @Test
    fun `preguntar crea la pregunta vigente`() = runBlocking {
        val q = repo.ask("light-1", "user-a", "Ana")
        assertEquals("light-1", q.lightId)
        assertEquals("Ana", q.nickname)
        assertTrue(q.isActive())
        val chat = repo.activeChat("light-1")
        assertNotNull(chat)
        assertEquals(q.id, chat!!.question.id)
        assertEquals(0, chat.totalAnswers)
    }

    @Test
    fun `una sola pregunta vigente por semaforo`() = runBlocking {
        val first = repo.ask("light-1", "user-a", "Ana")
        val second = repo.ask("light-1", "user-b", "Beto")
        // No se duplica: devuelve la misma pregunta activa.
        assertEquals(first.id, second.id)
        assertEquals("Ana", second.nickname)
    }

    @Test
    fun `preguntas distintas en semaforos distintos`() = runBlocking {
        val a = repo.ask("light-1", "user-a", "Ana")
        val b = repo.ask("light-2", "user-a", "Ana")
        assertTrue(a.id != b.id)
        assertNotNull(repo.activeChat("light-1"))
        assertNotNull(repo.activeChat("light-2"))
    }

    @Test
    fun `apodo en blanco usa el default`() = runBlocking {
        val q = repo.ask("light-1", "user-a", "   ")
        assertEquals(LightChatRules.DEFAULT_NICKNAME, q.nickname)
    }

    @Test
    fun `responder acumula conteos por opcion`() = runBlocking {
        val q = repo.ask("light-1", "user-a", "Ana")
        repo.answer(q.id, "user-b", AnswerOption.CHOQUE)
        repo.answer(q.id, "user-c", AnswerOption.CHOQUE)
        repo.answer(q.id, "user-d", AnswerOption.TRAFICO)
        val chat = repo.activeChat("light-1")!!
        assertEquals(3, chat.totalAnswers)
        assertEquals(2, chat.countsByOption()[AnswerOption.CHOQUE])
        assertEquals(1, chat.countsByOption()[AnswerOption.TRAFICO])
        assertNull(chat.countsByOption()[AnswerOption.OBRA])
    }

    @Test
    fun `una respuesta por usuario, cambiar la reemplaza`() = runBlocking {
        val q = repo.ask("light-1", "user-a", "Ana")
        repo.answer(q.id, "user-b", AnswerOption.CHOQUE)
        repo.answer(q.id, "user-b", AnswerOption.NADA)
        val chat = repo.activeChat("light-1")!!
        assertEquals(1, chat.totalAnswers)
        assertEquals(AnswerOption.NADA, chat.myAnswer("user-b")?.option)
        assertNull(chat.countsByOption()[AnswerOption.CHOQUE])
    }

    @Test
    fun `myAnswer solo para el usuario que respondio`() = runBlocking {
        val q = repo.ask("light-1", "user-a", "Ana")
        repo.answer(q.id, "user-b", AnswerOption.OBRA)
        val chat = repo.activeChat("light-1")!!
        assertNotNull(chat.myAnswer("user-b"))
        assertNull(chat.myAnswer("user-c"))
        assertNull(chat.myAnswer(null))
    }

    @Test
    fun `la pregunta expira a los 30 minutos`() = runBlocking {
        val q = repo.ask("light-1", "user-a", "Ana")
        assertTrue(q.isActive(q.askedAtMillis + LightChatRules.EXPIRY_MILLIS - 1))
        assertTrue(!q.isActive(q.askedAtMillis + LightChatRules.EXPIRY_MILLIS))
        assertEquals(30L * 60L * 1000L, LightChatRules.EXPIRY_MILLIS)
    }

    @Test
    fun `opciones predefinidas son las acordadas`() {
        assertEquals(
            listOf("TRAFICO", "CHOQUE", "MERCADO", "OBRA", "NADA"),
            AnswerOption.entries.map { it.name }
        )
        assertEquals("¿Ahí hay algo?", LightChatRules.QUESTION_TEXT)
    }
}
