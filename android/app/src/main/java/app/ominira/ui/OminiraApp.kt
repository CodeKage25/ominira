package app.ominira.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.ominira.Session
import app.ominira.core.Command
import app.ominira.core.DraftKind
import app.ominira.core.Mode
import app.ominira.core.Place
import app.ominira.ui.help.SettingsScreen
import app.ominira.ui.help.SignCallScreen
import app.ominira.ui.home.HomeActions
import app.ominira.ui.home.PlainHomeScreen
import app.ominira.ui.home.SwitchHomeScreen
import app.ominira.ui.home.VoiceHomeScreen
import app.ominira.ui.money.BalanceScreen
import app.ominira.ui.money.BlockCardScreen
import app.ominira.ui.money.SendMoneyScreen
import app.ominira.ui.onboarding.BvnScreen
import app.ominira.ui.onboarding.IdByEarScreen
import app.ominira.ui.onboarding.LanguageScreen
import app.ominira.ui.onboarding.SetPinScreen
import app.ominira.ui.onboarding.SonarScreen
import app.ominira.ui.onboarding.UnlockScreen
import app.ominira.ui.onboarding.WaysInScreen
import app.ominira.ui.onboarding.WelcomeScreen
import app.ominira.ui.theme.OminiraTheme
import app.ominira.voice.LocalVoice
import app.ominira.voice.rememberVoiceHost

object Route {
    const val LANGUAGE = "language"
    const val WELCOME = "welcome"
    const val WAYS_IN = "ways-in"
    const val SONAR = "sonar"
    const val ID = "id"
    const val BVN = "bvn"
    const val UNLOCK = "unlock"
    const val SET_PIN = "set-pin"
    const val VOICE_HOME = "home/voice"
    const val PLAIN_HOME = "home/plain"
    const val SWITCH_HOME = "home/switch"
    const val SEND = "send"
    const val BALANCE = "balance"
    const val CARD = "card"
    const val SIGN = "sign"
    const val SETTINGS = "settings"
    const val SETTINGS_LANGUAGE = "settings/language"
}

fun homeFor(modes: Set<Mode>): String = when {
    Mode.Switch in modes -> Route.SWITCH_HOME
    Mode.Plain in modes -> Route.PLAIN_HOME
    Mode.Sign in modes && Mode.Voice !in modes -> Route.PLAIN_HOME
    else -> Route.VOICE_HOME
}

private fun placeOf(route: String?): Place? = when (route) {
    Route.VOICE_HOME, Route.PLAIN_HOME, Route.SWITCH_HOME -> Place.Home
    Route.SEND -> Place.Send
    Route.BALANCE -> Place.Balance
    Route.CARD -> Place.Card
    Route.SIGN -> Place.Sign
    Route.SETTINGS, Route.SETTINGS_LANGUAGE -> Place.Settings
    else -> null
}

@Composable
fun OminiraApp(session: Session) {
    OminiraTheme(look = session.look, roomy = session.roomy, lang = session.lang) {
        val nav = rememberNavController()
        fun open(route: String) {
            session.speaker.stop()
            nav.navigate(route) { launchSingleTop = true }
        }
        val actions = HomeActions(
            send = { open(Route.SEND) },
            balance = { open(Route.BALANCE) },
            card = { open(Route.CARD) },
            sign = { open(Route.SIGN) },
            settings = { open(Route.SETTINGS) },
        )
        fun back() {
            session.speaker.stop()
            nav.popBackStack()
        }

        // One microphone for every screen after onboarding.
        val voice = rememberVoiceHost(session)
        DisposableEffect(voice) {
            session.volumeMic = { voice.listenNow() }
            onDispose { session.volumeMic = null }
        }
        voice.place = { placeOf(nav.currentBackStackEntry?.destination?.route) }
        voice.go = { command ->
            when (command) {
                Command.Home -> nav.goHome(session)
                Command.Back -> if (nav.previousBackStackEntry != null) back() else nav.goHome(session)
                Command.Balance -> actions.balance()
                Command.Send -> actions.send()
                Command.Card -> actions.card()
                Command.Settings -> actions.settings()
                Command.Help, Command.Again, Command.WhereAmI, Command.Stop -> Unit
            }
        }
        // A new screen each time, even over itself, so it takes the draft.
        voice.routeDraft = { turn ->
            session.pendingTurn = turn
            session.speaker.stop()
            nav.navigate(if (turn.draft?.kind == DraftKind.CardBlock) Route.CARD else Route.SEND)
        }

        val start = remember { if (session.onboarded) Route.UNLOCK else Route.LANGUAGE }
        CompositionLocalProvider(LocalVoice provides voice) {
            NavHost(navController = nav, startDestination = start) {
                composable(Route.LANGUAGE) {
                    LanguageScreen(session, fromSettings = false, onBack = null, onChosen = { nav.navigate(Route.WELCOME) })
                }
                composable(Route.WELCOME) {
                    WelcomeScreen(session, onTalk = { nav.navigate(Route.SONAR) }, onRead = { nav.navigate(Route.WAYS_IN) })
                }
                composable(Route.WAYS_IN) { WaysInScreen(session, onNext = { nav.navigate(Route.SONAR) }) }
                composable(Route.SONAR) { SonarScreen(session, onBack = { back() }, onNext = { nav.navigate(Route.ID) }) }
                composable(Route.ID) { IdByEarScreen(session, onBack = { back() }, onNext = { nav.navigate(Route.BVN) }) }
                composable(Route.BVN) { BvnScreen(session, onBack = { back() }, onNext = { nav.navigate(Route.SET_PIN) }) }
                // A new customer chooses their own PIN, so they go home from here, not through sign in.
                composable(Route.SET_PIN) {
                    SetPinScreen(session, onDone = {
                        session.markOnboarded()
                        nav.goHome(session)
                    })
                }
                composable(Route.UNLOCK) { UnlockScreen(session, onUnlocked = { nav.goHome(session) }) }
                composable(Route.VOICE_HOME) { VoiceHomeScreen(session, actions) }
                composable(Route.PLAIN_HOME) { PlainHomeScreen(session, actions) }
                composable(Route.SWITCH_HOME) { SwitchHomeScreen(session, actions) }
                composable(Route.SEND) {
                    SendMoneyScreen(
                        session,
                        onHome = { nav.goHome(session) },
                        onBalance = { nav.navigate(Route.BALANCE) },
                        onSign = { nav.navigate(Route.SIGN) },
                    )
                }
                composable(Route.BALANCE) { BalanceScreen(session, onHome = { nav.goHome(session) }) }
                composable(Route.CARD) { BlockCardScreen(session, onHome = { nav.goHome(session) }) }
                composable(Route.SIGN) { SignCallScreen(session, onHome = { nav.goHome(session) }) }
                composable(Route.SETTINGS) {
                    SettingsScreen(
                        session,
                        onBack = { nav.goHome(session) },
                        onLanguage = { nav.navigate(Route.SETTINGS_LANGUAGE) },
                        onRestart = {
                            session.restart()
                            nav.navigate(Route.LANGUAGE) { popUpTo(nav.graph.id) { inclusive = true } }
                        },
                        onWhose = { person ->
                            session.chooseCustomer(person)
                            nav.goHome(session)
                        },
                    )
                }
                composable(Route.SETTINGS_LANGUAGE) {
                    LanguageScreen(session, fromSettings = true, onBack = { back() }, onChosen = { back() })
                }
            }
        }
    }
}

private fun NavHostController.goHome(session: Session) {
    session.speaker.stop()
    navigate(homeFor(session.modes)) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}
