package com.alan.semaforosmx.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pruebas del parseo de la respuesta de `releases/latest` de GitHub
 * (SPEC.md §13): solo importa el tag, las notas y el asset .apk.
 */
class ReleaseParsingTest {

    private fun releaseJson(
        tag: String = "v0.3.0",
        name: String? = "Vía Verde v0.3.0",
        body: String? = "Actualización automática",
        assets: String = """
            [{"name": "app-debug.apk",
              "browser_download_url": "https://github.com/4vazquez/app-semaforos/releases/download/v0.3.0/app-debug.apk"}]
        """.trimIndent()
    ): String = """
        {"tag_name": ${tag.toJsonString()},
         "name": ${name.toJsonString()},
         "body": ${body.toJsonString()},
         "assets": $assets}
    """.trimIndent()

    private fun String?.toJsonString(): String =
        if (this == null) "null" else "\"$this\""

    @Test
    fun `parsea release valido con apk`() {
        val release = parseLatestRelease(releaseJson())
        assertEquals("v0.3.0", release?.tag)
        assertEquals(AppVersion(0, 3, 0), release?.version)
        assertEquals("Vía Verde v0.3.0", release?.name)
        assertEquals("Actualización automática", release?.notes)
        assertEquals(
            "https://github.com/4vazquez/app-semaforos/releases/download/v0.3.0/app-debug.apk",
            release?.apkUrl
        )
    }

    @Test
    fun `elige el asset apk entre varios`() {
        val json = releaseJson(
            assets = """
                [{"name": "checksums.txt",
                  "browser_download_url": "https://example.com/checksums.txt"},
                 {"name": "app-debug.apk",
                  "browser_download_url": "https://example.com/app-debug.apk"}]
            """.trimIndent()
        )
        assertEquals("https://example.com/app-debug.apk", parseLatestRelease(json)?.apkUrl)
    }

    @Test
    fun `sin asset apk devuelve null`() {
        val json = releaseJson(
            assets = """
                [{"name": "checksums.txt",
                  "browser_download_url": "https://example.com/checksums.txt"}]
            """.trimIndent()
        )
        assertNull(parseLatestRelease(json))
    }

    @Test
    fun `sin assets devuelve null`() {
        assertNull(parseLatestRelease(releaseJson(assets = "[]")))
    }

    @Test
    fun `tag malformado devuelve null`() {
        assertNull(parseLatestRelease(releaseJson(tag = "release-candidate")))
    }

    @Test
    fun `tag ausente devuelve null`() {
        val json = """
            {"name": "Sin tag", "body": null,
             "assets": [{"name": "app-debug.apk",
                         "browser_download_url": "https://example.com/a.apk"}]}
        """.trimIndent()
        assertNull(parseLatestRelease(json))
    }

    @Test
    fun `json invalido devuelve null sin lanzar`() {
        assertNull(parseLatestRelease("esto no es json"))
        assertNull(parseLatestRelease(""))
        assertNull(parseLatestRelease("{}"))
    }

    @Test
    fun `notas nulas quedan nulas`() {
        val release = parseLatestRelease(releaseJson(body = null))
        assertEquals("v0.3.0", release?.tag)
        assertNull(release?.notes)
    }

    @Test
    fun `url de descarga vacia devuelve null`() {
        val json = releaseJson(
            assets = """
                [{"name": "app-debug.apk", "browser_download_url": ""}]
            """.trimIndent()
        )
        assertNull(parseLatestRelease(json))
    }
}
