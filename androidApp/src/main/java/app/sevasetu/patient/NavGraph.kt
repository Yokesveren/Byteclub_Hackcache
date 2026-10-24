package app.sevasetu.patient

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import app.sevasetu.patient.data.model.MyTurnDto
import app.sevasetu.patient.ui.consent.ConsentScreen
import app.sevasetu.patient.ui.demo.DemoPanelScreen
import app.sevasetu.patient.ui.demo.StickerSheetScreen
import app.sevasetu.patient.ui.emergency.EmergencyScreen
import app.sevasetu.patient.ui.home.HomeScreen
import app.sevasetu.patient.ui.info.BeforeYouVisitScreen
import app.sevasetu.patient.ui.info.HelpCardsScreen
import app.sevasetu.patient.ui.info.OfflineReadinessScreen
import app.sevasetu.patient.ui.journey.HealthJourneyScreen
import app.sevasetu.patient.ui.language.LanguageScreen
import app.sevasetu.patient.ui.myturn.CalledScreen
import app.sevasetu.patient.ui.myturn.MyTurnScreen
import app.sevasetu.patient.ui.navigation.IndoorNavigationScreen
import app.sevasetu.patient.ui.register.RegistrationScreen
import app.sevasetu.patient.ui.scanner.StickerScannerScreen
import app.sevasetu.patient.ui.settings.SettingsScreen
import app.sevasetu.patient.ui.slip.IHaveMySlipScreen
import app.sevasetu.patient.ui.slip.OpdSlipScreen
import app.sevasetu.patient.ui.sms.SimulatedSmsScreen

object Routes {
    const val LANGUAGE = "language"
    const val CONSENT = "consent"
    const val HOME = "home"
    const val REGISTER = "register"
    const val SLIP = "slip"
    const val MY_TURN = "myturn"
    const val CALLED = "called"
    const val HAVE_SLIP = "haveslip"
    const val NAVIGATE = "navigate"
    const val SCAN = "scan"
    const val EMERGENCY = "emergency"
    const val JOURNEY = "journey"
    const val SETTINGS = "settings"
    const val PRE_VISIT = "previsit"
    const val HELP = "help"
    const val SMS = "sms"
    const val READINESS = "readiness"
    const val DEMO = "demo"
    const val STICKERS = "stickers"
}

@Composable
fun SevaNavGraph(
    navController: NavHostController,
    startDestination: String = Routes.LANGUAGE,
    onTextScaleChanged: (Float) -> Unit,
    onHighContrastChanged: (Boolean) -> Unit
) {
    var lastMyTurn: MyTurnDto? = null
    var targetWaypointId: String = "SS:H01:F1:W103"

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Routes.LANGUAGE) {
            LanguageScreen(
                onContinue = { navController.navigate(Routes.CONSENT) },
                onEmergencyClick = { navController.navigate(Routes.EMERGENCY) }
            )
        }

        composable(Routes.CONSENT) {
            ConsentScreen(
                onContinue = { navController.navigate(Routes.HOME) },
                onEmergencyClick = { navController.navigate(Routes.EMERGENCY) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToRegister = { navController.navigate(Routes.REGISTER) },
                onNavigateToHaveSlip = { navController.navigate(Routes.HAVE_SLIP) },
                onNavigateToMyTurn = { navController.navigate(Routes.MY_TURN) },
                onNavigateToFindPlace = { navController.navigate(Routes.NAVIGATE) },
                onNavigateToJourney = { navController.navigate(Routes.JOURNEY) },
                onNavigateToPreVisit = { navController.navigate(Routes.PRE_VISIT) },
                onNavigateToHelp = { navController.navigate(Routes.HELP) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToDemo = { navController.navigate(Routes.DEMO) },
                onEmergencyClick = { navController.navigate(Routes.EMERGENCY) }
            )
        }

        composable(Routes.REGISTER) {
            RegistrationScreen(
                onRegistrationComplete = { turn ->
                    lastMyTurn = turn
                    navController.navigate(Routes.SLIP)
                },
                onEmergencyTriggered = { navController.navigate(Routes.EMERGENCY) },
                onEmergencyClick = { navController.navigate(Routes.EMERGENCY) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SLIP) {
            lastMyTurn?.let { turn ->
                OpdSlipScreen(
                    myTurn = turn,
                    onNavigateToRoom = { waypointId ->
                        targetWaypointId = waypointId
                        navController.navigate(Routes.NAVIGATE)
                    },
                    onBack = { navController.popBackStack() },
                    onEmergencyClick = { navController.navigate(Routes.EMERGENCY) }
                )
            }
        }

        composable(Routes.MY_TURN) {
            MyTurnScreen(
                visitId = "kamla-demo-visit",
                onNavigateToCalled = { turn ->
                    lastMyTurn = turn
                    navController.navigate(Routes.CALLED)
                },
                onNavigateToRoom = { waypointId ->
                    targetWaypointId = waypointId
                    navController.navigate(Routes.NAVIGATE)
                },
                onBack = { navController.popBackStack() },
                onEmergencyClick = { navController.navigate(Routes.EMERGENCY) }
            )
        }

        composable(Routes.CALLED) {
            lastMyTurn?.let { turn ->
                CalledScreen(
                    myTurn = turn,
                    onNavigateToRoom = { waypointId ->
                        targetWaypointId = waypointId
                        navController.navigate(Routes.NAVIGATE)
                    }
                )
            }
        }

        composable(Routes.HAVE_SLIP) {
            IHaveMySlipScreen(
                onSlipLinked = { turn ->
                    lastMyTurn = turn
                    navController.navigate(Routes.MY_TURN)
                },
                onOpenScanner = { navController.navigate(Routes.SCAN) },
                onBack = { navController.popBackStack() },
                onEmergencyClick = { navController.navigate(Routes.EMERGENCY) }
            )
        }

        composable(Routes.NAVIGATE) {
            IndoorNavigationScreen(
                destinationWaypointId = targetWaypointId,
                onOpenScanner = { navController.navigate(Routes.SCAN) },
                onBack = { navController.popBackStack() },
                onEmergencyClick = { navController.navigate(Routes.EMERGENCY) }
            )
        }

        composable(Routes.SCAN) {
            StickerScannerScreen(
                onStickerScanned = { _ ->
                    navController.navigate(Routes.NAVIGATE)
                },
                onBack = { navController.popBackStack() },
                onEmergencyClick = { navController.navigate(Routes.EMERGENCY) }
            )
        }

        composable(Routes.EMERGENCY) {
            EmergencyScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.JOURNEY) {
            HealthJourneyScreen(
                onBack = { navController.popBackStack() },
                onEmergencyClick = { navController.navigate(Routes.EMERGENCY) }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onEmergencyClick = { navController.navigate(Routes.EMERGENCY) },
                onOpenReadiness = { navController.navigate(Routes.READINESS) },
                onOpenDemoPanel = { navController.navigate(Routes.DEMO) },
                onTextScaleChanged = onTextScaleChanged,
                onHighContrastChanged = onHighContrastChanged
            )
        }

        composable(Routes.PRE_VISIT) {
            BeforeYouVisitScreen(
                onBack = { navController.popBackStack() },
                onEmergencyClick = { navController.navigate(Routes.EMERGENCY) }
            )
        }

        composable(Routes.HELP) {
            HelpCardsScreen(
                onBack = { navController.popBackStack() },
                onEmergencyClick = { navController.navigate(Routes.EMERGENCY) }
            )
        }

        composable(Routes.SMS) {
            SimulatedSmsScreen(
                onBack = { navController.popBackStack() },
                onEmergencyClick = { navController.navigate(Routes.EMERGENCY) }
            )
        }

        composable(Routes.READINESS) {
            OfflineReadinessScreen(
                onBack = { navController.popBackStack() },
                onEmergencyClick = { navController.navigate(Routes.EMERGENCY) },
                onOpenDemoPanel = { navController.navigate(Routes.DEMO) }
            )
        }

        composable(Routes.DEMO) {
            DemoPanelScreen(
                onBack = { navController.popBackStack() },
                onEmergencyClick = { navController.navigate(Routes.EMERGENCY) },
                onOpenStickerSheet = { navController.navigate(Routes.STICKERS) }
            )
        }

        composable(Routes.STICKERS) {
            StickerSheetScreen(
                onBack = { navController.popBackStack() },
                onEmergencyClick = { navController.navigate(Routes.EMERGENCY) }
            )
        }
    }
}
