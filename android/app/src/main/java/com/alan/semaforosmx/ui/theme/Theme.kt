package com.alan.semaforosmx.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Tema v1.0 — «semáforo»: la marca manda.
 *
 * - Verde señal como primario (el verde del semáforo, no un verde
 *   genérico); ámbar como terciario y rojo semáforo como error.
 * - Claro: fondo blanco verdoso suave, superficies blancas.
 * - Oscuro: fondo verde-negro profundo, primario verde brillante para
 *   que las acciones sigan resaltando de noche (la app se usa al manejar).
 * - El modo oscuro sigue al sistema (antes el tema oscuro existía pero
 *   nada lo activaba: MainActivity llamaba al tema sin parámetro).
 * - Tipografía: títulos en negrita/semibold para jerarquía clara;
 *   el resto usa la familia del sistema (sin fuentes nuevas).
 * - Formas redondeadas (12/16/24 dp): tarjetas y botones más amables.
 */

// Verde señal (primario claro) y su versión brillante para oscuro.
private val SignalGreen = Color(0xFF0E7C3A)
private val SignalGreenBright = Color(0xFF4ADE80)

// Ámbar del semáforo (terciario) y rojo semáforo (error).
private val AmberSignal = Color(0xFFB26A00)
private val AmberSignalBright = Color(0xFFFFC94D)
private val RedSignal = Color(0xFFC62828)
private val RedSignalBright = Color(0xFFFFB4AB)

private val LightColors = lightColorScheme(
    primary = SignalGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB9F0C9),
    onPrimaryContainer = Color(0xFF053B1B),
    secondary = Color(0xFF3E7C59),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3EBDB),
    onSecondaryContainer = Color(0xFF0C2E1B),
    tertiary = AmberSignal,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE3A8),
    onTertiaryContainer = Color(0xFF4A2C00),
    error = RedSignal,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF4F7F4),
    onBackground = Color(0xFF191C19),
    surface = Color.White,
    onSurface = Color(0xFF191C19),
    surfaceVariant = Color(0xFFDEE5DE),
    onSurfaceVariant = Color(0xFF414941),
    outline = Color(0xFFB9C0B9),
    outlineVariant = Color(0xFFD5DBD5),
    surfaceTint = SignalGreen,
    inverseSurface = Color(0xFF2D312D),
    inverseOnSurface = Color(0xFFEFF2EF),
    inversePrimary = SignalGreenBright,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8FAF8),
    surfaceContainer = Color(0xFFEDF0ED),
    surfaceContainerHigh = Color(0xFFE7EAE7),
    surfaceContainerHighest = Color(0xFFE1E4E1)
)

private val DarkColors = darkColorScheme(
    primary = SignalGreenBright,
    onPrimary = Color(0xFF00391A),
    primaryContainer = Color(0xFF14532D),
    onPrimaryContainer = Color(0xFFBBF7D0),
    secondary = Color(0xFF7ED6A4),
    onSecondary = Color(0xFF003823),
    secondaryContainer = Color(0xFF1E4D33),
    onSecondaryContainer = Color(0xFFC6EDD6),
    tertiary = AmberSignalBright,
    onTertiary = Color(0xFF3E2A00),
    tertiaryContainer = Color(0xFF5C4300),
    onTertiaryContainer = Color(0xFFFFE3A8),
    error = RedSignalBright,
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0D130E),
    onBackground = Color(0xFFE2E6E2),
    surface = Color(0xFF131A14),
    onSurface = Color(0xFFE2E6E2),
    surfaceVariant = Color(0xFF2B352C),
    onSurfaceVariant = Color(0xFFBFC9BF),
    outline = Color(0xFF5A665A),
    outlineVariant = Color(0xFF3A453A),
    surfaceTint = SignalGreenBright,
    inverseSurface = Color(0xFFE2E6E2),
    inverseOnSurface = Color(0xFF2D312D),
    inversePrimary = SignalGreen,
    surfaceContainerLowest = Color(0xFF0A0F0B),
    surfaceContainerLow = Color(0xFF131A14),
    surfaceContainer = Color(0xFF171F18),
    surfaceContainerHigh = Color(0xFF222B22),
    surfaceContainerHighest = Color(0xFF2D372D)
)

private val SemaforosTypography = Typography(
    titleLarge = TextStyle(
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Bold
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Normal
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Normal
    ),
    labelLarge = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold
    )
)

private val SemaforosShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp)
)

@Composable
fun SemaforosMxTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = SemaforosTypography,
        shapes = SemaforosShapes,
        content = content
    )
}
