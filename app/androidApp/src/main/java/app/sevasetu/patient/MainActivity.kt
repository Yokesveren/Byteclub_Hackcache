package app.sevasetu.patient

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import app.sevasetu.patient.ui.theme.SevaTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var textScaleFactor by remember { mutableFloatStateOf(1.0f) }
            var isHighContrast by remember { mutableStateOf(false) }

            SevaTheme(
                textScaleFactor = textScaleFactor,
                isHighContrast = isHighContrast
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    SevaNavGraph(
                        navController = navController,
                        onTextScaleChanged = { scale -> textScaleFactor = scale },
                        onHighContrastChanged = { highContrast -> isHighContrast = highContrast }
                    )
                }
            }
        }
    }
}
