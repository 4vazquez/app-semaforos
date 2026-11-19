package com.alan.semaforosmx.data.auth

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

/**
 * Cliente REST de Supabase Auth (GoTrue) + lectura de la fila `users`.
 *
 * Sin SDK de Supabase: la app ya trae Retrofit + OkHttp + Moshi, así
 * que la autenticación se hace contra la API REST pública de GoTrue:
 * - Inicio con Google: el ID token de Google se canjea por una sesión
 *   de Supabase (`POST /auth/v1/token?grant_type=id_token`).
 * - Refresco: `POST /auth/v1/token?grant_type=refresh_token`.
 * - Cierre: `POST /auth/v1/logout`.
 * - Rol: `GET /rest/v1/users?id=eq.<uuid>` (RLS: solo la fila propia).
 * - Borrado de cuenta: `POST /rest/v1/rpc/delete_my_account`.
 *
 * La contraseña de Google nunca pasa por aquí: Credential Manager
 * entrega un ID token firmado por Google y eso es lo que se canjea.
 */
class SupabaseAuthApi(
    supabaseUrl: String,
    private val supabaseAnonKey: String
) {
    private val authApi: GoTrueApi
    private val restApi: UsersRestApi

    init {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val client = OkHttpClient.Builder().build()
        val base = supabaseUrl.trimEnd('/') + "/auth/v1/"
        authApi = Retrofit.Builder()
            .baseUrl(base)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GoTrueApi::class.java)
        restApi = Retrofit.Builder()
            .baseUrl(supabaseUrl.trimEnd('/') + "/rest/v1/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(UsersRestApi::class.java)
    }

    /** Canjea el ID token de Google por una sesión de Supabase. */
    suspend fun signInWithGoogleIdToken(idToken: String): GoTrueSessionResponse =
        authApi.signInWithIdToken(
            apikey = supabaseAnonKey,
            body = IdTokenRequest(provider = "google", idToken = idToken)
        )

    /** Refresca una sesión vencida. */
    suspend fun refreshSession(refreshToken: String): GoTrueSessionResponse =
        authApi.refreshSession(
            apikey = supabaseAnonKey,
            body = RefreshRequest(refreshToken = refreshToken)
        )

    /** Cierra la sesión en el servidor (best-effort: ignorar errores). */
    suspend fun signOut(accessToken: String) {
        runCatching {
            authApi.signOut(
                apikey = supabaseAnonKey,
                authorization = "Bearer $accessToken"
            )
        }
    }

    /** Lee la fila `users` del usuario autenticado (su rol incluido). */
    suspend fun fetchUserRow(userId: String, accessToken: String): UserRow? =
        restApi.userRow(
            apikey = supabaseAnonKey,
            authorization = "Bearer $accessToken",
            idFilter = "eq.$userId"
        ).firstOrNull()

    /** Ejecuta `delete_my_account()` (anonimiza y borra datos propios). */
    suspend fun deleteMyAccount(accessToken: String) {
        restApi.deleteMyAccount(
            apikey = supabaseAnonKey,
            authorization = "Bearer $accessToken",
            body = emptyMap()
        )
    }

    /** Endpoints de GoTrue. */
    interface GoTrueApi {
        @POST("token?grant_type=id_token")
        suspend fun signInWithIdToken(
            @Header("apikey") apikey: String,
            @Body body: IdTokenRequest
        ): GoTrueSessionResponse

        @POST("token?grant_type=refresh_token")
        suspend fun refreshSession(
            @Header("apikey") apikey: String,
            @Body body: RefreshRequest
        ): GoTrueSessionResponse

        @POST("logout")
        suspend fun signOut(
            @Header("apikey") apikey: String,
            @Header("Authorization") authorization: String
        )
    }

    /** Endpoints PostgREST para la fila `users` y el borrado de cuenta. */
    interface UsersRestApi {
        @GET("users")
        suspend fun userRow(
            @Header("apikey") apikey: String,
            @Header("Authorization") authorization: String,
            @Query("id") idFilter: String,
            @Query("select") select: String = "id,email,display_name,photo_url,role"
        ): List<UserRow>

        @POST("rpc/delete_my_account")
        suspend fun deleteMyAccount(
            @Header("apikey") apikey: String,
            @Header("Authorization") authorization: String,
            @Body body: Map<String, String>
        )
    }

    // --- DTOs ---

    @JsonClass(generateAdapter = true)
    data class IdTokenRequest(
        val provider: String,
        @Json(name = "id_token") val idToken: String
    )

    @JsonClass(generateAdapter = true)
    data class RefreshRequest(
        @Json(name = "refresh_token") val refreshToken: String
    )

    @JsonClass(generateAdapter = true)
    data class GoTrueUser(
        val id: String,
        val email: String?
    )

    @JsonClass(generateAdapter = true)
    data class GoTrueSessionResponse(
        @Json(name = "access_token") val accessToken: String,
        @Json(name = "refresh_token") val refreshToken: String,
        @Json(name = "expires_in") val expiresIn: Long,
        @Json(name = "expires_at") val expiresAt: Long?,
        val user: GoTrueUser?
    ) {
        /** Sesión lista para guardar (vencimiento en milisegundos). */
        fun toSession(nowMillis: Long = System.currentTimeMillis()): AuthSession =
            AuthSession(
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresAtMillis = expiresAt?.let { it * 1_000L }
                    ?: (nowMillis + expiresIn * 1_000L),
                userId = user?.id ?: "",
                email = user?.email ?: ""
            )
    }

    @JsonClass(generateAdapter = true)
    data class UserRow(
        val id: String,
        val email: String,
        @Json(name = "display_name") val displayName: String?,
        @Json(name = "photo_url") val photoUrl: String?,
        val role: String?
    ) {
        fun toAppUser() = AppUser(
            id = id,
            email = email,
            displayName = displayName,
            photoUrl = photoUrl,
            role = UserRole.fromString(role)
        )
    }
}
