package com.alan.semaforosmx.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.alan.semaforosmx.R

/**
 * Miniaturas de vehículo para «Mi ubicación» en el mapa (decisión de
 * Alan, 2026-11-20): vista cenital, estilo propio (verde Vía Verde),
 * NADA de copiar la flechita de Google/Waze. El usuario escoge su
 * vehículo en Ajustes; el marcador gira con el rumbo GPS.
 *
 * Los íconos se dibujan con Canvas (siluetas cenitales apuntando al
 * norte) para mantener un estilo consistente sin depender de assets.
 */
enum class VehicleType(val labelRes: Int) {
    SEDAN(R.string.vehicle_sedan),
    PICKUP(R.string.vehicle_pickup),
    SUV(R.string.vehicle_suv),
    TRAILER(R.string.vehicle_trailer);

    companion object {
        fun fromName(name: String?): VehicleType =
            entries.firstOrNull { it.name == name } ?: SEDAN
    }
}

/** Preferencia del vehículo elegido (Ajustes → Mi vehículo). */
object VehiclePrefs {
    private const val PREFS = "semaforos_mx_prefs"
    private const val KEY = "vehicle_type"

    fun load(context: Context): VehicleType =
        VehicleType.fromName(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY, null)
        )

    fun save(context: Context, type: VehicleType) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, type.name).apply()
    }
}

object VehicleIcons {
    private val BODY = Color.parseColor("#2E7D32")
    private val BODY_DARK = Color.parseColor("#1B5E20")
    private val GLASS = Color.parseColor("#E3F2FD")
    private val DARK_GLASS = Color.parseColor("#263238")
    private val WHEEL = Color.parseColor("#212121")
    private val LIGHT = Color.parseColor("#FFF9C4")
    private val OUTLINE = Color.WHITE
    private val SHADOW = Color.argb(70, 0, 0, 0)

    /** Crea el ícono del vehículo como Drawable listo para el marcador. */
    fun createIcon(context: Context, type: VehicleType): Drawable =
        BitmapDrawable(context.resources, createIconBitmap(context, type))

    /** Dibuja la miniatura cenital del vehículo en un Bitmap. */
    fun createIconBitmap(context: Context, type: VehicleType): Bitmap {
        val density = context.resources.displayMetrics.density
        val scale = maxOf(density, 2f)
        val (w, h) = when (type) {
            VehicleType.SEDAN -> 64 to 112
            VehicleType.PICKUP -> 68 to 122
            VehicleType.SUV -> 70 to 110
            VehicleType.TRAILER -> 72 to 156
        }
        val bmp = Bitmap.createBitmap(
            (w * scale).toInt(), (h * scale).toInt(), Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bmp)
        canvas.scale(scale, scale)
        when (type) {
            VehicleType.SEDAN -> drawSedan(canvas)
            VehicleType.PICKUP -> drawPickup(canvas)
            VehicleType.SUV -> drawSuv(canvas)
            VehicleType.TRAILER -> drawTrailer(canvas)
        }
        return bmp
    }

    private fun fill(color: Int): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }

    private fun stroke(color: Int, width: Float): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = width
        }

    private fun roundRect(
        canvas: Canvas, l: Float, t: Float, r: Float, b: Float,
        radius: Float, paint: Paint
    ) {
        canvas.drawRoundRect(RectF(l, t, r, b), radius, radius, paint)
    }

    /** Cuerpo con sombra suave y contorno blanco. */
    private fun body(
        canvas: Canvas, l: Float, t: Float, r: Float, b: Float,
        radius: Float, color: Int = BODY
    ) {
        roundRect(canvas, l + 1, t + 3, r + 1, b + 3, radius, fill(SHADOW))
        roundRect(canvas, l, t, r, b, radius, fill(color))
        roundRect(canvas, l, t, r, b, radius, stroke(OUTLINE, 3f))
    }

    /** Ruedas (rectángulos redondeados oscuros) centradas en los puntos dados. */
    private fun wheels(
        canvas: Canvas, vararg centers: Pair<Float, Float>,
        w: Float = 8f, h: Float = 16f
    ) {
        val p = fill(WHEEL)
        for ((cx, cy) in centers) {
            roundRect(canvas, cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2, 3f, p)
        }
    }

    private fun headlights(canvas: Canvas, vararg centers: Pair<Float, Float>) {
        val p = fill(LIGHT)
        for ((cx, cy) in centers) {
            canvas.drawCircle(cx, cy, 3f, p)
        }
    }

    private fun drawSedan(canvas: Canvas) {
        wheels(canvas, 3f to 20f, 61f to 20f, 3f to 88f, 61f to 88f)
        body(canvas, 4f, 4f, 60f, 108f, 14f)
        // Cabina (techo/cristales)
        roundRect(canvas, 13f, 36f, 51f, 74f, 10f, fill(GLASS))
        // Parabrisas y medallón
        roundRect(canvas, 15f, 38f, 49f, 47f, 6f, fill(DARK_GLASS))
        roundRect(canvas, 15f, 65f, 49f, 73f, 6f, fill(DARK_GLASS))
        headlights(canvas, 14f to 10f, 50f to 10f)
    }

    private fun drawPickup(canvas: Canvas) {
        wheels(canvas, 3f to 22f, 65f to 22f, 3f to 92f, 65f to 92f)
        body(canvas, 4f, 4f, 64f, 118f, 12f)
        // Cabina
        roundRect(canvas, 12f, 18f, 56f, 58f, 10f, fill(GLASS))
        roundRect(canvas, 14f, 20f, 54f, 30f, 6f, fill(DARK_GLASS))
        // Caja (batea) trasera
        roundRect(canvas, 12f, 64f, 56f, 110f, 6f, fill(BODY_DARK))
        roundRect(canvas, 16f, 68f, 52f, 106f, 4f, stroke(DARK_GLASS, 2f))
        headlights(canvas, 14f to 10f, 54f to 10f)
    }

    private fun drawSuv(canvas: Canvas) {
        wheels(canvas, 3f to 20f, 67f to 20f, 3f to 86f, 67f to 86f)
        body(canvas, 4f, 4f, 66f, 106f, 16f)
        // Techo/cristales grande (la SUV es más cuadrada)
        roundRect(canvas, 11f, 20f, 59f, 90f, 12f, fill(GLASS))
        roundRect(canvas, 13f, 22f, 57f, 32f, 6f, fill(DARK_GLASS))
        roundRect(canvas, 13f, 80f, 57f, 90f, 6f, fill(DARK_GLASS))
        headlights(canvas, 15f to 10f, 55f to 10f)
    }

    private fun drawTrailer(canvas: Canvas) {
        wheels(
            canvas,
            7f to 16f, 65f to 16f,
            5f to 118f, 67f to 118f, 5f to 138f, 67f to 138f,
            w = 8f, h = 14f
        )
        // Caja de carga (larga, un poco más oscura)
        body(canvas, 6f, 52f, 66f, 150f, 6f, BODY_DARK)
        // Líneas de panel en la caja
        roundRect(canvas, 10f, 84f, 62f, 87f, 1.5f, fill(Color.argb(60, 0, 0, 0)))
        roundRect(canvas, 10f, 116f, 62f, 119f, 1.5f, fill(Color.argb(60, 0, 0, 0)))
        // Cabina
        body(canvas, 10f, 4f, 62f, 46f, 10f)
        roundRect(canvas, 14f, 8f, 58f, 18f, 6f, fill(DARK_GLASS))
        headlights(canvas, 18f to 10f, 54f to 10f)
    }
}
