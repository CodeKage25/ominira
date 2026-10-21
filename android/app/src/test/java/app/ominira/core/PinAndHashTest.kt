package app.ominira.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PinAndHashTest {

    @Test
    fun sealsThePlaceholderBlockAsTheContractSays() {
        assertEquals("demo-7924", PinBlock.seal(listOf(2, 0, 7, 5)))
        assertEquals("demo-9999", PinBlock.seal(listOf(0, 0, 0, 0)))
        assertEquals("demo-0000", PinBlock.seal(listOf(9, 9, 9, 9)))
    }

    @Test
    fun opensOnlyWellFormedBlocks() {
        assertEquals(listOf(2, 0, 7, 5), PinBlock.open("demo-7924"))
        assertNull(PinBlock.open("7924"))
        assertNull(PinBlock.open("demo-79"))
        assertNull(PinBlock.open("demo-79a4"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesAnythingButFourDigits() {
        PinBlock.seal(listOf(1, 2, 3))
    }

    @Test
    fun readbackHashMatchesTheGateway() {
        // Expected values computed with Python's json.dumps(sort_keys=True, separators=(",", ":")).
        assertEquals(
            "{\"account_last4\":\"4471\",\"amount_kobo\":2000000,\"bank\":\"Zenith Bank\",\"kind\":\"transfer\",\"name\":\"Adebayo Tolulope\"}",
            ReadbackHash.canonical(DraftKind.Transfer, "Adebayo Tolulope", "Zenith Bank", "4471", 2_000_000),
        )
        assertEquals(
            "sha256:e15360d1707c35ca3b32ed4f36edd993fccb733198f020ca23aa612babc574dd",
            ReadbackHash.of(DraftKind.Transfer, "Adebayo Tolulope", "Zenith Bank", "4471", 2_000_000),
        )
        assertEquals(
            "sha256:7ab5cdeb2a813926f727908d54db783bbd6ccea541b6b81f3f09de56a3f949c1",
            ReadbackHash.of(DraftKind.CardBlock, "Card ending 8830", "Zenith Bank", "8830", null),
        )
        assertEquals(
            "{\"account_last4\":\"1\",\"amount_kobo\":1,\"bank\":\"B\",\"kind\":\"transfer\",\"name\":\"\\u00d2m\\u00ecnira \\u1ecc\"}",
            ReadbackHash.canonical(DraftKind.Transfer, "Òmìnira Ọ", "B", "1", 1),
        )
    }
}
