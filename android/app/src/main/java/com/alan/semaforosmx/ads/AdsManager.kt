package com.alan.semaforosmx.ads

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.alan.semaforosmx.data.model.UserContribution
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

/**
 * Monetización del MVP: banner de AdMob en la parte inferior de la
 * pantalla del mapa. Nunca dentro del widget flotante (distraería al
 * conductor).
 *
 * Tres niveles (ver SPEC.md §9):
 * 1. Gratis: app completa, con banner.
 * 2. Colaborador (GRATIS, sin banners, nunca se cobra): 20+ actualizaciones
 *    en semáforos distintos al mes. Su pago son los datos que aporta.
 * 3. Plus (suscripción de pago, se lanza DESPUÉS con buena base de
 *    usuarios): sin anuncios + extras (alertas personalizadas, estadísticas
 *    de manejo). En el MVP solo existe el flag [isPlusUser] como stub; el
 *    flujo de pago con Google Play Billing se integra en v1+.
 *
 * - El ID de unidad es el de PRUEBA oficial de Google. Cambiar por el ID
 *   real de la cuenta de AdMob de Alan antes de publicar (ver README).
 */
object AdsManager {

    /** ID de unidad de banner de PRUEBA de Google (siempre válido). */
    const val TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"

    /** Cambiar a true y poner el ID real al publicar. */
    const val USE_REAL_ADS = false
    const val REAL_BANNER_AD_UNIT_ID = ""

    val bannerAdUnitId: String
        get() = if (USE_REAL_ADS) REAL_BANNER_AD_UNIT_ID else TEST_BANNER_AD_UNIT_ID

    /**
     * ¿Este usuario tiene Plus (suscripción de pago)? STUB del MVP:
     * siempre false. En v1+ se conecta con Google Play Billing (la compra
     * se verifica en el backend y se guarda el entitlement aquí).
     */
    fun isPlusUser(): Boolean = false

    /**
     * ¿Se muestra el banner? Solo a usuarios Gratis: los Colaboradores
     * (20+ semáforos distintos al mes) y los Plus no ven anuncios.
     */
    fun shouldShowAds(contribution: UserContribution): Boolean =
        !contribution.isAdFree && !isPlusUser()
}

@Composable
fun BannerAd(modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context ->
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = AdsManager.bannerAdUnitId
                loadAd(AdRequest.Builder().build())
            }
        },
        modifier = modifier.fillMaxWidth()
    )
}
