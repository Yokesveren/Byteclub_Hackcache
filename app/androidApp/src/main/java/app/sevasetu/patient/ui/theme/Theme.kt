package app.sevasetu.patient.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Color Tokens
val ColorPrimary = Color(0xFF0B6E6E)
val ColorOnPrimary = Color(0xFFFFFFFF)
val ColorPrimaryContainer = Color(0xFFD6EFEF)
val ColorOnPrimaryContainer = Color(0xFF063F3F)

val ColorBackground = Color(0xFFF6FAF9)
val ColorSurface = Color(0xFFFFFFFF)
val ColorOnSurface = Color(0xFF17211F)
val ColorOnSurfaceVariant = Color(0xFF4A5A57)
val ColorOutline = Color(0xFFB8C7C4)

val ColorEmergency = Color(0xFFC62828)
val ColorError = Color(0xFFB3261E)

val ColorRoutineContainer = Color(0xFFDDF3E4)
val ColorRoutineContent = Color(0xFF1B5E34)

val ColorPriorityContainer = Color(0xFFFFF1D6)
val ColorPriorityContent = Color(0xFF8A4B00)

val ColorEmergencyContainer = Color(0xFFFDE3E1)
val ColorEmergencyContent = Color(0xFF8E1B16)

val ColorOfflineContainer = Color(0xFFFFF1D6)
val ColorOfflineContent = Color(0xFF8A4B00)

// High Contrast Theme Colors
val ColorHighContrastPrimary = Color(0xFF004D4D)
val ColorHighContrastEmergency = Color(0xFFB00020)

data class ExtraThemeColors(
    val isHighContrast: Boolean = false,
    val routineContainer: Color = ColorRoutineContainer,
    val routineContent: Color = ColorRoutineContent,
    val priorityContainer: Color = ColorPriorityContainer,
    val priorityContent: Color = ColorPriorityContent,
    val emergencyContainer: Color = ColorEmergencyContainer,
    val emergencyContent: Color = ColorEmergencyContent,
    val offlineContainer: Color = ColorOfflineContainer,
    val offlineContent: Color = ColorOfflineContent,
    val emergencyBar: Color = ColorEmergency
)

val LocalExtraColors = staticCompositionLocalOf { ExtraThemeColors() }
val LocalTextScaleFactor = staticCompositionLocalOf { 1.0f }

val StandardLightColorScheme = lightColorScheme(
    primary = ColorPrimary,
    onPrimary = ColorOnPrimary,
    primaryContainer = ColorPrimaryContainer,
    onPrimaryContainer = ColorOnPrimaryContainer,
    background = ColorBackground,
    surface = ColorSurface,
    onSurface = ColorOnSurface,
    onSurfaceVariant = ColorOnSurfaceVariant,
    outline = ColorOutline,
    error = ColorError
)

val HighContrastColorScheme = lightColorScheme(
    primary = ColorHighContrastPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2F1),
    onPrimaryContainer = Color.Black,
    background = Color.White,
    surface = Color.White,
    onSurface = Color.Black,
    onSurfaceVariant = Color.Black,
    outline = Color.Black,
    error = ColorHighContrastEmergency
)

@Composable
fun SevaTheme(
    textScaleFactor: Float = 1.0f,
    isHighContrast: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (isHighContrast) HighContrastColorScheme else StandardLightColorScheme
    val extraColors = if (isHighContrast) {
        ExtraThemeColors(
            isHighContrast = true,
            emergencyBar = ColorHighContrastEmergency
        )
    } else {
        ExtraThemeColors()
    }

    val scaledTypography = Typography(
        bodyLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontSize = (18 * textScaleFactor).sp,
            lineHeight = (26 * textScaleFactor).sp,
            fontWeight = FontWeight.Normal,
            color = colorScheme.onSurface
        ),
        titleLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontSize = (24 * textScaleFactor).sp,
            lineHeight = (32 * textScaleFactor).sp,
            fontWeight = FontWeight.Bold,
            color = colorScheme.onSurface
        ),
        headlineLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontSize = (28 * textScaleFactor).sp,
            lineHeight = (36 * textScaleFactor).sp,
            fontWeight = FontWeight.Bold,
            color = colorScheme.onSurface
        ),
        displayLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontSize = (56 * textScaleFactor).sp,
            lineHeight = (64 * textScaleFactor).sp,
            fontWeight = FontWeight.Bold,
            color = colorScheme.onSurface
        )
    )

    CompositionLocalProvider(
        LocalExtraColors provides extraColors,
        LocalTextScaleFactor provides textScaleFactor
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = scaledTypography,
            content = content
        )
    }
}
