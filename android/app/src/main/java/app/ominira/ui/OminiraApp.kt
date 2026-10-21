package app.ominira.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.ominira.Session
import app.ominira.core.Mode
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
import app.ominira.ui.onboarding.SonarScreen
import app.ominira.ui.onboarding.UnlockScreen
import app.ominira.ui.onboarding.WaysInScreen
import app.ominira.ui.onboarding.WelcomeScreen
import app.ominira.ui.theme.OminiraTheme

object Route {
    const val LANGUAGE = "language"
    const val WELCOME = "welcome"
    const val WAYS_IN = "ways-in"
    const val SONAR = "sonar"
    const val ID = "id"
    const val BVN = "bvn"
    const val UNLOCK = "unlock"
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

@Composable
fun OminiraApp(session: Session) {
    OminiraTheme(look = session.look, roomy = session.roomy) {
        val nav = rememberNavController()
        val actions = HomeActions(
            send = { nav.navigate(Route.SEND) },
            balance = { nav.navigate(Route.BALANCE) },
            card = { nav.navigate(Route.CARD) },
            sign = { nav.navigate(Route.SIGN) },
            settings = { nav.navigate(Route.SETTINGS) },
        )
        fun back() {
            session.speaker.stop()
            nav.popBackStack()
        }

        NavHost(navController = nav, startDestination = Route.LANGUAGE) {
            composable(Route.LANGUAGE) {
                LanguageScreen(session, fromSettings = false, onBack = null, onChosen = { nav.navigate(Route.WELCOME) })
            }
            composable(Route.WELCOME) {
                WelcomeScreen(session, onTalk = { nav.navigate(Route.SONAR) }, onRead = { nav.navigate(Route.WAYS_IN) })
            }
            composable(Route.WAYS_IN) { WaysInScreen(session, onNext = { nav.navigate(Route.SONAR) }) }
            composable(Route.SONAR) { SonarScreen(session, onBack = { back() }, onNext = { nav.navigate(Route.ID) }) }
            composable(Route.ID) { IdByEarScreen(session, onBack = { back() }, onNext = { nav.navigate(Route.BVN) }) }
            composable(Route.BVN) { BvnScreen(session, onBack = { back() }, onNext = { nav.navigate(Route.UNLOCK) }) }
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
                )
            }
            composable(Route.SETTINGS_LANGUAGE) {
                LanguageScreen(session, fromSettings = true, onBack = { back() }, onChosen = { back() })
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
