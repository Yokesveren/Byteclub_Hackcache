package app.sevasetu.patient.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.sevasetu.patient.R

// The SevaSetu website's hospital palette (web/src/app/globals.css): navy ink and frame, scrub teal for actions,
// clinical mint highlights, ward-blue and linen fields. Status colours (emergency red, priority amber, routine
// green) are separate and never reused as brand.
val Frame = Color(0xFF0B2A3C)
val Ink = Color(0xFF0B2A3C)
val Muted = Color(0xFF56636B)
val Accent = Color(0xFF0B7A75)
val AccentPressed = Color(0xFF08615D)
val Mint = Color(0xFF9FE5CF)
val Ward = Color(0xFFDCECF7)
val Linen = Color(0xFFEDF3F8)
val Raised = Color(0xFFF5F8FA)
val Field = Color(0xFFF1F4F6)
val Edge = Color(0xFFE1E7EC)
val Control = Color(0xFFC9D2D9)
val NightRaised = Color(0xFF1C4A60)

// Status (web/src/components/BandChip.tsx): pale tint, dark text of the same hue, a word and an icon every time.
val ColorEmergency = Color(0xFFC4242A)
val ColorError = Color(0xFFA3161C)
val ColorRoutineContainer = Color(0xFFDCF5E4)
val ColorRoutineContent = Color(0xFF0D3B1E)
val ColorRoutineEdge = Color(0xFF5FB47F)
val ColorPriorityContainer = Color(0xFFFFF1C2)
val ColorPriorityContent = Color(0xFF5C3D00)
val ColorPriorityEdge = Color(0xFFD9A400)
val ColorEmergencyContainer = Color(0xFFFFEDEA)
val ColorEmergencyContent = Color(0xFFA3161C)
val ColorOfflineContainer = ColorPriorityContainer
val ColorOfflineContent = ColorPriorityContent

// Kept for screens that reference the old names.
val ColorPrimary = Accent
val ColorHighContrastPrimary = Color(0xFF004A46)
val ColorHighContrastEmergency = Color(0xFF9E0A10)

data class ExtraThemeColors(
    val isHighContrast: Boolean = false,
    val routineContainer: Color = ColorRoutineContainer,
    val routineContent: Color = ColorRoutineContent,
    val routineEdge: Color = ColorRoutineEdge,
    val priorityContainer: Color = ColorPriorityContainer,
    val priorityContent: Color = ColorPriorityContent,
    val priorityEdge: Color = ColorPriorityEdge,
    val emergencyContainer: Color = ColorEmergencyContainer,
    val emergencyContent: Color = ColorEmergencyContent,
    val offlineContainer: Color = ColorOfflineContainer,
    val offlineContent: Color = ColorOfflineContent,
    val emergencyBar: Color = ColorEmergency,
    val frame: Color = Frame,
    val field: Color = Field,
    val edge: Color = Edge,
)

val LocalExtraColors = staticCompositionLocalOf { ExtraThemeColors() }
val LocalTextScaleFactor = staticCompositionLocalOf { 1.0f }

/** Chivo 400/700/900, the one face across the website and the dashboard. Hindi and Tamil fall back to the system font. */
val Chivo = FontFamily(
    Font(R.font.chivo_regular, FontWeight.Normal),
    Font(R.font.chivo_bold, FontWeight.Bold),
    Font(R.font.chivo_black, FontWeight.Black),
)

val StandardLightColorScheme = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = Mint,
    onPrimaryContainer = Ink,
    secondary = Frame,
    onSecondary = Color.White,
    secondaryContainer = Ward,
    onSecondaryContainer = Ink,
    tertiary = NightRaised,
    background = Color.White,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Linen,
    onSurfaceVariant = Muted,
    outline = Control,
    outlineVariant = Edge,
    error = ColorError,
)

val HighContrastColorScheme = lightColorScheme(
    primary = ColorHighContrastPrimary,
    onPrimary = Color.White,
    primaryContainer = Mint,
    onPrimaryContainer = Color.Black,
    secondary = Color.Black,
    onSecondary = Color.White,
    secondaryContainer = Color.White,
    onSecondaryContainer = Color.Black,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = Color.White,
    onSurfaceVariant = Color.Black,
    outline = Color.Black,
    outlineVariant = Color.Black,
    error = ColorHighContrastEmergency,
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
            emergencyBar = ColorHighContrastEmergency,
            frame = Color.Black,
            field = Color.White,
            edge = Color.Black,
        )
    } else {
        ExtraThemeColors()
    }

    fun style(size: Int, line: Int, weight: FontWeight, tracking: Double = -0.01) = TextStyle(
        fontFamily = Chivo,
        fontSize = (size * textScaleFactor).sp,
        lineHeight = (line * textScaleFactor).sp,
        fontWeight = weight,
        letterSpacing = tracking.em,
        color = colorScheme.onSurface,
    )

    // Display and headline sizes are the website's black-weight capitals: tight tracking, short leading.
    // Every style is set here, all with letter spacing in em: Material's defaults use sp, and Compose cannot blend
    // the two (a text field's label animates from bodyLarge to bodySmall and crashes on a mix).
    val scaledTypography = Typography(
        displayLarge = style(56, 58, FontWeight.Black, -0.05),
        displayMedium = style(44, 46, FontWeight.Black, -0.05),
        displaySmall = style(36, 40, FontWeight.Black, -0.04),
        headlineLarge = style(30, 32, FontWeight.Black, -0.04),
        headlineMedium = style(24, 27, FontWeight.Black, -0.03),
        headlineSmall = style(22, 26, FontWeight.Black, -0.02),
        titleLarge = style(22, 28, FontWeight.Black, -0.02),
        titleMedium = style(18, 24, FontWeight.Bold),
        titleSmall = style(16, 22, FontWeight.Bold),
        bodyLarge = style(18, 26, FontWeight.Normal),
        bodyMedium = style(16, 23, FontWeight.Normal),
        bodySmall = style(14, 20, FontWeight.Normal),
        labelLarge = style(16, 20, FontWeight.Black, 0.0),
        labelMedium = style(14, 18, FontWeight.Bold, 0.02),
        labelSmall = style(12, 16, FontWeight.Bold, 0.02),
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
