# Òmìnira

**Bank by yourself.** Voice-first, accessible banking for every Zenith Bank customer, built for Zecathon 6.0, Track 7.

Òmìnira means self-sufficiency. It is for anyone a normal banking app leaves out: blind and low-vision customers, deaf customers, older people, people with motor impairments, and people who don't bank in English. You talk to it in English, Pidgin, Yorùbá, Hausa or Igbo, and it talks back in the same language.

## Why

A 2026 survey of 185 visually impaired adults in Oyo State (Abilu and Adio, 2026) found:

- **Voice banking is the least used and among the most wanted.** Voice-enabled services scored 1.89 out of 4 for use, the lowest of any channel, yet 92% want them.
- **The top problem is having no audio or voice guidance** (84% agree), level with poor network.
- **Bank apps don't work well with screen readers** (76% agree), and respondents rejected the statement that banking apps are designed for them.
- **People cope by depending on others.** Asking bank staff is the most common strategy, and many rely on friends or family. The study notes that this dependence exposes customers to fraud and loss of privacy.
- **Everyone asked for training** (100% agree), along with accessible ATMs and voice-enabled banking.

Òmìnira answers the first four directly: guidance spoken in the customer's own language, an app that does not depend on the bank's screen labels, and a co-approver who can approve or decline but never sees the PIN or touches the money. The study's sample is purposive and covers one state, so we read it as strong local evidence, not a national figure.

> Abilu, R. A., & Adio, J. A. (2026). Challenges and Prospects of E-banking System for Persons with Visual Impairment in Oyo State, Nigeria. *Asian Journal of Economics, Business and Accounting*, 26(9), 15–26. https://doi.org/10.9734/ajeba/2026/v26i92364

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

## Responsible use of AI

Òmìnira serves people who are often targeted by fraud and who may not be able to check a screen. So the AI is kept to understanding and speaking, and everything that touches money stays in plain, tested code.

The full account, including how we used AI to build Òmìnira and the risks still open, is in [RESPONSIBLE_AI.md](RESPONSIBLE_AI.md).

**Where AI is used**

| What | Model | Where it runs | What it sees |
|---|---|---|---|
| Understanding a request ("send twenty thousand to Tolu") | Claude (Anthropic) | Gateway | The request as text, with account numbers and BVNs masked |
| Speech to text and text to speech, five languages | YarnGPT | YarnGPT's API, called by the Gateway | The customer's voice, or the line to be spoken |
| Framing the selfie and reading the ID card | ML Kit face detection and text recognition | On the phone, bundled | Camera frames, which never leave the phone |
| Hearing a spoken account number on a phone call | Google Speech-to-Text | Google Cloud, called by the Gateway with its own service account | That one utterance. Google's optional data logging is off, so the audio is not kept |

**AI never moves money.** Claude's tools can read a balance, list saved people, look up a name, and prepare a transfer or a card block as a draft. Only the customer's own confirm, with their Pulse PIN, can carry a draft out, through the confirm gate's checks in a fixed order. A model that misunderstands, or is talked into something, can at most prepare a draft the customer then hears and refuses.

**The customer hears facts, not model wording.** The readback before money moves (who, which bank, the last four digits, the amount) is built from the bank's own name enquiry, in fixed wording. The server refuses a confirm unless the customer heard the current readback.

**The model never sees secrets.**
- The Pulse PIN is never spoken, typed or shown. It is sealed on the phone the moment it is entered and goes only to the bank.
- If a customer starts to say a PIN aloud, the turn is stopped before any model sees it, and the customer is told never to say it.
- Account numbers and BVNs are replaced with labels such as ACCOUNT_1 before a request reaches Claude, and what is stored for each turn is the masked text.

**Less data leaves the phone.**
- The selfie and the ID card are read on the phone. The app sends the four liveness digits the customer said back, and the name, the last four NIN digits and the date of birth. It never sends an image.
- The live phone call keeps no audio. Each spoken turn is sent to YarnGPT to be transcribed (the account number to Google Speech-to-Text, which reads digits more reliably over a phone line) and is not stored by the Gateway. The log records how many digits of an account number were heard, never which.

**Protection against scams.**
- A large transfer to someone new during a phone call pauses and asks whether someone is telling the customer to do this. A phone call to the bank's own line always counts as a call.
- A trusted co-approver can be required for large transfers. They approve or decline on their own phone and never see the PIN or touch the money.
- Switching any protection off needs the PIN.

**It works without the AI.** If Claude is unavailable or not configured, a keyword parser takes the request in all five languages, and each turn records which one answered. Without YarnGPT, the app speaks with the phone's own voice and bundled clips.

**Accountability.** Every draft, confirm, refusal, pause and approval is written to an append-only, hash-chained audit log.

**Known limits**
- Speech recognition is less accurate for some accents, for noisy lines and for Yorùbá, Hausa and Igbo. The readback and the yes or no that follows are there to catch a mishearing before money moves.
- The Yorùbá, Hausa and Igbo wording is our draft and has not yet been reviewed by native speakers. That review is planned before any pilot.
- The voices are synthetic.
- The selfie's liveness check and the document check are placeholders. A real deployment needs certified checks and the bank's own review.
- The step-by-step phone line kept as a fallback records Yorùbá, Hausa and Igbo requests on Twilio before transcribing them. The live call does not.

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

**CI/CD** (GitHub Actions)

- Every push and pull request runs the Gateway's tests and builds the Android APK with its unit tests.
- On `main`, after the tests pass, the Gateway deploys to Cloud Run (`ominira-gateway`, africa-south1). GitHub signs in to Google Cloud through Workload Identity Federation, so no key is stored, and only this repository is trusted. The Gateway's keys and settings live on the Cloud Run service and stay as they are across deploys.
- On `main`, the APK points at the live Gateway (the `OMINIRA_GATEWAY_URL` secret) and is published to the **demo** pre-release: open its page on each demo phone and install `ominira-demo.apk`.
- The deployed Gateway keeps its data in Cloud SQL (Postgres, `ominira-db`, africa-south1); the connection string is in Secret Manager (`ominira-database-url`), readable only by the service. Sign-ups, balances and transfers survive deploys and restarts.
- A change that adds a column to an existing table needs one mock reset after it deploys (`POST /_mock/reset` with `X-Mock-Admin`): the Gateway creates missing tables on start but does not alter existing ones, and the reset rebuilds the demo world.

## Try it

Demo customer Ronke. Sign up and choose your own Pulse PIN, or use the demo PIN **2 0 7 5**.

1. "Send twenty thousand to Tolu", say yes, pulse the PIN: "Sent. Tolu has it."
2. Settings > Pretend I am on a phone call, then send ₦150,000 to Musa: the scam pause, then Funmi's approval at `/approve?token=demo-approver` on a second phone.
3. "Block my card."

The full demo path is in [`android/README.md`](android/README.md) and the scripts are at the end of [`docs/api.md`](docs/api.md).

## What is simulated

There is no live banking at the hackathon. The Zenith core is a mock that behaves like the real rails (NIP response codes, delays, in-progress transfers, failures on demand) and is swapped for the Zenith sandbox in one place. The selfie and the ID card use the phone's real cameras, read on the phone with ML Kit; the liveness and document checks behind them, the NIBSS BVN lookup, the sign-language video call and device passkeys are placeholders. The full list is in each README, and what does not work yet is in [`docs/known-issues.md`](docs/known-issues.md).

## Credits

Speech by [YarnGPT](https://yarngpt.ai). Fonts: Andika (SIL) and Unbounded, under the SIL Open Font License.
