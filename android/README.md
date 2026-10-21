# Òmìnira for Android

Bank by yourself. The Android app for Òmìnira, Zenith Bank Zecathon 6.0, Track 7 (accessible banking). One app for every customer: blind, low vision, deaf, older and motor-impaired people, and everyone else.

Jetpack Compose, one activity, edge to edge. Paper, Night and High contrast looks with every text pair at 7:1, Andika and Unbounded fonts (every Yorùbá, Hausa and Igbo mark, and ₦), 56dp targets (72dp in plain and switch mode), haptic and sound tokens from the design system.

## Run it

1. Open this `android/` folder in Android Studio (Koala or newer) and let Gradle sync. Gradle 8.9 comes with the wrapper.
2. Run the `app` configuration on a phone with Android 8.0 or later. A real phone is better than the emulator: the Pulse PIN is felt as vibration.
3. Demo PIN: **2 0 7 5**.

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

- A call that moves no money (onboarding, sign in, balance, payees, the assistant, making a draft) falls back to the bank inside the app if the Gateway cannot be reached. The rest of the session stays offline, and Settings says so.
- A draft the Gateway made always stays with the Gateway. Its confirm never falls back.
- Each PIN entry is a new confirm attempt with its own Idempotency-Key. If the answer is lost, the app sends the same request once more with the same key (the Gateway replays the stored answer), then asks `GET /v1/drafts/{id}` what happened. If it still cannot tell, the customer hears "Do not send it again", never "nothing was sent".
- With cloud voice on, speech goes through the Gateway (YarnGPT, all five languages). A rising tone means speak now, a falling tone means got it. Any failure rests cloud speech for a minute and the phone's own voice and recogniser take over.

## The demo path

1. **Language first, out loud.** The app opens on five big bands, each labelled only in its own language: English, Pidgin, Yorùbá, Hausa, Igbo. It speaks one line per language, each in that language ("For English, say English, or tap English." ... "Maka Igbo, kwuo Igbo, ma ọ bụ pịa Igbo."), then listens. Say the language in any of the five (Turanci, Bekee and Gẹ̀ẹ́sì all mean English) or tap a band. Nothing heard: the lines play once more, then it waits for a tap. With TalkBack on it stays silent and the bands are the interface.
2. **Welcome, in that language.** "Would you like me to talk you through it? Say yes, or tap anywhere on the screen." The whole screen is one button. "Read and tap instead", near the top, opens the ways-in picker, where nothing is ticked, so a deaf customer never lands in voice mode.
3. **Sonar selfie.** "Say start, or tap anywhere." A tone rises as the face centres, the phone buzzes when it locks, then four digits to say. Moves on by itself. (Camera simulated; the digits shown are sent as heard.)
4. **ID by ear.** Guided capture, then name, NIN ending and date of birth read back. Yes goes on, "again" repeats, no retakes. (Camera simulated.)
5. **BVN by voice.** Say the 11 digits. They are read back one at a time and used only after yes. Typing works too. Then the account is open.
6. **Pulse PIN.** Tap to start the count. One beat per digit, starting at a random digit; zero is a strong double buzz. Tap when the count reaches your digit, four times; the count rests after each digit. "How does it work?" explains it, "Practise first" runs a round where nothing is entered and zero also sounds. Three wrong tries locks it. In voice mode the screen stays dark; "Show count" draws it. Wired earphones move the count to quiet ticks in the ears. Speed: Settings, 1.8, 1.3 or 0.9 seconds a beat (switch and plain start at 1.8).
7. **Home.** Voice home (greets and listens), plain home (also the home for sign mode) or switch scanning, depending on the ways in.
   - "Send twenty thousand to Tolu", or "Send money to Tolu" then "Twenty thousand": readback, "Say yes", Pulse PIN, "Sent. Tolu has it."
   - Without voice: Send money, pick Tolu, Mama or Musa, type the amount, "Check it before sending".
   - With **Settings > Pretend I am on a phone call** on, send ₦150,000 to Musa: the scam pause ("say stop, or say carry on"). Carry on, pulse the PIN again (the gate wants a fresh PIN block), then the co-approver wait. Offline, Funmi approves by herself after about six seconds; on the Gateway, approve on `/approve`.
   - "Block my card": readback, "Say yes", Pulse PIN, card blocked, "Say home, or say branch".
   - Balance and what came in this week, each transaction read as one sentence; sign language call with running captions; Settings.
   - **Settings > Start again from welcome** gives a fresh demo bank, so rehearsals never run out of money.

Before a live demo: run it in English or Pidgin unless cloud voice is on, turn media volume up, unpair Bluetooth speakers, and grant the microphone once during rehearsal so no permission box appears on stage.

## What is placeholder

| Piece | Now | For real |
| --- | --- | --- |
| Bank | `FakeOminiraApi` offline, or `GatewayOminiraApi` over HTTPS with a demo bearer token | Device-bound passkey token |
| Pulse PIN on the wire | `PinBlock.seal`: "demo-" and 9 minus each digit (2 0 7 5 is "demo-7924") | ISO 9564 PIN block encrypted on the device with Zenith's PIN key |
| Language roll call | Bundled clips in `app/src/main/assets/audio/lang_<code>.mp3` if present, else the Gateway's voice, else the phone's voice for English and Pidgin only | Clips generated with YarnGPT by a Gateway script, checked by native speakers |
| Speech out | Gateway `/v1/speech/say` (YarnGPT), phone text-to-speech as fallback | Same, with reviewed voices per language |
| Speech in | Gateway `/v1/speech/listen` (16 kHz WAV), the phone's recogniser in the app, or the phone's listening box before the microphone is allowed | Same, with a real voice activity detector |
| Sonar selfie and ID | Simulated camera | CameraX with face and text detection, on Zenith's liveness vendor |
| Liveness digits | Shown and sent back as if heard | Heard and checked; a typed or signed option for customers who do not speak |
| Sign call | Placeholder video and scripted captions | A video room with a Zenith sign agent |
| Assistant offline | Keyword rules in `FakeOminiraApi.turn` | Claude with read-only tools behind the Gateway |
| Co-approver offline | Approves by itself after 6 seconds | Funmi approves on her phone; 15 minutes to answer |
| Translations | Pidgin, Yorùbá, Hausa and Igbo onboarding lines are drafts (`core/OnboardingLines.kt`) | Checked by native speakers |

Bundled language clips: drop `lang_en.mp3`, `lang_pcm.mp3`, `lang_yo.mp3`, `lang_ha.mp3` and `lang_ig.mp3` into `app/src/main/assets/audio/`. Each must say exactly the line in `LanguagePrompts`. Missing clips are skipped.

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
    PinBlock.kt            the placeholder PIN block
    ReadbackHash.kt        readback_hash exactly as the Gateway computes it (for the fake bank)
    Lines.kt               app lines that change with language
    OnboardingLines.kt     the language roll call and onboarding copy in all five languages
    Days.kt                "Today", "Yesterday", "Wed 14 Oct"
  bank/
    OminiraApi.kt          the customer's side of docs/api.md
    FakeOminiraApi.kt      the bank inside the app: same demo world, same gate order
    GatewayOminiraApi.kt   HTTPS client with the offline and lost-answer rules
  voice/                   Speaker, CloudVoice, Ears (listening), Prompt (ask then listen),
                           WavRecorder, Tones, Clips
  device/                  Haptics tokens; Signals (call, TalkBack, wired earphones, reduced motion)
  ui/theme/                Palette (three looks), Type, Theme, Glyphs (the icon set)
  ui/kit/                  Page, buttons, choices, notices, fields, money views, the voice orb
  ui/pulse/                PulsePad and PulseStep
  ui/onboarding/           language, welcome, ways in, sonar, ID, BVN, unlock
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
- Motion stops when "Remove animations" is on.

## Credits

Andika (SIL) and Unbounded are under the SIL Open Font License; the licences are in `app/src/main/assets/licenses`. Cloud speech is YarnGPT, behind the Gateway; the phone never holds its key.
