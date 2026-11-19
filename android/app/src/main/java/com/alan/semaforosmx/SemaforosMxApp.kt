package com.alan.semaforosmx

import android.app.Application
import com.alan.semaforosmx.data.auth.AuthManager
import com.alan.semaforosmx.data.repository.RepositoryProvider
import com.google.android.gms.ads.MobileAds
import org.osmdroid.config.Configuration

/**
 * Inicialización global de la app:
 * - osmdroid (mapa OpenStreetMap): agente de usuario obligatorio por la
 *   política de uso de mosaicos de OSM.
 * - AdMob: inicialización del SDK de anuncios.
 * - AuthManager: restaura la sesión de Google/Supabase si existe.
 * - Repositorio: siembra los 1 579 semáforos de Monterrey desde el GeoJSON.
 */
class SemaforosMxApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Configuration.getInstance().userAgentValue = packageName
        MobileAds.initialize(this)
        AuthManager.init(this)
        RepositoryProvider.init(this)
    }
}
