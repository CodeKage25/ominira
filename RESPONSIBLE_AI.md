# Responsible use of AI in Òmìnira

This page sets out two things: where AI works inside Òmìnira, and how we used AI to build it. It also covers the limits we know about. The README's "Responsible use of AI" section is the short version of part 1.

## The short version

- **The AI never moves money.** Claude can read the account and prepare a draft. Plain, tested code checks every draft, and only the customer's own Pulse PIN can send money or block a card. The approver's yes is a separate check.
- **The AI never sees a PIN or a full account number.** PINs never reach it at all. Account numbers, BVNs and card numbers are masked in code before any text reaches the model.
- **The app works without AI.** If Claude is slow, down or unreachable, an offline rule parser answers instead. If the speech service is down, the phone's own voice and recogniser take over.
- **We built Òmìnira with heavy AI help.** We used Claude Code to write code, write tests, review the code and draft translations. People on the team decided what to build, directed the work, checked the results and own every commit.

## 1. AI inside the product

### Claude, the assistant

**What it does:** it turns what the customer says ("send twenty thousand to Tolu") into an answer or a draft. It runs on the Gateway (`gateway/ominira/assistant/claude.py`), using the model set by `CLAUDE_MODEL` (default `claude-sonnet-5-5`).

**What it can do:** read the balance, recent transactions, money in this week, saved people and cards; look up a new account by name enquiry; prepare a transfer or a card block.

**What it cannot do:** confirm, cancel, approve, answer a scam pause, or change any setting. Those endpoints refuse the assistant's token with a 403. The model has no tool for any of them.

**What it sees:**
- The customer's words, after masking. Account numbers become labels like `ACCOUNT_1`, and BVNs and card numbers are replaced.
- The customer's first name and the language to reply in.
- Through its tools: the balance, recent transactions with names and amounts, saved people (nickname, name, bank), card last four digits, and name enquiry results.

**What it never sees:**
- The PIN. A spoken PIN ("my PIN is 2075", "PIN2075", "twenty seventy five", and phrasings in Pidgin, Yorùbá, Hausa and Igbo) is caught in code before any model call. Nothing is drafted, and the customer is told never to say it aloud.
- A full account number, BVN or card number.

**What happens to a draft it makes:**
1. The app reads it back aloud.
2. The customer says yes.
3. The customer enters their Pulse PIN.
4. The confirm gate runs in order (`gateway/ominira/gate.py`):
   - name enquiry again, and a check that the readback matches what the customer heard;
   - limits;
   - the scam pause;
   - the PIN;
   - the co-approver;
   - execution with an idempotency key.

A wrong amount or person from the model is therefore heard and refused by the customer, never paid on the model's word alone.

**When it fails:** on any error, timeout (`CLAUDE_TIMEOUT_S`, 8 s by default) or refusal, the offline rule parser (`assistant/rules.py`) answers instead. The rule parser understands English, Pidgin, Yorùbá, Hausa and Igbo for the main requests.

### YarnGPT, speech in and out

**What it does:** it reads lines aloud in Nigerian voices for English, Pidgin, Yorùbá, Hausa and Igbo, and turns the customer's speech into text. Calls go through the Gateway, so the API key never leaves the server (`gateway/ominira/speech/yarngpt.py`).

**What it receives:**
- Text the app is about to speak. This can include names, amounts and the last four digits of an account.
- Recordings of what the customer says, for transcription, from the app and from the live phone line.

**A limit to be clear about:** the spoken-PIN check runs on the transcript. If a customer says their PIN aloud despite being told not to, that audio has already reached the speech service before we catch it. The transcript is discarded and nothing is stored or drafted.

**Without it:** without a YarnGPT key the app uses the phone's own text-to-speech and recogniser. Common lines also ship as bundled audio clips, generated once by `gateway/scripts/make_*_clips.py`. The app never reads a Yorùbá, Hausa or Igbo line in an English voice; it skips the line instead.

### ML Kit, on the phone

ML Kit face detection and text recognition frame the selfie and read the ID card during sign-up. Both run on the phone from bundled models, and the camera frames never leave it. The app sends only:

- the four liveness digits the customer said back;
- the name, the last four NIN digits and the date of birth read from the card.

### The phone line

- **Live call:** keeps no audio. Each spoken turn goes to YarnGPT to be transcribed, and the Gateway doesn't store it.
- **Step-by-step keypad line (the fallback):** records Yorùbá, Hausa and Igbo requests on Twilio before transcribing them.

### What is deliberately not AI

All of the following are plain code with tests, not model output:

- the confirm gate and its order;
- limits, the scam pause and the co-approver;
- the readback text and its hash;
- PIN handling;
- idempotency;
- the audit log (a hash chain);
- the yes or no answers on money screens;
- the voice commands (home, back, balance and so on).

The fixed money lines the customer hears ("Sent. Tolu has it.") come from a reviewed line table (`gateway/ominira/lines.py`), not from the model.

## 2. Known limits and open risks

- **Translations are machine-drafted.** The Yorùbá, Hausa and Igbo app copy, money lines and number words were drafted with AI and are marked "unreviewed" in the code. Native speakers must check them before real customers hear them. Pidgin follows the earlier prototype's wording where one existed.
- **Speech recognition makes mistakes**, especially with accents, tone marks and noisy rooms. The readback is the safeguard: nothing moves until the customer hears the exact amount and name and says yes.
- **Prompt injection.** The model reads data it does not control, such as the name another customer chose at sign-up, which appears in "money in" results. Such text could try to steer the model into drafting a payment. The readback and the PIN stop that draft from paying, but the model can still be led to propose it. This is open.
- **Model judgement.** The model can misunderstand a request. The rules in its prompt (one draft per turn, never guess an amount or a person, ask before sending "all my money") reduce this, and the readback catches what is left.
- **Third-party processing.** Masked conversation text goes to Anthropic, and text and voice go to YarnGPT. For a pilot with Zenith, both need a data processing agreement and a review of where the data is kept, or should be replaced with services Zenith approves.
- **Simulated parts.** This build uses a mock Zenith core, demo tokens instead of device passkeys, and a placeholder PIN block. None of these involve AI, but they mean the safety story above has not yet been tested against the real core.

## 3. How we used AI to build Òmìnira

### Tools

We used Claude Code (Anthropic) in the editor and terminal, running several AI agents in parallel on separate parts of the work.

### What we used it for

- **Writing code**, from specs we gave it: Gateway features, Android screens, the voice host and the offline bank.
- **Writing tests**, including tests that reproduce a bug before it is fixed.
- **Reviewing code.** An end-to-end review of both halves looked at the money path, security, accessibility, the API contract and CI. It found, among other things, that the app could say "Sent" when nothing had gone to the bank, and that the phone line trusted a typed account number. Both are now fixed.
- **Drafting translations** for Yorùbá, Hausa, Igbo and Pidgin. These still need native-speaker review, as above.
- **Writing docs**, including this page, the READMEs and parts of `docs/api.md`.

### What people did

- Decided what to build and for whom, chose between the options the AI laid out, and changed direction mid-build (for example, putting the language choice first and making the mic available on every screen).
- Read and checked the AI's work before it was committed, and corrected it where it was wrong. For example, we removed a rule that would have heard an Igbo "yes" as "no", and fixed a Yorùbá phrase that was read as ₦1.
- Ran the test suites and CI, and tested on real phones.
- Own every commit. Commits are authored by team members and do not carry per-commit AI credit lines; this page is where we disclose AI use instead.

### How we used it, stage by stage

**21 Oct 2026: the first build**

1. **The contract.**
   - We wrote down the API between the app and the Gateway first, in `docs/api.md`, with the demo world we had rehearsed.
   - AI helped turn our prototype's API into that document.
   - We set the rules it encodes: the assistant only drafts, and the confirm gate runs in a fixed order.
2. **The Gateway and the app.**
   - AI wrote both against that contract, in parallel.
   - We set the stack, the screens, the safety rules and the demo scripts, and checked each part as it came in.
3. **A change of direction.**
   - Midway, we decided the app should open on a language choice, by voice or tap, with each language spoken in its own voice.
   - AI made the change on both sides to our spec.
4. **A security review.**
   - We had AI review the money paths independently, looking for double payment, role bypass and leaks.
   - It found no double payment or role bypass, but did find 12 smaller holes. We fixed them with AI and added 49 regression tests.
5. **Clean-up.**
   - AI trimmed verbose comments across both halves.
   - The design reasoning moved into our project notes.

**22 Oct 2026: review, languages and the mic everywhere (measured)**

- 10 commits on `main` came from AI-written changes: about 3,200 lines added and 800 removed across the Gateway and the app.
  - Gateway: Yorùbá, Hausa and Igbo money lines and amounts; real seeded customers; the onboarding lock; the phone caller check; masking and PIN-guard fixes.
  - Android: the Speak dock and voice commands; the money fallback fix; yes and no in every language; the hold-mode Pulse PIN fixes.
- Each change started from a written spec of ours. Each was checked against the test suites (412 Gateway tests pass) and CI, and reviewed before commit.

**Overall**

Across the project, we estimate that about half of the code was written with AI assistance. That figure is about code only. Much of the work that makes Òmìnira run is not code, and the team did it:

- **The problem.** Choosing it, the people it serves, and the Pulse PIN idea.
- **The screens.** Designing each one and how it should feel to a blind, deaf, older or motor-impaired customer.
- **The voices.** Generating the speech clips for all five languages with YarnGPT, choosing the voices, and listening to every clip before it went in.
- **The services.** Configuring the APIs and keys: Anthropic, YarnGPT, Twilio, Cloud SQL and Secret Manager.
- **Deployment.** Deploying the Gateway to Cloud Run and setting up CI.
- **Testing.** Running every flow on real phones, with TalkBack, switch access and wired earphones, and in each language.
- **The demo.** Writing the demo world and scripts, and the pitch.

For scale, the repository holds about 19,000 lines of Gateway and Android code and 3,300 lines of Gateway tests, across 92 commits (39 by Abdulkareem, 53 by Deniyi, as of 22 Oct 2026).

## 4. What we commit to before real customers

1. Native speakers review every Yorùbá, Hausa, Igbo and Pidgin line and number word.
2. Data processing agreements, or Zenith-approved replacements, for the language model and speech services.
3. Prompt-injection hardening: the model only drafts to a payee the customer named in the same turn.
4. Testing with blind and low-vision customers, deaf customers and older customers, in each language, on real phones.
5. Keep the rule that plain code, not the model, decides whether money moves.
