package app.ominira.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommandsTest {

    private fun each(command: Command, vararg said: String) {
        for (s in said) assertEquals(s, command, Commands.parse(s))
    }

    @Test
    fun whereAmIInEveryLanguage() = each(Command.WhereAmI, "Where am I?", "Where I dey?", "Níbo ni mo wà?", "Ina nake?", "Ebee ka m nọ?")

    @Test
    fun helpInEveryLanguage() = each(Command.Help, "What can I say?", "Help", "Wetin I fit talk?", "Ìrànlọ́wọ́", "Taimako", "Enyemaka")

    @Test
    fun sayThatAgainInEveryLanguage() =
        each(Command.Again, "Say that again", "Can you say that again please", "Talk am again", "Ẹ tún sọ", "Sake faɗa", "Kwuo ya ọzọ")

    @Test
    fun goHomeInEveryLanguage() = each(Command.Home, "Go home", "Go back home", "Padà sí ilé", "Koma gida", "Laghachi n’ụlọ")

    @Test
    fun goBackInEveryLanguage() = each(Command.Back, "Go back", "Comot", "Padà sẹ́yìn", "Koma baya", "Laghachi azụ")

    @Test
    fun balanceInEveryLanguage() = each(
        Command.Balance,
        "How much do I have?", "What is my balance", "How much I get?", "Èló ni mo ní?", "Kuɗina", "Ego ole ka m nwere?",
    )

    @Test
    fun sendMoneyInEveryLanguage() = each(Command.Send, "Send money", "Abeg I wan send money", "Fi owó ránṣẹ́", "Aika kuɗi", "Ziga ego")

    @Test
    fun myCardInEveryLanguage() = each(Command.Card, "My card", "My ATM card", "Káàdì mi", "Katina", "Kaadị m")

    @Test
    fun settingsInEveryLanguage() = each(Command.Settings, "Settings", "Open settings", "Ètò", "Saituna", "Ntọala")

    @Test
    fun stopInEveryLanguage() = each(Command.Stop, "Stop", "Abeg stop", "Dúró", "Tsaya", "Kwụsị")

    @Test
    fun languagesMix() {
        assertEquals(Command.Balance, Commands.parse("Abeg, owó mi"))
        assertEquals(Command.Send, Commands.parse("Please ziga ego"))
    }

    @Test
    fun aRequestWithDetailsGoesToTheAssistant() {
        for (s in listOf(
            "Send twenty thousand to Tolu", "Send money to Tolu", "Send ₦20,000 to Mama", "Help me send money to Tolu",
            "Block my card", "What came in this week?", "What is my account number",
        )) {
            assertNull(s, Commands.parse(s))
        }
    }

    @Test
    fun answersToAQuestionAreNotCommands() {
        for (s in listOf("Yes", "No", "Carry on", "home", "branch", "")) assertNull(s, Commands.parse(s))
    }
}
