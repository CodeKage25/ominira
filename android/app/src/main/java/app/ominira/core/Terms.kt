package app.ominira.core

// "PIN" in each language's own words (unreviewed drafts): a secret number. English and Pidgin keep PIN.
// Longest forms first, so "Pulse PIN ɗinku" becomes one phrase, not "Pulse lambar sirri ɗinku".
object PinTerm {
    private val rules: Map<Lang, List<Pair<String, String>>> = mapOf(
        Lang.Yo to listOf(
            "Pulse PIN" to "nọ́ńbà àṣírí Pulse",
            "PIN" to "nọ́ńbà àṣírí",
        ),
        Lang.Ha to listOf(
            "Pulse PIN ɗinku" to "lambar sirrinku ta Pulse",
            "Pulse PIN ɗina" to "lambar sirrina ta Pulse",
            "Pulse PIN" to "lambar sirri ta Pulse",
            "PIN ɗinku" to "lambar sirrinku",
            "PIN ɗina" to "lambar sirrina",
            "PIN" to "lambar sirri",
        ),
        Lang.Ig to listOf(
            "Pulse PIN" to "nọmba nzuzo Pulse",
            "PIN" to "nọmba nzuzo",
        ),
    )

    fun local(lang: Lang, text: String): String {
        val forLang = rules[lang] ?: return text
        var out = text
        for ((from, to) in forLang) out = out.replace(Regex("\\b" + Regex.escape(from) + "\\b"), to)
        return out
    }
}

// What a voice is given instead of what the screen shows: letters that must be spelled, spelled.
// gateway/scripts/make_speech_clips.py does the same before recording a clip.
object VoiceText {
    private val spell = listOf("NIN" to "N.I.N.", "BVN" to "B.V.N.")

    fun of(text: String): String {
        var out = text
        for ((from, to) in spell) out = out.replace(Regex("\\b$from\\b"), to)
        return out
    }
}
