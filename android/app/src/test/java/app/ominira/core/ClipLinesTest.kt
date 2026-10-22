package app.ominira.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// Every fixed line the app can say, for gateway/scripts/make_speech_clips.py.
// With OMINIRA_CLIP_LINES set to a path, the test also writes them there as JSON.
class ClipLinesTest {

    private class Row(val name: String, val alias: Boolean, val text: Map<Lang, String>)

    private fun getters(type: Class<*>, of: (Lang) -> Any, prefix: String): List<Row> =
        type.methods
            .filter { it.name.startsWith("get") && it.parameterCount == 0 && it.declaringClass == type }
            .flatMap { m ->
                val name = prefix + m.name.removePrefix("get").replaceFirstChar { it.lowercaseChar() }
                val values = Lang.entries.associateWith { m.invoke(of(it)) }
                when (values.getValue(Lang.En)) {
                    is String -> listOf(Row(name, true, values.mapValues { it.value as String }))
                    is List<*> -> (values.getValue(Lang.En) as List<*>).indices.map { i ->
                        Row("$name.$i", true, values.mapValues { (it.value as List<*>)[i] as String })
                    }
                    else -> emptyList()
                }
            }

    private fun rows(): List<Row> =
        getters(Words::class.java, { Words.of(it) }, "") +
            getters(OnboardingCopy::class.java, { OnboardingCopy.of(it) }, "Onboarding.") +
            listOf(
                Row("Lines.pulseExplain", true, Lang.entries.associateWith { Lines.pulseExplain(it) }),
                Row("Lines.pulseShort", true, Lang.entries.associateWith { Lines.pulseShort(it) }),
                Row("Lines.practiceStart", true, Lang.entries.associateWith { Lines.practiceStart(it) }),
                Row("Lines.practiceDone", true, Lang.entries.associateWith { Lines.practiceResult(it, 3) }),
            ) +
            // Camera directions take arguments, so they are listed one by one: they must play at once, offline.
            Aim.entries.flatMap { aim ->
                listOf(false, true).map { card ->
                    Row("aim.${aim.name}.${if (card) "card" else "face"}", true, Lang.entries.associateWith { Words.of(it).aim(aim, card) })
                }
            } +
            listOf(
                // Said in the language just picked, never as an English twin.
                Row("LanguagePrompts.chosen", false, Lang.entries.associateWith { LanguagePrompts.chosen(it) }),
            )

    private fun json(s: String): String = buildString {
        append('"')
        for (c in s) {
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                else -> append(c)
            }
        }
        append('"')
    }

    @Test
    fun linesAreNamedOnceAndFilledIn() {
        val all = rows()
        assertEquals(all.size, all.map { it.name }.toSet().size)
        assertTrue(all.all { row -> row.text.values.all { it.isNotBlank() } })
    }

    @Test
    fun clipKeysMatchTheScript() {
        // hashlib.sha256("Your account is open.".encode()).hexdigest()[:16]
        assertEquals("9f297397f12fc0d1", ClipKey.of("Your account is open."))
        assertEquals(ClipKey.of("Your account is open."), ClipKey.of("  Your account is open. "))
    }

    @Test
    fun exportForTheClipScript() {
        val path = System.getenv("OMINIRA_CLIP_LINES") ?: return
        val body = rows().joinToString(",\n") { row ->
            val text = row.text.entries.joinToString(", ") { (lang, t) -> json(lang.code) + ": " + json(t) }
            "{\"name\": ${json(row.name)}, \"alias\": ${row.alias}, \"text\": {$text}}"
        }
        File(path).writeText("[\n$body\n]\n", Charsets.UTF_8)
    }
}
