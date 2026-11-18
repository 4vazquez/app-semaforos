package com.alan.semaforosmx.update

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/**
 * Un release de GitHub con su APK, listo para actualizar.
 *
 * @param tag Tag del release tal como viene de GitHub ("v0.3.0").
 * @param version Versión parseada del tag.
 * @param name Nombre del release (puede ser null).
 * @param notes Notas del release (cuerpo, puede ser null o vacío).
 * @param apkUrl URL de descarga del asset .apk (`browser_download_url`).
 */
data class ReleaseInfo(
    val tag: String,
    val version: AppVersion,
    val name: String?,
    val notes: String?,
    val apkUrl: String
)

/**
 * Respuesta mínima de `GET /repos/{owner}/{repo}/releases/latest`.
 * Solo se mapean los campos que usa el actualizador; el resto de la
 * respuesta de GitHub se ignora.
 */
@JsonClass(generateAdapter = false)
private data class GithubReleaseJson(
    @Json(name = "tag_name") val tagName: String?,
    @Json(name = "name") val name: String?,
    @Json(name = "body") val body: String?,
    @Json(name = "assets") val assets: List<GithubAssetJson>?
)

@JsonClass(generateAdapter = false)
private data class GithubAssetJson(
    @Json(name = "name") val name: String?,
    @Json(name = "browser_download_url") val browserDownloadUrl: String?
)

private val releaseMoshi: Moshi by lazy {
    Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
}

private val releaseAdapter by lazy {
    releaseMoshi.adapter(GithubReleaseJson::class.java)
}

/**
 * Parsea el JSON de `releases/latest` de GitHub. Devuelve null si:
 * el JSON es inválido, no hay tag válido (formato vX.Y.Z) o el release
 * no trae ningún asset .apk. Lógica pura (sin Android): se prueba con
 * JUnit local. Nunca lanza excepciones.
 */
fun parseLatestRelease(json: String): ReleaseInfo? {
    return runCatching {
        val release = releaseAdapter.fromJson(json) ?: return null
        val version = AppVersion.parse(release.tagName) ?: return null
        val apk = release.assets
            ?.firstOrNull { it.name?.endsWith(".apk", ignoreCase = true) == true }
            ?: return null
        val apkUrl = apk.browserDownloadUrl?.takeIf { it.isNotBlank() } ?: return null
        ReleaseInfo(
            tag = release.tagName!!,
            version = version,
            name = release.name,
            notes = release.body?.takeIf { it.isNotBlank() },
            apkUrl = apkUrl
        )
    }.getOrNull()
}
