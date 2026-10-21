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
import app.ominira.core.Lang
import app.ominira.core.Lines
import app.ominira.core.Mode
import app.ominira.core.PreferencePatch
import app.ominira.core.Turn
import app.ominira.device.Haptics
import app.ominira.device.Signals
import app.ominira.ui.theme.Look
import app.ominira.voice.CloudVoice
import app.ominira.voice.Speaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Everything that outlives a screen: the customer's choices (kept on the phone), the bank client,
 * the voice and the haptics. Screens get it as a parameter.
 */
class Session(app: Application) : AndroidViewModel(app) {

    private val store = app.getSharedPreferences("ominira", Context.MODE_PRIVATE)
    private val background = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val context: Context get() = getApplication<Application>()

    // ---- Which bank ------------------------------------------------------------------------

    /** Live Gateway or the bank inside the app. gradle.properties sets the default; Settings flips it. */
    var useGateway by mutableStateOf(store.getBoolean(K_GATEWAY, !BuildConfig.USE_FAKE_API))
        private set

    var api: OminiraApi = newApi()
        private set

    /** The live client lost the Gateway and this session now runs on the bank inside the app. */
    val gatewayLost: Boolean get() = (api as? GatewayOminiraApi)?.offline == true

    private val cloudVoice: CloudVoice? =
        if (BuildConfig.CLOUD_VOICE) CloudVoice(BuildConfig.GATEWAY_URL, BuildConfig.GATEWAY_TOKEN) else null

    /** Cloud speech only with the live Gateway, and not once the session has gone offline. */
    fun activeCloudVoice(): CloudVoice? = if (api is GatewayOminiraApi && !gatewayLost) cloudVoice else null

    private fun newApi(): OminiraApi =
        if (useGateway && BuildConfig.GATEWAY_URL.isNotBlank()) GatewayOminiraApi(BuildConfig.GATEWAY_URL, BuildConfig.GATEWAY_TOKEN, BuildConfig.MOCK_ADMIN_TOKEN)
        else FakeOminiraApi()

    // ---- Voice and touch ---------------------------------------------------------------------

    val speaker = Speaker(app) { activeCloudVoice() }
    val haptics = Haptics(app)

    /** Posts a line to TalkBack. Set by MainActivity while it is alive. */
    var announcer: ((String) -> Unit)? = null

    // ---- Remembered choices ------------------------------------------------------------------

    var look by mutableStateOf(enumOr(store.getString(K_LOOK, null), Look.Paper))
        private set
    var lang by mutableStateOf(Lang.fromCode(store.getString(K_LANG, null)) ?: Lang.En)
        private set
    var modes by mutableStateOf(readModes())
        private set
    var speechRate by mutableStateOf(store.getFloat(K_RATE, 0.95f))
        private set

    /** Pulse PIN beat gap: 1.8 s slower, 1.3 s normal, 0.9 s faster. Switch and plain start slower. */
    var pulseGapMs by mutableStateOf(store.getLong(K_PULSE, 1_300L))
        private set

    /** Zero also sounds on the loudspeaker during a real PIN. Off: a listener could count from it. */
    var zeroSound by mutableStateOf(store.getBoolean(K_ZERO, false))
        private set
    var captions by mutableStateOf(store.getBoolean(K_CAPTIONS, true))
        private set

    /** How long the scanning ring rests on each choice. */
    var scanMs by mutableStateOf(store.getLong(K_SCAN, 2_500L))
        private set

    // ---- This run only -----------------------------------------------------------------------

    /** Settings > "Pretend I am on a phone call", so the scam pause can be shown. */
    var demoCallActive by mutableStateOf(false)

    /** A draft the assistant made on a home screen, handed to the send or card screen. */
    var pendingTurn by mutableStateOf<Turn?>(null)

    /** The onboarding session this phone started. */
    var onboardingId: String? = null

    /** The microphone permission was asked for once already in this run. */
    var micAsked = false

    val customerName = "Ronke"

    /** Plain and switch mode get bigger type and 72dp targets. */
    val roomy: Boolean get() = Mode.Plain in modes || Mode.Switch in modes

    init {
        speaker.rate = speechRate
    }

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

    /** Sends every remembered choice to the Gateway, once onboarding has given this phone a token. */
    fun syncAll() = sync(PreferencePatch(lang = lang, modes = modes, theme = look.wire, speechRate = speechRate, pulseGapMs = pulseGapMs))

    private fun sync(patch: PreferencePatch) {
        val client = api
        background.launch { runCatching { client.savePreferences(patch) } }
    }

    /**
     * "Start again from welcome": a fresh bank, no pretend call, nothing half done. With the live
     * Gateway and an admin token, the Gateway's mock bank is put back to the demo world too.
     */
    fun restart() {
        speaker.stop()
        val fresh = newApi()
        if (fresh is GatewayOminiraApi) background.launch { fresh.resetMock() }
        api = fresh
        demoCallActive = false
        pendingTurn = null
        onboardingId = null
    }

    // ---- Talking -----------------------------------------------------------------------------

    fun screenReaderOn(): Boolean = Signals.screenReaderOn(context)

    fun callActive(): Boolean = demoCallActive || Signals.inCall(context)

    /**
     * True when the app should ask each question out loud and then listen: voice mode is on and
     * TalkBack is off. With TalkBack on, TalkBack reads the screen and the app never opens the
     * microphone by itself.
     */
    val talksFirst: Boolean get() = Mode.Voice in modes && !screenReaderOn()

    /**
     * Says [lines] in the customer's language, with readback pauses between them. With TalkBack
     * on, the words go to TalkBack instead, so the customer only ever hears one voice.
     */
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

    /** Spoken on arrival only in voice mode with TalkBack off; TalkBack already reads the screen. */
    fun sayOnArrival(vararg lines: String) {
        if (talksFirst) speaker.speak(lang, lines.toList())
    }

    /** What a Pulse PIN screen says as it opens: the full explanation three times, then a reminder. */
    fun pulseIntro(): String {
        val seen = store.getInt(K_PULSE_INTROS, 0)
        store.edit().putInt(K_PULSE_INTROS, seen + 1).apply()
        return if (seen < 3) Lines.pulseExplain(lang) else Lines.pulseShort(lang)
    }

    fun greeting(): String = Lines.greeting(lang, customerName, Calendar.getInstance().get(Calendar.HOUR_OF_DAY))

    /** The customer's current account, or null if the bank could not say. */
    suspend fun mainAccount(): Account? = runCatching { api.accounts().firstOrNull() }.getOrNull()

    /** This phone's onboarding session, started on first use. */
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
