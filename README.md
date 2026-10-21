# Òmìnira

**Bank by yourself.** Voice-first, accessible banking for every Zenith Bank customer, built for Zecathon 6.0, Track 7.

Òmìnira means self-sufficiency. It is for anyone a normal banking app leaves out: blind and low-vision customers, deaf customers, older people, people with motor impairments, and people who don't bank in English. You talk to it in English, Pidgin, Yorùbá, Hausa or Igbo, and it talks back in the same language.

## What it does

- **Asks your language first.** The app speaks as soon as it opens, one line per language in that language's own voice. Answer by voice or by tapping your language.
- **Lets you ask in plain words.** "Send twenty thousand to Tolu." "How much do I have?" "Block my card."
- **Reads back before money moves.** It says who, which bank, the last four digits and the amount, and waits for your yes. The server checks you heard the current version before anything is sent.
- **Pulse PIN.** You feel the count as vibrations and tap when it reaches your digit. Nothing is said aloud and nothing is shown on screen.
- **Stops scams in the moment.** If you are on a phone call and sending a large amount to someone new, it pauses and asks if someone is telling you to do this. A trusted co-approver can be set for large transfers. Switching off these protections needs your PIN.
- **Works the way you do.** Voice mode, a plain large-text mode, switch scanning for motor impairments, and a sign-language help call. It works alongside TalkBack without talking over it.

TalkBack makes any app readable. Òmìnira makes banking doable, safely, in your own language.

## How it is built

```
Android app  ──HTTPS──>  Gateway  ──>  Zenith core (a mock now, the Zenith sandbox in the pilot)
                           ├── Claude: reads and drafts, never moves money
                           ├── YarnGPT: speech in and out, in five Nigerian languages
                           └── audit log, hash-chained
```

**The assistant drafts, plain code moves money.** Every transfer or card block starts as a draft. Only the customer's own confirm, with the Pulse PIN, can carry it out, through a confirm gate of eight checks in a fixed order: the draft is real, the readback was heard, limits, scam pause, PIN, co-approver, execute exactly once, audit.

| Folder | What | Stack |
|---|---|---|
| [`android/`](android/) | The app | Kotlin, Jetpack Compose |
| [`gateway/`](gateway/) | The service between the app and the bank | Python, FastAPI, SQLAlchemy, uv |
| [`docs/api.md`](docs/api.md) | The API contract both sides build to | |

## Run it

**Gateway**

```bash
cd gateway
uv sync
uv run uvicorn ominira.main:app --reload --port 8080   # endpoints at http://localhost:8080/docs
uv run pytest -q
```

Without an Anthropic key the assistant uses an offline rule parser, and without a YarnGPT key the app uses the phone's own voice, so it runs on a laptop with no keys at all. See [`gateway/README.md`](gateway/README.md).

**Android**

Open `android/` in Android Studio and run the `app` configuration on a phone with Android 8.0 or later. It runs fully offline by default. To use a Gateway, set `ominira.useFakeApi=false` and `ominira.gatewayUrl` in your own `~/.gradle/gradle.properties`. See [`android/README.md`](android/README.md).

Every push builds the debug APK on GitHub Actions. Download it from **Actions > Android > latest run > ominira-debug-apk**.

## Try it

Demo customer Ronke, Pulse PIN **2 0 7 5**.

1. "Send twenty thousand to Tolu", say yes, pulse the PIN: "Sent. Tolu has it."
2. Settings > Pretend I am on a phone call, then send ₦150,000 to Musa: the scam pause, then Funmi's approval at `/approve?token=demo-approver` on a second phone.
3. "Block my card."

The full demo path is in [`android/README.md`](android/README.md) and the scripts are at the end of [`docs/api.md`](docs/api.md).

## What is simulated

There is no live banking at the hackathon. The Zenith core is a mock that behaves like the real rails (NIP response codes, delays, in-progress transfers, failures on demand) and is swapped for the Zenith sandbox in one place. The camera steps in onboarding, the sign-language video call and device passkeys are placeholders. The full list is in each README.

## Credits

Speech by [YarnGPT](https://yarngpt.ai). Fonts: Andika (SIL) and Unbounded, under the SIL Open Font License.
