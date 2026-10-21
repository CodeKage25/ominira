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
- Every Gateway deploy starts its mock bank afresh: customers who signed up, and their balances, are gone. Do not push Gateway changes in the hours before a demo.

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
