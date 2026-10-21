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
import kotlinx.coroutines.launch

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
            // The phone's own customer once it has signed up; the demo customer before that.
            GatewayOminiraApi(
                BuildConfig.GATEWAY_URL,
                store.getString(K_TOKEN, null) ?: BuildConfig.GATEWAY_TOKEN,
                BuildConfig.MOCK_ADMIN_TOKEN,
                onToken = { store.edit().putString(K_TOKEN, it).apply() },
            )
        }
        else FakeOminiraApi(lang = Lang.fromCode(store.getString(K_LANG, null)) ?: Lang.En)

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
    var captions by mutableStateOf(store.getBoolean(K_CAPTIONS, true))
        private set

    var scanMs by mutableStateOf(store.getLong(K_SCAN, 2_500L))
        private set

    var demoCallActive by mutableStateOf(false)

    var pendingTurn by mutableStateOf<Turn?>(null)

    var onboardingId: String? = null

    // Signed in this session: onboarding finished, or the Pulse PIN opened the app.
    var signedIn = false

    private var watcher: Job? = null

    // While the app is open, money that lands is announced in the customer's language. Gateway only:
    // the bank inside the app has no one else to send money.
    fun watchIncoming() {
        if (!signedIn || api !is GatewayOminiraApi || watcher?.isActive == true) return
        watcher = background.launch {
            var seen: Set<String>? = null
            while (true) {
                val came = mainAccount()?.let { a -> runCatching { api.transactions(a.id, 10, "in") }.getOrNull() }
                if (came != null) {
                    val before = seen
                    // The first look only learns what is already there.
                    if (before != null) {
                        for (t in came.filter { it.id !in before }) {
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

    fun stopWatching() {
        watcher?.cancel()
        watcher = null
    }

    // The ID the customer confirmed, for the PIN's birthday check. Memory only, never stored.
    var idOnFile: IdDetails? = null

    var micAsked = false

    val customerName = "Ronke"

    val roomy: Boolean get() = Mode.Plain in modes || Mode.Switch in modes

    // The tag the phone's recogniser takes for each language it knows; filled in by Hearing.
    var phoneHears by mutableStateOf(mapOf(Lang.En to "en-NG", Lang.Pcm to "en-NG"))
        private set

    init {
        speaker.rate = speechRate
        checkHearing(lang)
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
        api = newApi()
        pendingTurn = null
        onboardingId = null
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
        store.edit().remove(K_TOKEN).apply()
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

    fun sayIn(language: Lang, vararg lines: String) {
        if (lines.isEmpty()) return
        val announce = announcer
        if (screenReaderOn() && announce != null) {
            speaker.stop()
            announce(lines.joinToString(" "))
        } else {
            speaker.speak(language, lines.toList())
        }
    }

    fun sayOnArrival(vararg lines: String) {
        if (talksFirst) speaker.speak(lang, lines.toList())
    }

    fun pulseIntro(): String {
        val seen = store.getInt(K_PULSE_INTROS, 0)
        store.edit().putInt(K_PULSE_INTROS, seen + 1).apply()
        return if (seen < 3) Lines.pulseExplain(voiceLang) else Lines.pulseShort(voiceLang)
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
        const val K_LOOK = "look"
        const val K_LANG = "lang"
        const val K_MODES = "modes"
        const val K_RATE = "rate"
        const val K_PULSE = "pulseGap"
        const val K_ZERO = "zeroSound"
        const val K_CAPTIONS = "captions"
        const val K_SCAN = "scan"
        const val K_PULSE_INTROS = "pulseIntros"
    }
}
