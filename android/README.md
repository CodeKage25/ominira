# Òmìnira for Android

Bank by yourself. The Android app for Òmìnira, Zenith Bank Zecathon 6.0, Track 7 (accessible banking). One app for every customer: blind, low vision, deaf, older and motor-impaired people, and everyone else.

Jetpack Compose, one activity, edge to edge. Paper, Night and High contrast looks with every text pair at 7:1, Andika and Unbounded fonts (every Yorùbá, Hausa and Igbo mark, and ₦), 56dp targets (72dp in plain and switch mode), haptic and sound tokens from the design system.

## Run it

1. Open this `android/` folder in Android Studio (Koala or newer) and let Gradle sync. Gradle 8.9 comes with the wrapper and needs JDK 17 or 21 (it does not run on JDK 25).
2. Run the `app` configuration on a phone with Android 8.0 or later. A real phone, not the emulator: the Pulse PIN is felt as vibration, and the selfie and ID card use the real cameras. The APK carries phone processors only (arm64 and 32-bit ARM).
3. Each new customer chooses their own Pulse PIN while signing up. Until then, and after **Settings > Start again from welcome**, the bank inside the app uses **2 0 7 5**.
4. Hold up a sample NIN slip, never a real one: the card is read aloud.

From a terminal: `./gradlew assembleDebug testDebugUnitTest`. CI runs the same on every push (`.github/workflows/android.yml`) and keeps the debug APK as a build artifact.

By default the app runs fully offline on `FakeOminiraApi`, the bank inside the app. Nothing touches a real bank.

## Point it at the Gateway

The Gateway (`../gateway/`) holds the confirm gate, the assistant, YarnGPT speech and a mock Zenith core. Either flip **Settings > Use the live Gateway** on the phone, or set the default in `gradle.properties`:

```properties
ominira.useFakeApi=false
# Cloud Run, or http://<laptop IP>:8080/v1 on the same Wi-Fi (debug builds only allow plain http)
ominira.gatewayUrl=https://<your Cloud Run service>/v1
ominira.gatewayToken=demo-customer
ominira.cloudVoice=true
```

Put `ominira.gatewayUrl` in your own `~/.gradle/gradle.properties`, not the repo's file, so the live URL stays out of git. CI builds read it from the `OMINIRA_GATEWAY_URL` repository secret. With no URL set, the live Gateway switch does nothing and the app stays offline.

If the Gateway has a `MOCK_ADMIN_TOKEN`, put it in your own `~/.gradle/gradle.properties` as `ominira.mockAdminToken=...`, never in the repo's file. With it, **Settings > Start again from welcome** also puts the Gateway's mock bank back to the demo world (`POST /_mock/reset` with `X-Mock-Admin`). Without it, only the app starts again.

To approve as Funmi, open `<gateway>/approve?token=demo-approver` on a second phone or laptop.

### When the network is bad

- A call that moves no money (onboarding, sign in, balance, payees, the assistant) falls back to the bank inside the app only when the Gateway cannot be reached at all (no network, no host, a timeout, a broken TLS handshake). The rest of the session stays offline, and Settings says so. A reply from the Gateway, even a 401, 429 or 500, never takes the session offline (`bank/Reach.kt`).
- Money never runs on the bank inside the app while the Gateway is on. Drafting a transfer or a card block, setting the PIN, confirming, pausing and cancelling go to the Gateway or fail with "I cannot reach the bank. Nothing was sent." Offline, the assistant may still answer a question, but never hands back a draft.
- Money coming in is announced only from a real Gateway reply, in the customer's language, after the app stops speaking and the microphone closes.
- Each PIN entry is a new confirm attempt with its own Idempotency-Key. If the answer is lost, the app sends the same request once more with the same key (the Gateway replays the stored answer), then asks `GET /v1/drafts/{id}` what happened. If it still cannot tell, the customer hears "Do not send it again", never "nothing was sent".
- With cloud voice on, speech goes through the Gateway (YarnGPT, all five languages). A rising tone means speak now, a falling tone means got it. Any failure rests cloud speech for a minute and the phone's own voice and recogniser take over.

## The demo path

1. **Language first, out loud.** The app opens on five big bands, each labelled only in its own language: English, Pidgin, Yorùbá, Hausa, Igbo. It speaks one line per language, each in that language ("For English, say English, or tap English." ... "Maka Igbo, kwuo Igbo, ma ọ bụ pịa Igbo."), then listens. Say the language in any of the five (Turanci, Bekee and Gẹ̀ẹ́sì all mean English) or tap a band. Nothing heard: the lines play once more, then it waits for a tap. With TalkBack on it stays silent and the bands are the interface.
2. **Welcome, in that language.** "Would you like me to talk you through it? Say yes, or tap anywhere on the screen." The whole screen is one button. "Read and tap instead", near the top, opens the ways-in picker, where nothing is ticked, so a deaf customer never lands in voice mode.
3. **Sonar selfie.** "Say start, or tap anywhere." The front camera and ML Kit face detection, on the phone: a ping rises in pitch as the face centres, and one direction at a time is spoken once it has held for a moment ("Bring the phone a little closer", "Move the phone a little to the left", "Look straight at the phone", "It is too dark"). Directions never talk over each other, and are spoken only in voice mode or with TalkBack; otherwise the panel shows them. The phone buzzes when the face holds still, then four digits to say. Moves on by itself. No camera, no selfie: the app asks for it again rather than pretend. (The digits shown are sent as heard.)
4. **ID by ear.** The back camera and ML Kit text recognition, with the same guidance and a filling bar. It locks only once it has read the name (middle name too), the NIN and the date of birth, by where each line sits on the card, so the digital NIN slip, the printed slip's table and the ID card all read. Then they are read back. Yes goes on, "again" repeats, no retakes. Only the NIN's last four digits are kept, and the card's text is never stored or logged. An enrolment transaction slip, which carries no NIN, is named and turned away.
5. **BVN by consent.** "Zenith will confirm your BVN with NIBSS, using your NIN and this phone number. You do not need to say it. Say yes, or tap anywhere, to allow it." Then the account is open.
6. **Choose your Pulse PIN.** Pulse four digits, then the same four again. One digit four times, a run up or down, a repeated pair, and the birthday on the ID just read are refused on the phone, and the bank refuses the easy ones again. Sealed at once, then home.
7. **Pulse PIN.** Tap to start the count. One beat per digit, starting at a random digit; zero is a strong double buzz. Tap when the count reaches your digit, four times; the count rests after each digit. "How does it work?" explains it, "Practise first" runs a round where nothing is entered and zero also sounds. Three wrong tries locks it. In voice mode the screen stays dark; "Show count" draws it. Wired earphones move the count to quiet ticks in the ears. Speed: Settings, 1.8, 1.3 or 0.9 seconds a beat (switch and plain start at 1.8).
8. **Home.** Voice home (greets and listens), plain home (also the home for sign mode) or switch scanning, depending on the ways in. A phone that has signed up, or was handed to a demo person, opens next time on its Pulse PIN instead of the language picker; **Settings > Start again from welcome** forgets it.
   - "Send twenty thousand to Tolu", or "Send money to Tolu" then "Twenty thousand": readback, "Say yes", Pulse PIN, "Sent. Tolu has it."
   - Without voice: Send money, pick Tolu, Mama or Musa, type the amount, "Check it before sending".
   - With **Settings > Pretend I am on a phone call** on, send ₦150,000 to Musa: the scam pause ("say stop, or say carry on"). Carry on, pulse the PIN again (the gate wants a fresh PIN block), then the co-approver wait. Offline, Funmi approves by herself after about six seconds; on the Gateway, approve on `/approve`.
   - "Block my card": readback, "Say yes", Pulse PIN, card blocked, "Say home, or say branch".
   - Balance and what came in this week, each transaction read as one sentence; sign language call with running captions; Settings.
   - **Settings > Start again from welcome** gives a fresh demo bank, so rehearsals never run out of money.

Every screen follows the language picked first. Phones have no recogniser for Yorùbá, Hausa or Igbo, so without the Gateway a yes or no in those languages is a touch: tap anywhere for yes, touch and hold for no (at the scam pause the tap stops the payment). Names, amounts and digits are still heard. With the Gateway and cloud voice on, YarnGPT hears all five.

Before a live demo: turn media volume up, unpair Bluetooth speakers, and grant the microphone and the camera once during rehearsal so no permission box appears on stage. Yorùbá and Hausa play from bundled clips with no network. The Gateway's YarnGPT voice is limited to 120 calls a day: do not spend them rehearsing on the morning of the pitch.

What does not work yet, and the plan for each: [`docs/known-issues.md`](../docs/known-issues.md).

## The voice dock

Every screen after onboarding has the same dock at the bottom: one big **Speak** button (it says **Stop** while the app listens or talks) with the last thing heard and the last thing said as captions above it. Switch scanning offers Speak as a scan item instead. Pulse PIN screens have no dock. There is one microphone for all these screens (`voice/VoiceHost.kt`): a screen that is waiting for an answer (yes or no at a readback, stop or carry on at the scam pause, home or branch for a new card) hears first; anything else goes to the commands below, then to the assistant. With TalkBack on the app never listens by itself; pressing Speak announces "Listening" and opens the microphone once TalkBack is done.

Commands are answered on the phone, in any of the five languages, mixed (`core/Commands.kt`): go home, go back, my balance, send money, my card, settings, help (what can I say), say that again, where am I, and stop. A command is the whole request: "send money" opens Send and asks who, "send twenty thousand to Tolu" goes to the assistant.

## Whose phone is this (demo)

With the live Gateway on, **Settings > Whose phone is this** turns the phone into Ronke (`demo-customer`) or one of the Gateway's seeded people: Tolu and Mama (Yorùbá), Musa and Aisha (Hausa), Emeka and Chika (Igbo), all with the PIN 2 0 7 5. The app switches its Gateway token, speaks that person's language, greets them by name, and announces their money in that language. The choice is kept until **Start again from welcome**.

## What is placeholder

| Piece | Now | For real |
| --- | --- | --- |
| Bank | `FakeOminiraApi` offline, or `GatewayOminiraApi` over HTTPS with a demo bearer token | Device-bound passkey token |
| Pulse PIN on the wire | `PinBlock.seal`: "demo-" and 9 minus each digit (2 0 7 5 is "demo-7924") | ISO 9564 PIN block encrypted on the device with Zenith's PIN key |
| Language roll call | Bundled clips `assets/audio/lang_<code>.mp3`, made with YarnGPT | The same clips, checked by native speakers |
| Speech out | Bundled YarnGPT clips for every fixed line (Yorùbá, Hausa and Igbo), then Gateway `/v1/speech/say`, then the phone's voice | Same, with reviewed voices per language |
| Speech in | Gateway `/v1/speech/listen` (16 kHz WAV), else the phone's recogniser in the language when the phone has it (asked with `checkRecognitionSupport`), else en-NG for English names and numbers, with touch answers for yes and no | Same, with a real voice activity detector and talking over the app (see `docs/known-issues.md`) |
| Sonar selfie and ID | CameraX and ML Kit on the phone (face detection, text recognition), bundled so they work offline | The same guidance, with the images going to Zenith's liveness and document vendor |
| Liveness digits | Shown and sent back as if heard | Heard and checked; a typed or signed option for customers who do not speak |
| BVN | Consent, then the mock bank's NIBSS lookup always matches | NIBSS iGree lookup from the NIN and phone number |
| Sign call | Placeholder video and scripted captions | A video room with a Zenith sign agent |
| Assistant offline | Keyword rules in `FakeOminiraApi.turn` | Claude with read-only tools behind the Gateway |
| Co-approver offline | Approves by itself after 6 seconds | Funmi approves on her phone; 15 minutes to answer |
| Translations | Pidgin, Yorùbá, Hausa and Igbo lines are drafts (`core/Words.kt`, `core/OnboardingLines.kt`) | Checked by native speakers |

Bundled speech: `gateway/scripts/make_language_clips.py` makes the roll call's `lang_<code>.mp3`. `gateway/scripts/make_speech_clips.py` reads every fixed line from the app (through `ClipLinesTest`), keeps the ones the app speaks, and writes `assets/audio/<lang>/<key>.mp3` and an `index.json` that maps each line, and its English twin, to its clip. A changed line gets a new clip, an unchanged one is never made twice, and lines with a name or an amount fall back to the cloud voice or the phone's.

## How it is built

```
app/src/main/java/app/ominira/
  MainActivity.kt          edge to edge; hands TalkBack announcements to the session
  Session.kt               remembered choices, the bank client, voice, haptics
  core/                    pure Kotlin, unit tested on the JVM
    Model.kt               accounts, payees, drafts, readbacks, outcomes of the confirm gate
    Naira.kt               ₦ formatting and British English number words
    SpokenAmount.kt        amounts from speech and typing ("20k", "twenty thousand", "150,000")
    Answers.kt             yes, no, stop, carry on, start, again, language names, digits
    Commands.kt            voice commands answered on the phone, in all five languages
    People.kt              the Gateway's seeded demo people, for Whose phone is this
    PinBlock.kt            the placeholder PIN block
    ReadbackHash.kt        readback_hash exactly as the Gateway computes it (for the fake bank)
    Words.kt               every screen line in all five languages, side by side
    Lines.kt               app lines that change with language
    OnboardingLines.kt     the language roll call and onboarding copy in all five languages
    Framing.kt             camera directions from where the face or card sits, and when to say them
    IdCardText.kt          reads a NIN slip or ID card by where its lines sit; keeps the NIN's last four
    PinRules.kt            refuses easy PINs and the birthday on the card
    ClipKey.kt             names a bundled speech clip, the same way the clip script does
    Days.kt                "Today", "Yesterday", "Wed 14 Oct"
  bank/
    OminiraApi.kt          the customer's side of docs/api.md
    FakeOminiraApi.kt      the bank inside the app: same demo world, same gate order
    GatewayOminiraApi.kt   HTTPS client with the offline and lost-answer rules
    Reach.kt               which failures mean the Gateway cannot be reached
  voice/                   Speaker (bundled clips, cloud voice, phone voice), CloudVoice, Ears (listening),
                           Hearing (which languages the phone's recogniser takes), Prompt (ask then
                           listen, for onboarding), VoiceHost (the one microphone after onboarding),
                           WavRecorder, Tones, Clips
  device/                  Haptics tokens; Signals (call, TalkBack, wired earphones, reduced motion)
  ui/theme/                Palette (three looks), Type, Theme, Glyphs (the icon set)
  ui/kit/                  Page, buttons, choices, notices, fields, money views, the voice dock, touch answers
  ui/camera/               CameraFeed (CameraX and ML Kit), Aiming (pings and spoken directions)
  ui/pulse/                PulsePad and PulseStep
  ui/onboarding/           language, welcome, ways in, sonar, ID, BVN, choosing the PIN, unlock
  ui/home/                 voice, plain and switch homes
  ui/money/                send money, block card, balance
  ui/help/                 sign call, settings
app/src/test/java/         JUnit 4 tests for core/ and the fake bank's gate
```

## Accessibility notes

- Every screen works with TalkBack: headings, live regions for anything that changes, and readbacks, transactions and the bank card each read as one sentence.
- With TalkBack on, the app never opens the microphone by itself and every line it would speak goes to TalkBack instead, so the customer hears one voice. The Pulse PIN tap area then takes a double tap, like every TalkBack control.
- Voice mode with TalkBack off asks every question out loud and listens for the answer. Spoken answers are understood in English, Pidgin, Yorùbá, Hausa and Igbo (`core/Answers.kt`). A spoken PIN is caught and never kept.
- Switch scanning takes the on-screen button, a Bluetooth switch, or a keyboard's space or enter. Every row also opens directly, so Android Switch Access works.
- Camera guidance is spoken one direction at a time, never over itself, and always names the phone ("move the phone left"), mirrored for the front camera; a sighted or deaf customer reads the same direction on the panel.
- Where nothing can understand the customer's language, yes and no are a tap and a touch and hold, and the safe answer is always the tap.
- Motion stops when "Remove animations" is on.

## Credits

Andika (SIL) and Unbounded are under the SIL Open Font License; the licences are in `app/src/main/assets/licenses`. Cloud speech is YarnGPT, behind the Gateway; the phone never holds its key.
