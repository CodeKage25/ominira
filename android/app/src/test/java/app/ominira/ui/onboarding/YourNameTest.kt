package app.ominira.ui.onboarding

import org.junit.Assert.assertEquals
import org.junit.Test

class YourNameTest {

    @Test
    fun aSpokenNameIsTidied() {
        assertEquals("Adebayo Tolulope", cleanName("my name is adebayo tolulope."))
        assertEquals("Kemi Bello", cleanName("Kemi  Bello"))
        assertEquals("Chinedu Okafor", cleanName("I'm chinedu okafor"))
    }
}
