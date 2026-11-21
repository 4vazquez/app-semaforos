package com.alan.semaforosmx.data.repository

import com.alan.semaforosmx.data.model.AnswerOption
import com.alan.semaforosmx.data.model.LightAnswer
import com.alan.semaforosmx.data.model.LightChat
import com.alan.semaforosmx.data.model.LightChatRules
import com.alan.semaforosmx.data.model.LightQuestion
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query
import java.time.OffsetDateTime

/**
 * Cliente REST de Supabase (PostgREST) para la mensajería ligera por
 * semáforo (tablas `light_questions` y `light_answers`, migración 008).
 *
 * Sigue el mismo patrón que [SupabaseTimingRepository]: el interceptor
 * agrega la clave anónima y el JWT del usuario cuando hay sesión; RLS
 * exige sesión iniciada para leer y escribir (la mensajería es un
 * aporte: igual que sync/agregar/reportar, ver SPEC.md §4.7).
 *
 * `asked_at` y `expires_at` los genera la base de datos (now() y
 * now() + 30 min); el cliente solo manda light_id, asked_by y apodo,
 * y lee la fila creada de vuelta (Prefer: return=representation).
 * Responder es un upsert sobre (question_id, answered_by): una
 * respuesta por usuario por pregunta, cambiar de opinión reemplaza.
 */
class SupabaseLightMessagesRepository(
    supabaseUrl: String,
    supabaseAnonKey: String,
    private val tokenProvider: () -> String? = { null }
) : LightMessagesRepository {

    private val api: MessagesApi

    init {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("apikey", supabaseAnonKey)
                    .addHeader("Authorization", "Bearer ${tokenProvider() ?: supabaseAnonKey}")
                    .build()
                chain.proceed(request)
            }
            .build()
        api = Retrofit.Builder()
            .baseUrl(supabaseUrl.trimEnd('/') + "/rest/v1/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(MessagesApi::class.java)
    }

    override suspend fun activeChat(lightId: String): LightChat? {
        val nowIso = OffsetDateTime.now(java.time.ZoneOffset.UTC).toString()
        val question = api.activeQuestion(
            lightFilter = "eq.$lightId",
            expiresFilter = "gt.$nowIso"
        ).firstOrNull()?.toDomain() ?: return null
        val answers = api.answersFor("eq.${question.id}").map { it.toDomain() }
        return LightChat(question, answers)
    }

    override suspend fun ask(
        lightId: String,
        userId: String?,
        nickname: String
    ): LightQuestion {
        // Una sola pregunta vigente por semáforo: si ya hay una activa,
        // se devuelve tal cual (no se duplica).
        activeChat(lightId)?.let { return it.question }
        val row = QuestionRow(
            id = null,
            lightId = lightId,
            askedBy = userId,
            nickname = nickname.ifBlank { LightChatRules.DEFAULT_NICKNAME },
            askedAt = null,
            expiresAt = null
        )
        return api.insertQuestion(row).first().toDomain()
    }

    override suspend fun answer(
        questionId: String,
        userId: String?,
        option: AnswerOption
    ): LightAnswer {
        val row = AnswerRow(
            id = null,
            questionId = questionId,
            answeredBy = userId,
            option = option.name,
            answeredAt = null
        )
        // Upsert sobre (question_id, answered_by): una respuesta por
        // usuario por pregunta; responder de nuevo la reemplaza.
        return api.upsertAnswer(row).first().toDomain()
    }

    /** Endpoints PostgREST. */
    interface MessagesApi {
        @GET("light_questions")
        suspend fun activeQuestion(
            @Query("light_id") lightFilter: String,
            @Query("expires_at") expiresFilter: String,
            @Query("order") order: String = "asked_at.desc",
            @Query("limit") limit: Int = 1
        ): List<QuestionRow>

        @POST("light_questions")
        suspend fun insertQuestion(
            @Body question: QuestionRow,
            @Header("Prefer") prefer: String = "return=representation"
        ): List<QuestionRow>

        @GET("light_answers")
        suspend fun answersFor(
            @Query("question_id") questionFilter: String
        ): List<AnswerRow>

        @POST("light_answers")
        suspend fun upsertAnswer(
            @Body answer: AnswerRow,
            @Query("on_conflict") onConflict: String = "question_id,answered_by",
            @Header("Prefer") prefer: String = "resolution=merge-duplicates,return=representation"
        ): List<AnswerRow>
    }

    // --- Filas de la base de datos (DTOs) ---

    @JsonClass(generateAdapter = true)
    data class QuestionRow(
        val id: String?,
        @Json(name = "light_id") val lightId: String,
        @Json(name = "asked_by") val askedBy: String?,
        val nickname: String,
        @Json(name = "asked_at") val askedAt: String?,
        @Json(name = "expires_at") val expiresAt: String?
    ) {
        fun toDomain() = LightQuestion(
            id = id.orEmpty(),
            lightId = lightId,
            askedBy = askedBy,
            nickname = nickname,
            askedAtMillis = parseIso(askedAt),
            expiresAtMillis = parseIso(expiresAt)
        )
    }

    @JsonClass(generateAdapter = true)
    data class AnswerRow(
        val id: String?,
        @Json(name = "question_id") val questionId: String,
        @Json(name = "answered_by") val answeredBy: String?,
        val option: String,
        @Json(name = "answered_at") val answeredAt: String?
    ) {
        fun toDomain() = LightAnswer(
            id = id.orEmpty(),
            questionId = questionId,
            answeredBy = answeredBy,
            option = runCatching { AnswerOption.valueOf(option) }
                .getOrDefault(AnswerOption.NADA),
            answeredAtMillis = parseIso(answeredAt)
        )
    }

    companion object {
        /** Parsea un timestamptz ISO 8601 de PostgREST a milisegundos. */
        fun parseIso(iso: String?): Long {
            if (iso.isNullOrBlank()) return 0L
            return runCatching {
                OffsetDateTime.parse(iso).toInstant().toEpochMilli()
            }.getOrDefault(0L)
        }
    }
}
