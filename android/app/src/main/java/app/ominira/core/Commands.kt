package app.ominira.core

enum class Command { WhereAmI, Help, Again, Home, Back, Balance, Send, Card, Settings, Stop }

// The inner screens the voice commands know by name.
enum class Place { Home, Send, Balance, Card, Sign, Settings }

// Answered on the phone before the assistant. A command is the whole request: "send money" opens
// Send, "send twenty thousand to Tolu" goes to the assistant. Words from any of the five languages, mixed.
// Hausa hooked letters (ɗ, ƙ) normalise to a space, so phrases keep their real spelling beside the plain one.
object Commands {

    private val phrases: List<Pair<Command, List<String>>> = listOf(
        Command.WhereAmI to listOf(
            "where am i", "what screen is this", "which screen is this", "what page is this", "which page",
            "where i dey", "nibo ni mo wa", "ibo ni mo wa", "ina nake", "ebee ka m no", "ebe ka m no",
        ),
        Command.Help to listOf(
            "help", "help me", "what can i say", "what can i do", "what do i say", "commands",
            "wetin i fit talk", "wetin i fit do", "wetin i fit say",
            "iranlowo", "ki ni mo le so", "kini mo le so", "taimako", "me zan iya cewa", "me zan ce",
            "enyemaka", "gini ka m nwere ike ikwu", "gini ka m ga ekwu",
        ),
        Command.Again to listOf(
            "again", "say that again", "say it again", "repeat", "repeat that", "come again", "pardon",
            "what did you say", "talk am again", "talk again", "wetin you talk", "wetin you say",
            "tun so", "tun un so", "e tun so", "so lekan si", "sake fada", "sake faɗa", "maimaita", "kwuo ya ozo", "kwughachi",
        ),
        Command.Home to listOf(
            "go home", "back home", "back to home", "take me home", "home screen", "home page", "main menu",
            "go back home", "lo si ile", "pada si ile", "koma gida", "je gida", "laghachi n'ulo", "laghachi ulo", "gaa n'ulo",
        ),
        Command.Back to listOf("back", "go back", "previous", "previous page", "comot", "pada", "pada seyin", "koma baya", "laghachi azu"),
        Command.Balance to listOf(
            "balance", "my balance", "account balance", "check balance", "how much do i have", "how much money do i have",
            "how much have i got", "how much is in my account", "how much i get", "how much money i get",
            "owo mi", "elo ni mo ni", "elo lo wa", "kuɗina", "kudina", "nawa nake da shi", "nawa ne kuɗina",
            "ego m", "ego ole ka m nwere", "ego ole m nwere",
        ),
        Command.Send to listOf(
            "send", "send money", "transfer", "transfer money", "make a transfer", "pay someone", "send moni",
            "fi owo ranse", "ranse owo", "aika kuɗi", "aika kudi", "tura kuɗi", "ziga ego",
        ),
        Command.Card to listOf("card", "my card", "atm card", "kaadi mi", "kaadi", "katina", "katin", "kaadi m"),
        Command.Settings to listOf("settings", "setting", "options", "preferences", "eto", "saituna", "ntoala"),
        Command.Stop to listOf(
            "stop", "stop talking", "be quiet", "quiet", "shut up", "cancel", "hold on", "wait",
            "duro", "dake", "e dake", "tsaya", "yi shiru", "kwusi", "mechie onu",
        ),
    )

    // Words around a command that change nothing: "please", "I want to", "abeg", "my".
    private val filler = setOf(
        "please", "abeg", "biko", "jowo", "ejo", "don", "allah", "oya", "ok", "okay", "o", "now", "just",
        "i", "want", "wan", "to", "can", "you", "could", "would", "will", "take", "me", "tell", "show", "open", "check",
        "what", "whats", "what's", "is", "be", "wetin", "make", "let", "lets", "let's", "us", "go", "the", "a", "my",
        "mi", "m", "na", "ni", "si", "and", "for", "of", "this", "that",
    )

    private val compiled: List<Pair<Command, List<List<String>>>> =
        phrases.map { (command, list) -> command to list.map { Answers.normalise(it).split(' ') } }

    fun parse(text: String): Command? {
        val said = Answers.normalise(text).split(' ').filter { it.isNotEmpty() }
        if (said.isEmpty() || said.size > 9) return null
        for ((command, list) in compiled) {
            if (list.any { covers(said, it) }) return command
        }
        return null
    }

    // The phrase is said, and every other word is filler.
    private fun covers(said: List<String>, phrase: List<String>): Boolean {
        val left = said.toMutableList()
        var found = false
        var i = 0
        while (i + phrase.size <= left.size) {
            if (left.subList(i, i + phrase.size) == phrase) {
                repeat(phrase.size) { left.removeAt(i) }
                found = true
            } else {
                i += 1
            }
        }
        return found && left.all { it in filler }
    }
}
