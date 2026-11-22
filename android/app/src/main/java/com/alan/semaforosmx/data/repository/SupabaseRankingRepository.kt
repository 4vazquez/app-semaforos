package com.alan.semaforosmx.data.repository

import com.alan.semaforosmx.data.model.RankingEntry
import com.alan.semaforosmx.data.model.RankingOptIn
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Cliente REST de Supabase (PostgREST) para el ranking semanal
 * (función `top_collaborators_week` y columnas `nickname` /
 * `show_in_ranking` de la tabla `users`, migración 004).
 *
 * Sigue el mismo patrón que [SupabaseLightMessagesRepository]: el
 * interceptor agrega la clave anónima y el JWT del usuario cuando hay
 * sesión. El ranking solo expone apodos con opt-in (la función SQL lo
 * garantiza); el apodo y la preferencia los actualiza el propio
 * usuario en su fila (RLS `users_update_own`; el trigger
 * `guard_role_change` solo bloquea cambios de rol).
 */
class SupabaseRankingRepository(
    supabaseUrl: String,
    supabaseAnonKey: String,
    private val tokenProvider: () -> String? = { null }
) : RankingRepository {

    private val api: RankingApi

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
            .create(RankingApi::class.java)
    }

    override suspend fun topWeek(limit: Int): List<RankingEntry> {
        // La función usa la semana en curso por defecto (lunes,
        // America/Monterrey) y ordena por semáforos distintos
        // descendente, confianza descendente y primer aporte ascendente.
        return api.topCollaboratorsWeek(RpcArgs(limit)).map { it.toDomain() }
    }

    override suspend fun optInFor(userId: String): RankingOptIn {
        val row = api.userRankingPrefs("eq.$userId").firstOrNull() ?: return RankingOptIn()
        return RankingOptIn(nickname = row.nickname, showInRanking = row.showInRanking)
    }

    override suspend fun setOptIn(userId: String, nickname: String?, showInRanking: Boolean) {
        val clean = nickname?.trim()?.takeIf { it.isNotEmpty() }
        // Sin apodo no se puede activar la aparición (SPEC §6.10).
        val effectiveShow = showInRanking && clean != null
        api.updateRankingPrefs("eq.$userId", UserPrefsRow(clean, effectiveShow))
    }

    /** Endpoints PostgREST. */
    interface RankingApi {
        @POST("rpc/top_collaborators_week")
        suspend fun topCollaboratorsWeek(@Body args: RpcArgs): List<RankingRow>

        @GET("users")
        suspend fun userRankingPrefs(@Query("id") idFilter: String): List<UserPrefsRow>

        @PATCH("users")
        suspend fun updateRankingPrefs(
            @Query("id") idFilter: String,
            @Body row: UserPrefsRow
        )
    }

    @JsonClass(generateAdapter = true)
    data class RpcArgs(@Json(name = "p_limit") val limit: Int)

    @JsonClass(generateAdapter = true)
    data class RankingRow(
        @Json(name = "user_id") val userId: String,
        val nickname: String,
        @Json(name = "distinct_lights") val distinctLights: Long,
        @Json(name = "trust_score") val trustScore: Double,
        @Json(name = "first_contribution") val firstContribution: String?
    ) {
        fun toDomain() = RankingEntry(
            userId = userId,
            nickname = nickname,
            distinctLights = distinctLights.toInt(),
            trustScore = trustScore.toFloat(),
            firstContributionMillis =
                SupabaseLightMessagesRepository.parseIso(firstContribution)
        )
    }

    @JsonClass(generateAdapter = true)
    data class UserPrefsRow(
        val nickname: String?,
        @Json(name = "show_in_ranking") val showInRanking: Boolean
    )
}
