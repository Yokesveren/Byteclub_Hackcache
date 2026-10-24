package app.sevasetu.patient

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation.compose.rememberNavController
import app.sevasetu.patient.ui.i18n.I18n
import app.sevasetu.patient.ui.i18n.LocalLang
import app.sevasetu.patient.ui.theme.ColorEmergency
import app.sevasetu.patient.ui.theme.Frame
import app.sevasetu.patient.ui.theme.SevaTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Draw under the system bars (the screens pad themselves) with light icons on the navy frame and the red
        // emergency bar.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Frame.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(ColorEmergency.toArgb())
        )
        super.onCreate(savedInstanceState)

        val prefs = SevaApplication.instance.prefs
        // Language and consent are asked once; after that the app opens on Home.
        val start = if (prefs.current.onboarded) Routes.HOME else Routes.LANGUAGE

        setContent {
            val settings by prefs.settings.collectAsState()
            SevaTheme(textScaleFactor = settings.textScale, isHighContrast = settings.highContrast) {
                // The chosen language for every screen; Urdu and Kashmiri read right to left.
                CompositionLocalProvider(
                    LocalLang provides settings.language,
                    LocalLayoutDirection provides if (I18n.isRtl(settings.language)) LayoutDirection.Rtl else LayoutDirection.Ltr
                ) {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        val navController = rememberNavController()
                        val startDestination = remember { start }
                        SevaNavGraph(navController = navController, startDestination = startDestination)
                    }
                }
            }
        }
    }
}
