package com.alan.semaforosmx.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.alan.semaforosmx.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Actualizador propio (sideload) de Vía Verde.
 *
 * El usuario nunca tiene que descargar e instalar el APK a mano: la app
 * consulta el último release de GitHub, avisa si hay versión nueva y la
 * instala con un toque.
 *
 * ⚠️ POLÍTICA DE GOOGLE PLAY: auto-actualizarse desde fuera de Play está
 * PROHIBIDO para apps distribuidas en Play. Por eso todo el flujo está
 * detrás del flag [BuildConfig.SELF_UPDATE_ENABLED]: `true` en el build
 * debug/sideload, `false` en el build release/Play. Cuando la app se
 * publique en Play, la actualización la maneja Play (In-App Updates API,
 * flexible/inmediata) y este actualizador queda desactivado (ver SPEC.md
 * §13). No quitar el flag ni activarlo en release.
 *
 * Comportamiento:
 * - La red se consulta como máximo una vez al día (la hora del último
 *   chequeo persiste en SharedPreferences). Sin red o con error de la
 *   API, falla en silencio: no hay diálogo ni crash.
 * - Si hay versión nueva, queda registrada como "pendiente" y se muestra
 *   el diálogo al abrir la app. «Más tarde» la silencia 24 h para esa
 *   versión; una versión distinta vuelve a avisar.
 * - La descarga muestra progreso; el instalador solo se lanza con el
 *   toque del usuario, nunca solo.
 */
object SelfUpdater {

    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/4vazquez/app-semaforos/releases/latest"
    private const val PREFS_NAME = "self_update_prefs"
    private const val KEY_LAST_CHECK_AT = "last_check_at"
    private const val KEY_DISMISSED_TAG = "dismissed_tag"
    private const val KEY_DISMISSED_AT = "dismissed_at"
    private const val KEY_PENDING_TAG = "pending_tag"
    private const val KEY_PENDING_VERSION = "pending_version"
    private const val KEY_PENDING_NAME = "pending_name"
    private const val KEY_PENDING_NOTES = "pending_notes"
    private const val KEY_PENDING_APK_URL = "pending_apk_url"
    private const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L // 24 h
    private const val APK_FILE_NAME = "update.apk"

    /** El actualizador solo existe en el build sideload (ver KDoc). */
    fun isEnabled(): Boolean = BuildConfig.SELF_UPDATE_ENABLED

    private val apiClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val downloadClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Busca una versión más nueva que la instalada. Devuelve el release
     * si hay que avisar, o null si no hay nada nuevo, si el actualizador
     * está desactivado, si ya se revisó hoy, si el usuario pospuso esta
     * versión hace menos de 24 h, o si hubo cualquier error de red/API.
     * Nunca lanza excepciones. Llamar desde un hilo IO.
     */
    suspend fun checkForUpdate(context: Context): ReleaseInfo? {
        if (!isEnabled()) return null
        val installed = installedVersion(context) ?: return null
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()

        // Si la versión instalada ya alcanzó (o superó) la pendiente, la
        // pendiente quedó obsoleta (el usuario ya actualizó): limpiarla.
        loadPending(prefs)?.let { pending ->
            if (pending.version <= installed) {
                clearPending(prefs)
            } else if (!isDismissedRecently(prefs, pending.tag, now)) {
                return pending // Avisar sin tocar la red.
            } else {
                return null // Pospuesta hace menos de 24 h.
            }
        }

        // Throttle de red: como máximo una consulta al día.
        val lastCheck = prefs.getLong(KEY_LAST_CHECK_AT, 0L)
        if (now - lastCheck < CHECK_INTERVAL_MS) return null

        val release = fetchLatestRelease() ?: return null
        prefs.edit().putLong(KEY_LAST_CHECK_AT, now).apply()
        if (release.version <= installed) {
            clearPending(prefs)
            return null
        }
        if (isDismissedRecently(prefs, release.tag, now)) return null
        savePending(prefs, release)
        return release
    }

    /**
     * «Más tarde»: silencia esta versión 24 h. Una versión distinta
     * vuelve a avisar aunque esta esté pospuesta.
     */
    fun dismiss(context: Context, tag: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_DISMISSED_TAG, tag)
            .putLong(KEY_DISMISSED_AT, System.currentTimeMillis())
            .apply()
        clearPending(prefs)
    }

    /** Olvida la actualización pendiente (al lanzar el instalador). */
    fun clearPendingUpdate(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        clearPending(prefs)
    }

    /**
     * Descarga el APK a la caché privada, con progreso 0f..1f (-1f si el
     * servidor no reporta tamaño). Devuelve el archivo si se descargó
     * completo y no está vacío; null ante cualquier error (el archivo
     * parcial se borra). Llamar desde un hilo IO.
     */
    suspend fun downloadApk(
        context: Context,
        url: String,
        onProgress: (Float) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val file = File(dir, APK_FILE_NAME)
        val ok = runCatching {
            val request = Request.Builder().url(url).build()
            downloadClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching false
                val body = response.body ?: return@runCatching false
                val total = body.contentLength()
                var downloaded = 0L
                body.byteStream().use { input ->
                    FileOutputStream(file).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            onProgress(
                                if (total > 0) downloaded.toFloat() / total else -1f
                            )
                        }
                        output.flush()
                    }
                }
                file.length() > 0
            }
        }.getOrDefault(false)
        if (ok) {
            file
        } else {
            // Descarga fallida o vacía: no dejar un archivo parcial.
            if (file.exists()) file.delete()
            null
        }
    }

    /**
     * ¿La app tiene permiso para instalar paquetes desconocidos? En
     * Android 8+ el usuario lo concede por app en Ajustes.
     */
    fun canInstallPackages(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    /** Abre los Ajustes del sistema para conceder el permiso de instalar. */
    fun openInstallPermissionSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        context.startActivity(intent)
    }

    /**
     * Lanza el instalador del sistema para el APK descargado. El usuario
     * confirma la instalación en el diálogo de Android; la app no instala
     * nada por su cuenta.
     */
    fun installApk(context: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /** Versión instalada, parseada del versionName del paquete. */
    fun installedVersion(context: Context): AppVersion? {
        return runCatching {
            val pm = context.packageManager
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, 0)
            }
            AppVersion.parse(info.versionName)
        }.getOrNull()
    }

    private fun fetchLatestRelease(): ReleaseInfo? {
        return runCatching {
            val request = Request.Builder()
                .url(LATEST_RELEASE_URL)
                .addHeader("Accept", "application/vnd.github+json")
                .build()
            apiClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val json = response.body?.string() ?: return null
                parseLatestRelease(json)
            }
        }.getOrNull()
    }

    private fun isDismissedRecently(
        prefs: android.content.SharedPreferences,
        tag: String,
        now: Long
    ): Boolean {
        val dismissedTag = prefs.getString(KEY_DISMISSED_TAG, null)
        val dismissedAt = prefs.getLong(KEY_DISMISSED_AT, 0L)
        return dismissedTag == tag && now - dismissedAt < CHECK_INTERVAL_MS
    }

    private fun loadPending(
        prefs: android.content.SharedPreferences
    ): ReleaseInfo? {
        val tag = prefs.getString(KEY_PENDING_TAG, null) ?: return null
        val version = AppVersion.parse(
            prefs.getString(KEY_PENDING_VERSION, null)
        ) ?: return null
        val apkUrl = prefs.getString(KEY_PENDING_APK_URL, null)
            ?.takeIf { it.isNotBlank() } ?: return null
        return ReleaseInfo(
            tag = tag,
            version = version,
            name = prefs.getString(KEY_PENDING_NAME, null),
            notes = prefs.getString(KEY_PENDING_NOTES, null),
            apkUrl = apkUrl
        )
    }

    private fun savePending(
        prefs: android.content.SharedPreferences,
        release: ReleaseInfo
    ) {
        prefs.edit()
            .putString(KEY_PENDING_TAG, release.tag)
            .putString(KEY_PENDING_VERSION, release.version.toString())
            .putString(KEY_PENDING_NAME, release.name)
            .putString(KEY_PENDING_NOTES, release.notes)
            .putString(KEY_PENDING_APK_URL, release.apkUrl)
            .apply()
    }

    private fun clearPending(prefs: android.content.SharedPreferences) {
        prefs.edit()
            .remove(KEY_PENDING_TAG)
            .remove(KEY_PENDING_VERSION)
            .remove(KEY_PENDING_NAME)
            .remove(KEY_PENDING_NOTES)
            .remove(KEY_PENDING_APK_URL)
            .apply()
    }
}
