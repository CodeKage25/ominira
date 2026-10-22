package app.ominira

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import app.ominira.bank.FakeOminiraApi
import app.ominira.bank.GatewayOminiraApi
import app.ominira.bank.OminiraApi
import app.ominira.core.Account
import app.ominira.core.DemoPeople
import app.ominira.core.DemoPerson
import app.ominira.core.IdDetails
import app.ominira.core.Lang
import app.ominira.core.Lines
import app.ominira.core.Mode
import app.ominira.core.PreferencePatch
import app.ominira.core.Turn
import app.ominira.core.Words
import app.ominira.device.Haptics
import app.ominira.device.Signals
import app.ominira.ui.theme.Look
import app.ominira.voice.CloudVoice
import app.ominira.voice.Hearing
import app.ominira.voice.Speaker
import java.util.Calendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class Session(app: Application) : AndroidViewModel(app) {

    private val store = app.getSharedPreferences("ominira", Context.MODE_PRIVATE)
    private val background = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val context: Context get() = getApplication<Application>()

    var useGateway by mutableStateOf(store.getBoolean(K_GATEWAY, !BuildConfig.USE_FAKE_API))
        private set

    var api: OminiraApi = newApi()
        private set

    val gatewayLost: Boolean get() = (api as? GatewayOminiraApi)?.offline == true

    private val cloudVoice: CloudVoice? =
        if (BuildConfig.CLOUD_VOICE) CloudVoice(BuildConfig.GATEWAY_URL, BuildConfig.GATEWAY_TOKEN) else null

    fun activeCloudVoice(): CloudVoice? = if (api is GatewayOminiraApi && !gatewayLost) cloudVoice else null

    private fun newApi(): OminiraApi =
        if (useGateway && BuildConfig.GATEWAY_URL.isNotBlank()) {
            // A demo person chosen in Settings, else the phone's own customer once it has signed up, else the demo customer.
            GatewayOminiraApi(
                BuildConfig.GATEWAY_URL,
                chosenToken() ?: store.getString(K_TOKEN, null) ?: BuildConfig.GATEWAY_TOKEN,
                BuildConfig.MOCK_ADMIN_TOKEN,
                onToken = {
                    store.edit().putString(K_TOKEN, it).remove(K_PERSON).apply()
                    personKey = null
                },
                customerLang = { lang },
            )
        }
        else FakeOminiraApi(lang = Lang.fromCode(store.getString(K_LANG, null)) ?: Lang.En)

    private fun chosenToken(): String? =
        DemoPeople.byKey(store.getString(K_PERSON, null))?.let { it.token ?: BuildConfig.GATEWAY_TOKEN }

    val liveGateway: Boolean get() = api is GatewayOminiraApi

    val speaker = Speaker(app) { activeCloudVoice() }
    val haptics = Haptics(app)

    var announcer: ((String) -> Unit)? = null

    var look by mutableStateOf(enumOr(store.getString(K_LOOK, null), Look.Paper))
        private set
    var lang by mutableStateOf(Lang.fromCode(store.getString(K_LANG, null)) ?: Lang.En)
        private set
    // Screen copy, in the customer's language.
    val words: Words get() = Words.of(lang)

    // No voice for the language: speak English, not yo/ha/ig in an English voice.
    val voiceLang: Lang get() = if (speaker.hasVoiceFor(lang)) lang else Lang.En

    // Copy to speak: pair each words.x on screen with spoken.x in say().
    val spoken: Words get() = Words.of(voiceLang)

    var modes by mutableStateOf(readModes())
        private set
    var speechRate by mutableStateOf(store.getFloat(K_RATE, 0.95f))
        private set

    var pulseGapMs by mutableStateOf(store.getLong(K_PULSE, 1_300L))
        private set

    // Off by default: a listener could count the PIN from the zero sound.
    var zeroSound by mutableStateOf(store.getBoolean(K_ZERO, false))
        private set
    // Hold to count, let go at the digit; off is tap to start, tap at the digit.
    var pulseHold by mutableStateOf(store.getBoolean(K_HOLD, true))
        private set
    // TalkBack's double tap is a tap: holding is only offered without it.
    val holdToPulse: Boolean get() = pulseHold && !screenReaderOn()
    var captions by mutableStateOf(store.getBoolean(K_CAPTIONS, true))
        private set

    var scanMs by mutableStateOf(store.getLong(K_SCAN, 2_500L))
        private set

    var demoCallActive by mutableStateOf(false)

    var pendingTurn by mutableStateOf<Turn?>(null)

    var onboardingId: String? = null

    // Signed in this session: onboarding finished, or the Pulse PIN opened the app.
    var signedIn = false

    // This phone signed up before: it opens on the Pulse PIN, not on onboarding.
    val hasOwnAccount: Boolean get() = store.contains(K_TOKEN)

    val onboarded: Boolean get() = hasOwnAccount || store.getBoolean(K_ONBOARDED, false)

    fun markOnboarded() {
        store.edit().putBoolean(K_ONBOARDED, true).apply()
    }

    // The demo person this phone speaks for, chosen in Settings; null for its own customer.
    var personKey by mutableStateOf(store.getString(K_PERSON, null))
        private set

    // Open while the shared voice host listens; money in waits for it to close.
    val micOpen = MutableStateFlow(false)

    // Volume down opens the microphone on any screen after onboarding: a button found without looking.
    var volumeMic: (() -> Unit)? = null

    private var watcher: Job? = null

    // While the app is open, money that lands is announced in the customer's language. Gateway only:
    // the bank inside the app has no one else to send money.
    fun watchIncoming() {
        val client = api as? GatewayOminiraApi ?: return
        if (!signedIn || watcher?.isActive == true) return
        watcher = background.launch {
            var seen: Set<String>? = null
            while (true) {
                // Only a real Gateway reply is announced, never the bank inside the app.
                val came = client.incomingNow(10)
                if (came != null) {
                    val before = seen
                    // The first look only learns what is already there.
                    if (before != null) {
                        for (t in came.filter { it.id !in before }) {
                            awaitTurnToSpeak()
                            haptics.done()
                            say(spoken.moneyIn(spoken.money(t.amountKobo), t.who))
                        }
                    }
                    seen = (before ?: emptySet()) + came.map { it.id }
                }
                delay(10_000)
            }
        }
    }

    // Never over a line being said, never into an open microphone.
    private suspend fun awaitTurnToSpeak() {
        repeat(20) {
            speaker.awaitQuiet()
            withTimeoutOrNull(60_000) { micOpen.first { !it } }
            delay(400)
            if (!speaker.talking.value && !micOpen.value) return
        }
    }

    fun stopWatching() {
        watcher?.cancel()
        watcher = null
    }

    // The ID the customer confirmed, for the PIN's birthday check. Memory only, never stored.
    var idOnFile: IdDetails? = null

    var micAsked = false

    // The customer's first name, from the bank; kept so the PIN screen can greet them before it answers.
    var customerName by mutableStateOf(store.getString(K_NAME, null).orEmpty())
        private set

    fun refreshName() {
        val client = api
        background.launch {
            val name = client.firstName() ?: return@launch
            customerName = name
            store.edit().putString(K_NAME, name).apply()
        }
    }

    val roomy: Boolean get() = Mode.Plain in modes || Mode.Switch in modes

    // The tag the phone's recogniser takes for each language it knows; filled in by Hearing.
    var phoneHears by mutableStateOf(mapOf(Lang.En to "en-NG", Lang.Pcm to "en-NG"))
        private set

    init {
        speaker.rate = speechRate
        checkHearing(lang)
        if (onboarded) refreshName()
    }

    private fun checkHearing(value: Lang) {
        if (value in phoneHears) return
        Hearing.check(context, value) { tag -> if (tag != null) phoneHears = phoneHears + (value to tag) }
    }

    // Something can understand the customer's language: YarnGPT through the Gateway, or the phone.
    val understands: Boolean get() = lang in phoneHears || activeCloudVoice() != null

    // Where nothing can, yes, no and other one-word answers are touches; names and numbers still listen.
    val answersByTouch: Boolean get() = talksFirst && !understands

    // en-NG still hears English names and numbers said by a customer of any language.
    fun phoneTag(): String = phoneHears[lang] ?: "en-NG"

    private fun readModes(): Set<Mode> =
        store.getStringSet(K_MODES, null)?.mapNotNull { name -> Mode.entries.firstOrNull { it.name == name } }?.toSet()
            ?.takeIf { it.isNotEmpty() } ?: setOf(Mode.Voice)

    fun chooseLook(value: Look) {
        look = value
        store.edit().putString(K_LOOK, value.name).apply()
        sync(PreferencePatch(theme = value.wire))
    }

    fun chooseLang(value: Lang) {
        lang = value
        store.edit().putString(K_LANG, value.code).apply()
        sync(PreferencePatch(lang = value))
        checkHearing(value)
    }

    fun chooseModes(value: Set<Mode>) {
        if (value.isEmpty()) return
        modes = value
        store.edit().putStringSet(K_MODES, value.map { it.name }.toSet()).apply()
        if ((Mode.Switch in value || Mode.Plain in value) && !store.contains(K_PULSE)) choosePulseGap(1_800L)
        sync(PreferencePatch(modes = value))
    }

    fun chooseSpeechRate(value: Float) {
        speechRate = value
        speaker.rate = value
        store.edit().putFloat(K_RATE, value).apply()
        sync(PreferencePatch(speechRate = value))
    }

    fun choosePulseGap(value: Long) {
        pulseGapMs = value
        store.edit().putLong(K_PULSE, value).apply()
        sync(PreferencePatch(pulseGapMs = value))
    }

    fun chooseZeroSound(value: Boolean) {
        zeroSound = value
        store.edit().putBoolean(K_ZERO, value).apply()
    }

    fun choosePulseHold(value: Boolean) {
        pulseHold = value
        store.edit().putBoolean(K_HOLD, value).apply()
    }

    fun chooseCaptions(value: Boolean) {
        captions = value
        store.edit().putBoolean(K_CAPTIONS, value).apply()
    }

    fun chooseScanMs(value: Long) {
        scanMs = value
        store.edit().putLong(K_SCAN, value).apply()
    }

    fun chooseGateway(value: Boolean) {
        speaker.stop()
        useGateway = value
        store.edit().putBoolean(K_GATEWAY, value).apply()
        stopWatching()
        api = newApi()
        pendingTurn = null
        onboardingId = null
        watchIncoming()
    }

    // Demo only, live Gateway: the phone becomes one of the Gateway's seeded customers.
    fun chooseCustomer(person: DemoPerson) {
        val client = api as? GatewayOminiraApi ?: return
        speaker.stop()
        stopWatching()
        store.edit().putString(K_PERSON, person.key).putBoolean(K_ONBOARDED, true).putString(K_NAME, person.firstName).apply()
        personKey = person.key
        client.switchCustomer(person.token ?: BuildConfig.GATEWAY_TOKEN)
        customerName = person.firstName
        person.lang?.let { chooseLang(it) }
        pendingTurn = null
        onboardingId = null
        idOnFile = null
        signedIn = true
        watchIncoming()
        // The Gateway's own name and language for them win.
        background.launch {
            val (name, spokenLang) = client.profile() ?: return@launch
            if (api !== client || personKey != person.key) return@launch
            if (name != null) {
                customerName = name
                store.edit().putString(K_NAME, name).apply()
            }
            if (spokenLang != null && spokenLang != lang) chooseLang(spokenLang)
        }
    }

    fun syncAll() = sync(PreferencePatch(lang = lang, modes = modes, theme = look.wire, speechRate = speechRate, pulseGapMs = pulseGapMs))

    private fun sync(patch: PreferencePatch) {
        val client = api
        background.launch { runCatching { client.savePreferences(patch) } }
    }

    fun restart() {
        speaker.stop()
        // With its own customer, the phone just signs up again: resetting the shared mock bank would wipe
        // the other phone's customer in a two-phone demo. Only the demo customer resets it.
        val ownCustomer = store.contains(K_TOKEN)
        store.edit().remove(K_TOKEN).remove(K_NAME).remove(K_PERSON).remove(K_ONBOARDED).apply()
        customerName = ""
        personKey = null
        stopWatching()
        signedIn = false
        val fresh = newApi()
        if (fresh is GatewayOminiraApi && !ownCustomer) background.launch { fresh.resetMock() }
        api = fresh
        demoCallActive = false
        pendingTurn = null
        onboardingId = null
        idOnFile = null
    }

    fun screenReaderOn(): Boolean = Signals.screenReaderOn(context)

    fun callActive(): Boolean = demoCallActive || Signals.inCall(context)

    // TalkBack on: the app never opens the microphone by itself.
    val talksFirst: Boolean get() = Mode.Voice in modes && !screenReaderOn()

    // TalkBack on: words go to TalkBack, so the customer hears one voice.
    fun say(vararg lines: String) = sayIn(lang, *lines)

    // The last line said, for "say that again" and the dock's caption.
    var lastLine by mutableStateOf<String?>(null)
        private set
    private var lastSaid: Pair<Lang, List<String>>? = null

    private fun keepLast(language: Lang, lines: List<String>) {
        val clean = lines.filter { it.isNotBlank() }
        if (clean.isEmpty()) return
        lastSaid = language to clean
        lastLine = clean.joinToString(" ")
    }

    fun sayAgain() {
        val last = lastSaid
        if (last == null) say(spoken.nothingToRepeat) else sayIn(last.first, *last.second.toTypedArray())
    }

    fun sayIn(language: Lang, vararg lines: String) {
        if (lines.isEmpty()) return
        keepLast(language, lines.toList())
        val announce = announcer
        if (screenReaderOn() && announce != null) {
            speaker.stop()
            announce(lines.joinToString(" "))
        } else {
            speaker.speak(language, lines.toList())
        }
    }

    fun sayOnArrival(vararg lines: String) {
        if (!talksFirst) return
        keepLast(lang, lines.toList())
        speaker.speak(lang, lines.toList())
    }

    // Holding is explained in full at least once, even to someone who learned tapping first.
    fun pulseIntro(): String {
        val hold = holdToPulse
        val seen = store.getInt(K_PULSE_INTROS, 0)
        val heldBefore = store.getInt(K_HOLD_INTROS, 0)
        val edit = store.edit().putInt(K_PULSE_INTROS, seen + 1)
        if (hold) edit.putInt(K_HOLD_INTROS, heldBefore + 1)
        edit.apply()
        val full = seen < 3 || (hold && heldBefore == 0)
        return if (full) Lines.pulseExplain(voiceLang, hold) else Lines.pulseShort(voiceLang, hold)
    }

    fun greeting(language: Lang = lang): String = Lines.greeting(language, customerName, Calendar.getInstance().get(Calendar.HOUR_OF_DAY))

    suspend fun mainAccount(): Account? = runCatching { api.accounts().firstOrNull() }.getOrNull()

    suspend fun onboardingSession(): String {
        onboardingId?.let { return it }
        val started = api.startOnboarding(lang)
        onboardingId = started.id
        return started.id
    }

    override fun onCleared() {
        announcer = null
        background.cancel()
        speaker.shutdown()
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
        name?.let { n -> enumValues<T>().firstOrNull { it.name == n } } ?: fallback

    private companion object {
        const val K_GATEWAY = "gateway"
        const val K_TOKEN = "customerToken"
        const val K_NAME = "customerName"
        const val K_PERSON = "demoPerson"
        const val K_ONBOARDED = "onboarded"
        const val K_LOOK = "look"
        const val K_LANG = "lang"
        const val K_MODES = "modes"
        const val K_RATE = "rate"
        const val K_PULSE = "pulseGap"
        const val K_ZERO = "zeroSound"
        const val K_HOLD = "pulseHold"
        const val K_CAPTIONS = "captions"
        const val K_SCAN = "scan"
        const val K_PULSE_INTROS = "pulseIntros"
        const val K_HOLD_INTROS = "holdIntros"
    }
}
