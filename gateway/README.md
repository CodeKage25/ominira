# Òmìnira Gateway

The one service between the Òmìnira app and Zenith's core banking. The app talks only to this
Gateway, over the contract in [`../docs/api.md`](../docs/api.md).

One rule shapes the whole design: **the assistant drafts, plain code moves money.** Claude (or the
offline rule parser) can read and create drafts. Only the customer's own token, with the Pulse PIN,
can confirm, cancel or answer a scam pause, and only the approver's token can approve. The
assistant never sees a PIN or a full account number.

There is no live banking at the hackathon, so Zenith's core is a mock built to behave like the real
rails: NIP response codes, realistic delays, transfers that come back "in progress", timeouts, and
failures on demand. The Zenith sandbox replaces it in one file, `ominira/core/__init__.py`.

## Run it

```bash
uv sync
uv run uvicorn ominira.main:app --reload --port 8080
uv run pytest -q
```

Open http://localhost:8080/docs to see every endpoint. With the team's `.env` one folder up:

```bash
uv run --env-file ../.env uvicorn ominira.main:app --port 8080
```

Every setting has a safe laptop default; `.env.example` lists them all. Without
`ANTHROPIC_API_KEY` the assistant uses the offline rule parser, and without `YARNGPT_API_KEY` the
app uses the phone's own voice, so the demo still runs on venue Wi-Fi that blocks both.

The tests use a temporary SQLite file, the fast mock profile, and fakes for Claude and YarnGPT, so
they need no network. `TEST_DATABASE_URL=postgresql+psycopg://...` runs the same suite on Postgres.

## Where things are

| Path | What it does |
|---|---|
| `ominira/gate.py` | The confirm gate: eight checks in order, execution, requery, pause, cancel, approvals |
| `ominira/drafting.py`, `readback.py` | Drafts, the readback the customer hears, and its hash |
| `ominira/core/base.py` | The interface to Zenith's core; the sandbox client will implement it |
| `ominira/core/mock.py` | The mock core and its rehearsal controls |
| `ominira/assistant/` | `guard.py` (spoken PIN, masking), `rules.py`, `claude.py`, `toolbox.py` |
| `ominira/speech/yarngpt.py` | YarnGPT client: speak (ticket links) and listen (transcripts) |
| `ominira/lines.py` | Every line a customer hears, in English and Pidgin |
| `ominira/audit_log.py` | Hash-chained audit log |
| `ominira/idempotency.py` | `Idempotency-Key` replay, 24 hours, per token |
| `ominira/routes/` | The HTTP endpoints, one file per area |
| `ominira/static/approve.html` | Funmi's approval page |
| `scripts/speech_field_test.py` | Checks YarnGPT with a real key |
| `scripts/make_language_clips.py` | Makes the language picker's offline clips |

## The demo world

| | |
|---|---|
| Customer | Ronke Adeyemi (`cus_ronke`), account `ac_1` ending 4567, balance ₦842,300, Pulse PIN **2 0 7 5** |
| Saved people | Tolu (Adebayo Tolulope, Zenith Bank, ending 4471), Mama (Adeyemi Folake, GTBank, ending 0213), Musa (Musa Ibrahim, OPay, ending 7710, **new**, never paid) |
| Co-approver | Funmi, for transfers above ₦50,000. She has 15 minutes to answer |
| Card | `cd_1`, ending 8830 |
| Limits | ₦1,000,000 per transfer, ₦5,000,000 a day |
| Tokens | `demo-customer` (Ronke's phone), `demo-approver` (Funmi), `demo-assistant` (the assistant's read-and-draft identity) |

The app's placeholder PIN block for 2 0 7 5 is `demo-7924` (each digit becomes 9 minus the digit). A new sign-up chooses its own PIN with `POST /v1/onboarding/{id}/pin`; the mock core refuses one digit four times, a run up or down, and a repeated pair. `POST /v1/onboarding/{id}/bvn-check` stands in for the NIBSS lookup the customer consents to, and always matches.

Accounts with special behaviour on the mock rails: ten identical digits (07, invalid); any number
ending `0006`, or UBA `2109876543` (06, dormant); BVN `12345678901` (someone else's). Any other
10-digit number at a listed bank gets a consistent generated name, as a real name enquiry would.

## Things to try

- **Happy path:** "send twenty thousand to Tolu", confirm with the PIN, and hear "Sent. Tolu has it."
- **Two turns:** "send money to Tolu", then "twenty thousand". Pidgin works too: "abeg send 20k give Tolu".
- **Scam pause:** ₦50,000 or more to Musa with `call_active: true` gives `409 pause_required`.
  `stop` cancels and flags Musa; `chose_myself` adds "Zenith will never call you to move money..."
- **Co-approver:** anything above ₦50,000 gives `202 approval_required`. Open
  **`/approve?token=demo-approver`** on a second phone and hand it to a judge. Just before an approved
  transfer goes, the account and limits are checked again; if the name changed, nothing moves and the
  customer hears the new readback.
- **Wrong PIN:** two tries left, then one, then locked.
- **Someone new:** `POST /v1/payees/lookup`, then a draft with `bank_code` and `account_number`. The first payment saves them.
- **The assistant cannot move money:** any confirm, cancel, pause answer or decision with `demo-assistant` is `403`.
- **A spoken PIN:** "my PIN is 2075, send..." (or "2075 is my PIN", or "pin mi ni 2075") is caught in code before any model call. Nothing is drafted.
- **A scammer on the phone can't switch off your protections without your PIN:** raising the scam-pause
  threshold, raising or clearing the daily limit, and adding or removing the co-approver all answer
  `401 pin_required` until the customer pulses their PIN. Lowering a limit needs no PIN.
- **Typing the number doesn't dodge a flag:** after "stop" on Musa, a draft to OPay `8031447710` is
  still Musa's draft, so it pauses, and every other open request to Musa was cancelled.

### Rehearsal commands

These use `jq`. `G` is the Gateway's address.

```bash
G=http://localhost:8080
C='Authorization: Bearer demo-customer'
J='Content-Type: application/json'

# Script 1: say it, hear the readback, confirm with the PIN
CV=$(curl -s -X POST $G/v1/conversations -H "$C" | jq -r .id)
T=$(curl -s -X POST $G/v1/conversations/$CV/turns -H "$C" -H "$J" -d '{"text":"send twenty thousand to Tolu"}')
echo "$T" | jq '.say, .draft.readback.spoken'
D=$(echo "$T" | jq -r .draft.id); H=$(echo "$T" | jq -r .draft.readback_hash)
curl -s -X POST $G/v1/drafts/$D/confirm -H "$C" -H "$J" -H "Idempotency-Key: $(uuidgen)" \
  -d "{\"readback_hash\":\"$H\",\"pin_block\":\"demo-7924\",\"call_active\":false}" | jq

# Script 3: Musa during a call, carry on, then Funmi approves on /approve
R=$(curl -s -X POST $G/v1/drafts -H "$C" -H "$J" -d '{"kind":"transfer","payee_id":"py_musa","amount_kobo":15000000}')
D=$(echo "$R" | jq -r .id); H=$(echo "$R" | jq -r .readback_hash)
BODY="{\"readback_hash\":\"$H\",\"pin_block\":\"demo-7924\",\"call_active\":true}"
curl -s -X POST $G/v1/drafts/$D/confirm -H "$C" -H "$J" -d "$BODY" | jq .code       # pause_required
curl -s -X POST $G/v1/drafts/$D/pause-answer -H "$C" -H "$J" -d '{"answer":"chose_myself"}' | jq .say
curl -s -X POST $G/v1/drafts/$D/confirm -H "$C" -H "$J" -d "$BODY" | jq .code       # approval_required
curl -s $G/v1/drafts/$D -H "$C" | jq .status                                        # poll after Funmi taps Approve

# Script 4: block the card
R=$(curl -s -X POST $G/v1/drafts -H "$C" -H "$J" -d '{"kind":"card_block","card_id":"cd_1"}')
curl -s -X POST $G/v1/drafts/$(echo "$R" | jq -r .id)/confirm -H "$C" -H "$J" \
  -d "{\"readback_hash\":$(echo "$R" | jq .readback_hash),\"pin_block\":\"demo-7924\"}" | jq .say

# Steer the mock core
curl -s -X POST $G/_mock/reset | jq .balances                                     # back to the start
curl -s -X POST $G/_mock/controls -H "$J" -d '{"banks_down":["999992"]}'          # OPay stops answering
curl -s -X POST $G/_mock/controls -H "$J" -d '{"next_transfer_code":"91"}'        # next transfer: bank unavailable
curl -s -X POST $G/_mock/controls -H "$J" -d '{"next_transfer_code":"09","settle_after_s":30}'  # "in progress" for 30 s
curl -s -X POST $G/_mock/controls -H "$J" -d '{"next_transfer_timeout":"after_send"}'  # went through, answer lost
curl -s -X POST $G/_mock/controls -H "$J" -d '{"profile":"flaky"}'               # random 91/96, slower, Kuda down
curl -s $G/_mock/state | jq
```

On a public deployment set `MOCK_ADMIN_TOKEN` and add `-H "X-Mock-Admin: $TOKEN"`, or set
`MOCK_ADMIN=false` to switch these off (404).

Protective settings need the PIN, for example:

```bash
curl -s -X PUT $G/v1/me/preferences -H "$C" -H "$J" -d '{"pause_threshold_kobo":20000000}' | jq .code   # pin_required
curl -s -X PUT $G/v1/me/preferences -H "$C" -H "$J" -d '{"pause_threshold_kobo":20000000,"pin_block":"demo-7924"}' | jq
curl -s -X DELETE $G/v1/approvers/ap_funmi -H "$C" -H "X-Pin-Block: demo-7924" | jq
```

## How money is kept safe

- **The gate runs in order and stops at the first failure:** real draft, readback hash, limits,
  scam pause, PIN, co-approver, execute, audit. The PIN comes before the co-approver (the contract's
  order), so Funmi is never asked about something Ronke could not authorise.
- **Never twice:** a draft is marked `processing` before Zenith is called, under a per-draft lock
  (and `SELECT ... FOR UPDATE` on Postgres). Zenith's key is `{draft_id}:{attempt}`. A timeout or
  "in progress" keeps the key, and the draft stays `processing` (it cannot be cancelled or expire)
  until Zenith gives a final answer for that key; if Zenith has no record, the next re-check sends it
  again under the same key. Only a definite "not sent" (91, 96) moves the attempt on. A double tap,
  a retry after a timeout and a late reply all land on the same transfer.
- **Limits hold:** a transfer's limit check and the transfer itself run under a per-customer lock,
  so two drafts confirmed at once cannot both fit under the daily limit.
- **Flags stick:** a draft to an account number that belongs to a saved payee is that payee's
  draft. "Stop" on a scam pause flags the payee and cancels every other open request to them, and an
  approval for a payee flagged while it waited is held, not paid.
- **PIN guessing is slow:** 5 PIN checks a minute per token (unlock, confirm and settings together),
  on top of Zenith's lock after three wrong tries. New-account name enquiries are 5 a minute per
  customer, whether they come from a lookup, a draft or the assistant.
- **Idempotency-Key** replays the first answer for 24 hours; 5xx and 429 answers are not kept. The
  stored fingerprints are HMACs under `IDEMPOTENCY_SECRET` (random per process when unset), so the
  table cannot be used to guess a PIN block.
- **Audit:** every step is in a hash-chained log. `GET /v1/audit/verify` finds an edited, deleted or
  reordered row.
- **No secrets stored:** no PIN, PIN block or BVN anywhere; a full account number lives in a draft
  only while it is open, and expired drafts are swept at start and whenever a draft is created.
  Account labels for the assistant live in process memory, not the database.

## The assistant

`POST /v1/conversations/{id}/turns` first checks the words in code: a spoken PIN stops the turn
before any model call, and account numbers and BVNs are masked (`ACCOUNT_1`, `[BVN]`) in what is
stored and returned as `heard`. Then Claude answers when `ASSISTANT=claude` and `ANTHROPIC_API_KEY`
is set, with read-only tools plus draft creation (there is no confirm tool at all). Any Claude error
or a slow answer (`CLAUDE_TIMEOUT_S`, default 8) falls back to the rule parser for that turn. The
default model is `CLAUDE_MODEL=claude-sonnet-5-5`.

Lines follow the customer's `lang` preference on the very next call. English and Pidgin are
written; Yorùbá, Hausa and Igbo fall back to English until a native speaker has written them. A few
assistant prompts ("How much should I send to Tolu?", the help line, "came in this week") are still
English only and need Pidgin copy.

## Speech (YarnGPT)

The Gateway is the only place the YarnGPT key lives. Put `YARNGPT_API_KEY` in `../.env`.

| Endpoint | What it does |
|---|---|
| `GET /v1/speech/status` | Whether cloud speech is on |
| `POST /v1/speech/say` | Text in, a five-minute audio link out. The phone plays it straight from YarnGPT |
| `POST /v1/speech/listen` | One recording in (raw `audio/wav` body), its transcript out. A spoken PIN comes back as `pin_spoken` |
| `POST /v1/conversations/{id}/voice-turns` | One recording in: transcript, assistant turn, draft, and the reply as an audio link, with timings |

If YarnGPT is down or slow, these answer `503 speech_unavailable` with a `say` line, and the app
uses the phone's own voice. Pin a voice per language with `YARNGPT_VOICES="yo=<id>,ha=<id>,ig=<id>"`
once a native speaker has picked them.

**Field test** (about fifteen calls of credit):

```bash
set -a; . ../.env; set +a
uv run python scripts/speech_field_test.py                       # all five languages
uv run python scripts/speech_field_test.py --gateway http://localhost:8080
```

It lists the voices and the languages transcription covers, speaks a line in each language the
way the phone receives it (ticket, then stream) into `speech_out/`, transcribes each back, and times
every step.

**Language picker clips.** The app's first screen speaks one line per language before sign-in, so
those clips ship inside the APK:

```bash
set -a; . ../.env; set +a
uv run python scripts/make_language_clips.py                     # writes ../android/app/src/main/assets/audio/lang_<code>.mp3
uv run python scripts/make_language_clips.py --only yo --voice yo=<id> --out /tmp/clips
```

The five lines sit in `LINES` at the top of the script for native speakers to edit. Each language
uses its pinned voice, else the first catalogue voice for that language, else YarnGPT's default.
The script prints each clip's size and length and stops with a clear message when the key is missing.

## Deploy (Cloud Run)

Project `united-strategy-472602-a5`, region `africa-south1`. From this folder:

```bash
P=united-strategy-472602-a5
IMG=africa-south1-docker.pkg.dev/$P/ominira/gateway:$(git rev-parse --short HEAD)
gcloud builds submit --project $P --tag $IMG .          # or: docker build -t $IMG . && docker push $IMG
gcloud run deploy gateway --project $P --region africa-south1 --image $IMG \
  --allow-unauthenticated --no-invoker-iam-check --min-instances 1 --max-instances 1 \
  --set-env-vars ZENITH_PROFILE=realistic,ASSISTANT=claude,CLAUDE_MODEL=claude-sonnet-5-5,DATABASE_URL=sqlite:////tmp/ominira.db \
  --set-secrets ANTHROPIC_API_KEY=anthropic-api-key:latest,YARNGPT_API_KEY=yarngpt-api-key:latest,MOCK_ADMIN_TOKEN=mock-admin-token:latest,IDEMPOTENCY_SECRET=idempotency-secret:latest
```

Create any secret that does not exist yet with
`printf %s "$VALUE" | gcloud secrets create NAME --project $P --data-file=-`.

Things that matter on Cloud Run:

- **`--no-invoker-iam-check` is required.** Without it Cloud Run reads `Authorization: Bearer demo-customer` as a Google token and answers 401.
- **Cloud Run reserves `/healthz`.** The health check is `GET /health`.
- **`--max-instances 1` while the state lives in SQLite.** The rate limits and idempotency locks are in memory too.
- The container runs as `nobody`, so the SQLite file lives in `/tmp` and resets when the instance restarts.
- With `MOCK_ADMIN_TOKEN` set, the app's "reset demo" call needs the `X-Mock-Admin` header.

For Cloud SQL, set `DATABASE_URL=postgresql+psycopg://USER:PASS@/ominira?host=/cloudsql/PROJECT:africa-south1:INSTANCE`,
add `--add-cloudsql-instances PROJECT:africa-south1:INSTANCE`, and make the audit log append-only
for the app's role:

```sql
REVOKE UPDATE, DELETE, TRUNCATE ON audit_log FROM gateway;
GRANT SELECT, INSERT ON audit_log TO gateway;
```

## Known gaps for the demo

- **Onboarding has no authentication.** Anyone can start a session, and `complete` hands back the
  demo customer's token (and, with a `lang`, sets her language). That is deliberate for the hackathon,
  where every new account is Ronke; the pilot opens a real account and enrols a device passkey there.
- After three wrong PINs only `/_mock/reset` unlocks the mock core. Zenith's real process is a call
  from the bank.

## Not built yet

- USSD (`*966#`) on the same drafts and gate.
- Streamed speech both ways (WebSocket relay with barge-in). One recording per turn works now.
- Push or server-sent events; the app polls `GET /v1/drafts/{id}` every two seconds meanwhile.
- Pilot work: passkey sign-in, ISO 9564 PIN blocks, the Zenith sandbox client, migrations instead
  of `create_all`, signed audit checkpoints.
