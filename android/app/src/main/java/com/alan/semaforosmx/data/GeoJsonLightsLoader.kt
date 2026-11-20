package com.alan.semaforosmx.data

import android.content.Context
import com.alan.semaforosmx.data.model.TrafficLight
import org.json.JSONObject
import java.io.BufferedReader

/**
 * Carga la base semilla de semáforos desde el GeoJSON exportado de
 * OpenStreetMap (overpass-turbo, 2025-11-16; actualizada con Overpass
 * el 2026-11-19).
 *
 * Fuente original: `export (1).geojson` del borrador de Alan
 * (https://github.com/4vazquez/app-semaforos) — 1 579 semáforos del
 * área metropolitana de Monterrey (lat 25.57–25.83, lon −100.50 a
 * −100.00). Actualización 2026-11-19: consulta fresca a Overpass
 * (1 607 nodos en OSM): +31 semáforos nuevos, coordenadas refrescadas
 * para los existentes y 3 nodos ya ausentes en OSM conservados.
 * Total: 1 610 semáforos.
 *
 * Licencia de los datos: OpenStreetMap, ODbL
 * (© OpenStreetMap contributors). La atribución se muestra en el mapa
 * (osmdroid) y en la pantalla "Acerca de".
 *
 * El archivo vive en `assets/semaforos_mty.geojson` (~500 KB) y se carga
 * una sola vez al arrancar la app.
 */
object GeoJsonLightsLoader {

    const val ASSET_FILE = "semaforos_mty.geojson"

    /**
     * Lee el GeoJSON y devuelve la lista de semáforos.
     * El `id` de cada semáforo es el ID del nodo de OSM (p. ej.
     * "node/289739626"), estable entre exportaciones.
     */
    fun load(context: Context): List<TrafficLight> {
        val text = context.assets.open(ASSET_FILE).bufferedReader().use(
            BufferedReader::readText
        )
        return parse(text)
    }

    /** Parsea el texto GeoJSON (separable para pruebas). */
    fun parse(geoJson: String): List<TrafficLight> {
        val result = ArrayList<TrafficLight>()
        val root = JSONObject(geoJson)
        val features = root.optJSONArray("features") ?: return result
        for (i in 0 until features.length()) {
            val feature = features.optJSONObject(i) ?: continue
            val geometry = feature.optJSONObject("geometry") ?: continue
            if (geometry.optString("type") != "Point") continue
            val coords = geometry.optJSONArray("coordinates") ?: continue
            if (coords.length() < 2) continue
            val lon = coords.optDouble(0, Double.NaN)
            val lat = coords.optDouble(1, Double.NaN)
            if (lat.isNaN() || lon.isNaN()) continue
            // Solo el área metropolitana de Monterrey (filtro de seguridad).
            if (lat !in 25.0..26.5 || lon !in -101.0..-99.5) continue
            val id = feature.optString("id").ifBlank { "osm-$i" }
            result.add(
                TrafficLight(
                    id = id,
                    latitude = lat,
                    longitude = lon,
                    name = null, // El GeoJSON de OSM no trae nombres de cruces.
                    city = "Monterrey"
                )
            )
        }
        return result
    }
}
