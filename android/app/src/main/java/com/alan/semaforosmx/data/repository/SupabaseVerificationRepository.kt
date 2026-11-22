package com.alan.semaforosmx.data.repository

import com.alan.semaforosmx.data.model.VerificationRequest
import com.alan.semaforosmx.data.model.VerificationStatus
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Cliente REST de Supabase para la verificación de conductor
 * (tabla `verification_requests` + bucket privado `license-photos`,
 * migración 003).
 *
 * Flujo de envío: (1) la foto se sube al bucket privado en la ruta
 * '{user_id}/{archivo}' (RLS: el dueño solo sube en su carpeta; solo
 * el Admin puede verla); (2) se inserta la fila en estado PENDING con
 * el consentimiento expreso (`consent_at`, LFPDPPP art. 9). La foto
 * se borra tras la revisión del Admin (panel de Supabase +
 * `resolve_verification()`); en la app nunca se descarga.
 *
 * Sigue el mismo patrón que [SupabaseLightMessagesRepository]: el
 * interceptor agrega la clave anónima y el JWT del usuario.
 */
class SupabaseVerificationRepository(
    supabaseUrl: String,
    supabaseAnonKey: String,
    private val tokenProvider: () -> String? = { null }
) : VerificationRepository {

    private val api: VerificationApi
    private val storageApi: StorageApi

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
        val base = supabaseUrl.trimEnd('/')
        api = Retrofit.Builder()
            .baseUrl("$base/rest/v1/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(VerificationApi::class.java)
        storageApi = Retrofit.Builder()
            .baseUrl("$base/storage/v1/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(StorageApi::class.java)
    }

    override suspend fun myRequests(userId: String): List<VerificationRequest> =
        api.myRequests("eq.$userId").map { it.toDomain() }

    override suspend fun submit(
        userId: String,
        licenseType: String,
        photoBytes: ByteArray,
        fileName: String,
        consentAtMillis: Long
    ): VerificationRequest {
        // Una sola solicitud pendiente por usuario (índice parcial
        // único en la base de datos; aquí se revisa antes de subir).
        val existing = myRequests(userId)
        if (existing.any { it.status == VerificationStatus.PENDING }) {
            throw IllegalStateException("Ya hay una solicitud en revisión")
        }

        // 1) Subir la foto al bucket privado: '{user_id}/{archivo}'.
        val photoPath = "$userId/$fileName"
        val body = photoBytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
        val upload = storageApi.uploadObject(
            bucket = BUCKET,
            path = photoPath,
            body = body,
            contentType = "image/jpeg"
        )
        if (!upload.isSuccessful) {
            throw IllegalStateException("No se pudo subir la foto (${upload.code()})")
        }

        // 2) Insertar la fila en PENDING con el consentimiento.
        val row = VerificationRow(
            id = null,
            userId = userId,
            licenseType = licenseType,
            photoPath = photoPath,
            status = VerificationStatus.PENDING.name,
            submittedAt = null,
            consentAt = OffsetDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(consentAtMillis), ZoneOffset.UTC
            ).toString(),
            reviewedAt = null,
            rejectionReason = null
        )
        return api.insertRequest(row).first().toDomain()
    }

    /** Endpoints PostgREST (tabla) y Storage (bucket). */
    interface VerificationApi {
        @GET("verification_requests")
        suspend fun myRequests(
            @Query("user_id") userFilter: String,
            @Query("order") order: String = "submitted_at.desc"
        ): List<VerificationRow>

        @POST("verification_requests")
        suspend fun insertRequest(
            @Body row: VerificationRow,
            @Header("Prefer") prefer: String = "return=representation"
        ): List<VerificationRow>
    }

    interface StorageApi {
        @POST("object/{bucket}/{path}")
        suspend fun uploadObject(
            @Path("bucket") bucket: String,
            // La ruta contiene una diagonal ({user_id}/{archivo}):
            // encoded = true para no codificarla.
            @Path(value = "path", encoded = true) path: String,
            @Body body: okhttp3.RequestBody,
            @Header("Content-Type") contentType: String
        ): Response<Unit>
    }

    @JsonClass(generateAdapter = true)
    data class VerificationRow(
        val id: String?,
        @Json(name = "user_id") val userId: String?,
        @Json(name = "license_type") val licenseType: String?,
        @Json(name = "photo_path") val photoPath: String?,
        val status: String?,
        @Json(name = "submitted_at") val submittedAt: String?,
        @Json(name = "consent_at") val consentAt: String?,
        @Json(name = "reviewed_at") val reviewedAt: String?,
        @Json(name = "rejection_reason") val rejectionReason: String?
    ) {
        fun toDomain() = VerificationRequest(
            id = id.orEmpty(),
            userId = userId.orEmpty(),
            licenseType = licenseType.orEmpty(),
            photoPath = photoPath,
            status = runCatching { VerificationStatus.valueOf(status.orEmpty()) }
                .getOrDefault(VerificationStatus.PENDING),
            submittedAtMillis = SupabaseLightMessagesRepository.parseIso(submittedAt),
            consentAtMillis = consentAt?.let { SupabaseLightMessagesRepository.parseIso(it) },
            reviewedAtMillis = reviewedAt?.let { SupabaseLightMessagesRepository.parseIso(it) },
            rejectionReason = rejectionReason
        )
    }

    companion object {
        const val BUCKET = "license-photos"
    }
}
