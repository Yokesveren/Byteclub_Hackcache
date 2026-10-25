package app.sevasetu.patient

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import app.sevasetu.patient.data.model.MyTurnDto
import app.sevasetu.patient.ui.components.EmptyState
import app.sevasetu.patient.ui.components.SevaScaffold
import app.sevasetu.patient.ui.chat.ChatScreen
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
import app.sevasetu.patient.ui.scanner.ScanMode
import app.sevasetu.patient.ui.scanner.StickerScannerScreen
import app.sevasetu.patient.ui.scanner.isSlip
import app.sevasetu.patient.ui.settings.SettingsScreen
import app.sevasetu.patient.ui.slip.IHaveMySlipScreen
import app.sevasetu.patient.ui.slip.OpdSlipScreen
import app.sevasetu.patient.ui.sms.SimulatedSmsScreen

object Routes {
    const val LANGUAGE = "language?again={again}"
    const val CONSENT = "consent"
    const val HOME = "home"
    const val REGISTER = "register"
    const val SLIP = "slip"
    const val MY_TURN = "myturn"
    const val CALLED = "called"
    const val HAVE_SLIP = "haveslip"
    const val NAVIGATE = "navigate"
    const val SCAN = "scan/{mode}"
    const val EMERGENCY = "emergency?auto={auto}"
    const val JOURNEY = "journey"
    const val SETTINGS = "settings"
    const val PRE_VISIT = "previsit"
    const val HELP = "help"
    const val SMS = "sms"
    const val CHAT = "chat"
    const val READINESS = "readiness"
    const val DEMO = "demo"
    const val STICKERS = "stickers"

    fun scan(mode: ScanMode) = "scan/${mode.name.lowercase()}"
    fun emergency(auto: Boolean = false) = "emergency?auto=$auto"
    fun language(again: Boolean) = "language?again=$again"
}

private const val SCANNED_SLIP = "scanned_slip"

@Composable
fun SevaNavGraph(
    navController: NavHostController,
    startDestination: String,
) {
    val app = SevaApplication.instance
    // Remembered, so the ticket survives recomposition between screens.
    var lastMyTurn by remember { mutableStateOf<MyTurnDto?>(null) }
    val activeVisit by app.visits.active.collectAsState()

    fun emergency() = navController.navigate(Routes.emergency()) { launchSingleTop = true }
    fun home() = navController.navigate(Routes.HOME) {
        popUpTo(Routes.HOME) { inclusive = true }
        launchSingleTop = true
    }
    fun goTo(waypointId: String) {
        app.nav.setDestination(waypointId)
        navController.navigate(Routes.NAVIGATE) { launchSingleTop = true }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(
            Routes.LANGUAGE,
            arguments = listOf(navArgument("again") { type = NavType.BoolType; defaultValue = false })
        ) { entry ->
            val again = entry.arguments?.getBoolean("again") == true
            LanguageScreen(
                onContinue = {
                    if (again) navController.popBackStack() else navController.navigate(Routes.CONSENT)
                },
                onEmergencyClick = ::emergency
            )
        }

        composable(Routes.CONSENT) {
            ConsentScreen(
                onContinue = {
                    navController.navigate(Routes.HOME) { popUpTo(0) { inclusive = true } }
                },
                onEmergencyClick = ::emergency,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToRegister = { navController.navigate(Routes.REGISTER) },
                onNavigateToHaveSlip = { navController.navigate(Routes.HAVE_SLIP) },
                onNavigateToMyTurn = { navController.navigate(Routes.MY_TURN) },
                onNavigateToSlip = { navController.navigate(Routes.SLIP) },
                onNavigateToFindPlace = {
                    if (app.nav.destination.value == null) app.nav.setDestination(activeVisit?.doctor?.roomWaypointId)
                    navController.navigate(Routes.NAVIGATE)
                },
                onNavigateToJourney = { navController.navigate(Routes.JOURNEY) },
                onNavigateToPreVisit = { navController.navigate(Routes.PRE_VISIT) },
                onNavigateToHelp = { navController.navigate(Routes.HELP) },
                onNavigateToChat = { navController.navigate(Routes.CHAT) },
                onNavigateToSms = { navController.navigate(Routes.SMS) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onEmergencyClick = ::emergency
            )
        }

        composable(Routes.REGISTER) {
            RegistrationScreen(
                onRegistrationComplete = { turn ->
                    lastMyTurn = turn
                    navController.navigate(Routes.SLIP) { popUpTo(Routes.HOME) }
                },
                onEmergencyTriggered = {
                    navController.navigate(Routes.emergency(auto = true)) { popUpTo(Routes.HOME) }
                },
                onEmergencyClick = ::emergency,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SLIP) {
            val turn = activeVisit ?: lastMyTurn
            if (turn == null) {
                SevaScaffold(title = "OPD slip", onBack = { navController.popBackStack() }, onEmergencyClick = ::emergency) {
                    EmptyState("No slip yet. Register for OPD first.")
                }
            } else {
                OpdSlipScreen(
                    myTurn = turn,
                    onNavigateToRoom = ::goTo,
                    onOpenMyTurn = { navController.navigate(Routes.MY_TURN) { popUpTo(Routes.HOME) } },
                    onBack = { navController.popBackStack() },
                    onEmergencyClick = ::emergency
                )
            }
        }

        composable(Routes.MY_TURN) {
            MyTurnScreen(
                visitId = activeVisit?.visitId,
                onNavigateToCalled = { turn ->
                    lastMyTurn = turn
                    navController.navigate(Routes.CALLED) { popUpTo(Routes.HOME) }
                },
                onNavigateToRoom = ::goTo,
                onShowSlip = { navController.navigate(Routes.SLIP) },
                onBack = { navController.popBackStack() },
                onEmergencyClick = ::emergency
            )
        }

        composable(Routes.CALLED) {
            (activeVisit ?: lastMyTurn)?.let { turn ->
                CalledScreen(
                    myTurn = turn,
                    onNavigateToRoom = ::goTo,
                    onHome = ::home,
                    onEmergencyClick = ::emergency
                )
            }
        }

        composable(Routes.HAVE_SLIP) { entry ->
            val scanned by entry.savedStateHandle.getStateFlow<String?>(SCANNED_SLIP, null).collectAsState()
            IHaveMySlipScreen(
                scanned = scanned,
                onScannedConsumed = { entry.savedStateHandle[SCANNED_SLIP] = null },
                onSlipLinked = { turn ->
                    lastMyTurn = turn
                    navController.navigate(Routes.MY_TURN) { popUpTo(Routes.HOME) }
                },
                onOpenScanner = { navController.navigate(Routes.scan(ScanMode.SLIP)) },
                onBack = { navController.popBackStack() },
                onEmergencyClick = ::emergency
            )
        }

        composable(Routes.NAVIGATE) {
            IndoorNavigationScreen(
                onOpenScanner = { navController.navigate(Routes.scan(ScanMode.STICKER)) },
                onBack = { navController.popBackStack() },
                onEmergencyClick = ::emergency
            )
        }

        composable(
            Routes.SCAN,
            arguments = listOf(navArgument("mode") { type = NavType.StringType; defaultValue = "sticker" })
        ) { entry ->
            val mode = when (entry.arguments?.getString("mode")) {
                "slip" -> ScanMode.SLIP
                "any" -> ScanMode.ANY
                else -> ScanMode.STICKER
            }
            StickerScannerScreen(
                mode = mode,
                onResult = { code ->
                    if (isSlip(code)) {
                        navController.previousBackStackEntry?.savedStateHandle?.set(SCANNED_SLIP, code)
                        navController.popBackStack()
                    } else {
                        app.nav.scan(code)
                        navController.popBackStack()
                    }
                },
                onBack = { navController.popBackStack() },
                onEmergencyClick = ::emergency
            )
        }

        composable(
            Routes.EMERGENCY,
            arguments = listOf(navArgument("auto") { type = NavType.BoolType; defaultValue = false })
        ) { entry ->
            EmergencyScreen(
                autoRaise = entry.arguments?.getBoolean("auto") == true,
                onBack = {
                    if (!navController.popBackStack()) home()
                }
            )
        }

        composable(Routes.JOURNEY) {
            HealthJourneyScreen(onBack = { navController.popBackStack() }, onEmergencyClick = ::emergency)
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onEmergencyClick = ::emergency,
                onOpenLanguage = { navController.navigate(Routes.language(again = true)) },
                onOpenReadiness = { navController.navigate(Routes.READINESS) },
                onOpenStickerSheet = { navController.navigate(Routes.STICKERS) },
                onOpenDemoPanel = { navController.navigate(Routes.DEMO) }
            )
        }

        composable(Routes.PRE_VISIT) {
            BeforeYouVisitScreen(onBack = { navController.popBackStack() }, onEmergencyClick = ::emergency)
        }

        composable(Routes.HELP) {
            HelpCardsScreen(onBack = { navController.popBackStack() }, onEmergencyClick = ::emergency)
        }

        composable(Routes.CHAT) {
            ChatScreen(
                onEmergency = { navController.navigate(Routes.emergency()) { launchSingleTop = true } },
                onGoToPlace = { tag ->
                    val hospital = activeVisit?.hospital?.id ?: app.prefs.current.hospitalId
                    app.nav.graph(hospital)?.nodes?.firstOrNull { it.sticker && tag in it.tags }?.let { goTo(it.id) }
                },
                onOpenScreen = { screen ->
                    when (screen) {
                        "register" -> navController.navigate(Routes.REGISTER)
                        "myturn" -> navController.navigate(Routes.MY_TURN)
                        "haveslip" -> navController.navigate(Routes.HAVE_SLIP)
                        "navigate" -> navController.navigate(Routes.NAVIGATE)
                        "journey" -> navController.navigate(Routes.JOURNEY)
                        "settings" -> navController.navigate(Routes.SETTINGS)
                        "sms" -> navController.navigate(Routes.SMS)
                    }
                },
                onOpenHelpCards = { navController.navigate(Routes.HELP) },
                onBack = { navController.popBackStack() },
                onEmergencyClick = ::emergency
            )
        }

        composable(Routes.SMS) {
            SimulatedSmsScreen(onBack = { navController.popBackStack() }, onEmergencyClick = ::emergency)
        }

        composable(Routes.READINESS) {
            OfflineReadinessScreen(
                onBack = { navController.popBackStack() },
                onEmergencyClick = ::emergency,
                onOpenDemoPanel = { navController.navigate(Routes.DEMO) }
            )
        }

        composable(Routes.DEMO) {
            DemoPanelScreen(
                onBack = { navController.popBackStack() },
                onEmergencyClick = ::emergency,
                onOpenStickerSheet = { navController.navigate(Routes.STICKERS) }
            )
        }

        composable(Routes.STICKERS) {
            StickerSheetScreen(onBack = { navController.popBackStack() }, onEmergencyClick = ::emergency)
        }
    }
}
