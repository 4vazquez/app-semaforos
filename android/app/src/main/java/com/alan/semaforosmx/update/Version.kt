package com.alan.semaforosmx.update

/**
 * Versión semántica (mayor.menor.parche) para comparar la versión
 * instalada con la de un release de GitHub.
 *
 * Lógica pura, sin dependencias de Android: se prueba con JUnit local.
 */
data class AppVersion(val major: Int, val minor: Int, val patch: Int) :
    Comparable<AppVersion> {

    override fun compareTo(other: AppVersion): Int =
        compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        /**
         * Parsea "v0.3.0" o "0.3.0". Devuelve null si el texto no tiene
         * el formato exacto X.Y.Z con números no negativos (el prefijo
         * "v" es opcional). Los tags de release deben seguir este formato
         * para que el actualizador los reconozca; cualquier otra cosa
         * (sufijos -beta, dos partes, texto) se ignora en silencio.
         */
        fun parse(raw: String?): AppVersion? {
            val cleaned = raw?.trim()?.removePrefix("v")?.removePrefix("V")
                ?: return null
            if (cleaned.isEmpty()) return null
            val parts = cleaned.split(".")
            if (parts.size != 3) return null
            val numbers = parts.map { it.toIntOrNull() ?: return null }
            if (numbers.any { it < 0 }) return null
            return AppVersion(numbers[0], numbers[1], numbers[2])
        }
    }
}
