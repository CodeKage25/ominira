# Òmìnira API contract

The one agreement between the Android app (`android/`) and the Gateway (`gateway/`). If the code and
this file disagree, fix one of them in the same change.

- JSON over HTTPS. Base path `/v1`. Times are ISO 8601 UTC with a trailing `Z`.
- Money is **kobo as integers** everywhere on the wire (`amount_kobo`, `balance_kobo`). ₦1 = 100 kobo.
- Auth: `Authorization: Bearer <token>`. Hackathon tokens stand in for device-bound passkeys.
- Every state-changing call may send `Idempotency-Key: <uuid>`. The Gateway stores the response for
  24 h per token, method and path, and replays it for the same key and body with the header
  `Idempotent-Replayed: true`; the same key with a different body is `422 idempotency_key_reused`.
  5xx and 429 answers are not stored, so retrying them with the same key really retries. Use a new
  key for each new attempt the customer makes (a new PIN entry is a new attempt). Stored request
  fingerprints are HMACs under a server secret (`IDEMPOTENCY_SECRET`), never plain hashes, so a stored
  confirm cannot be used to guess a PIN block.
- Anything a customer hears carries `say`: one line, already in the customer's language.
- Languages: `en`, `pcm` (Pidgin), `yo`, `ha`, `ig`. Every `say` and readback line, amounts and digits
  included, is in the customer's language ("Naira dubu ashirin."); Pidgin says amounts in English words.
  Yorùbá, Hausa and Igbo copy is an unreviewed draft until native speakers check it.

## One rule

**The assistant drafts, plain code moves money.** The assistant (Claude, or the offline rule parser)
can read and create drafts. Only the customer's own token can confirm, cancel or answer a pause, and
only the approver's token can approve. The assistant never sees a PIN or a full account number.

## Roles and demo tokens

| Token | Role | Can |
|---|---|---|
| `demo-customer` | customer (Ronke's phone) | everything for Ronke |
| `demo-approver` | approver (Funmi) | `GET /v1/approvals`, `POST /v1/approvals/{id}/decision` |
| `demo-assistant` | assistant | read endpoints and `POST /v1/drafts`; every confirm, cancel, pause-answer and decision call is `403 forbidden` |
| `demo-tolu`, `demo-mama`, `demo-musa`, `demo-aisha`, `demo-emeka`, `demo-chika` | customer | everything for that person (see the demo world) |

## Errors

RFC 9457 problem details, `Content-Type: application/problem+json`:

```json
{ "type": "https://docs.ominira.app/errors/pin_incorrect", "title": "The PIN did not match",
  "status": 401, "code": "pin_incorrect", "say": "That PIN did not match. ...", "tries_left": 2 }
```

Clients switch on `code`, never on `title`. Extra fields sit at the top level. `Retry-After` is set
whenever the body has `retry_after`.

| HTTP | code | Extra fields | When |
|---|---|---|---|
| 401 | `unauthorized` | | missing or unknown token |
| 401 | `pin_incorrect` | `tries_left` (0 means locked) | Pulse PIN did not match |
| 401 | `pin_required` | `say` | a call that weakens a protection was sent without `pin_block` |
| 403 | `forbidden` | `say` | wrong role for this call, or an approver who was removed or replaced |
| 404 | `not_found`, `payee_not_found`, `card_not_found` | | |
| 409 | `pause_required` | `draft_id` | scam pause; answer with pause-answer |
| 409 | `readback_mismatch` | `readback`, `readback_hash` | read the new readback to the customer, then confirm with the new hash |
| 409 | `already_executed` | `reference` | |
| 409 | `processing` | | transfer already on its way |
| 409 | `draft_closed` | `status_now` | cancelled or failed |
| 409 | `already_blocked` | | card already blocked |
| 409 | `not_paused`, `already_decided`, `onboarding_incomplete` | | |
| 409 | `onboarding_closed` | | `complete` called again, or `pin` after the sign-up's PIN was set |
| 410 | `draft_expired` | `draft_id` | older than 10 minutes |
| 410 | `approval_expired` | `draft_id` | approver did not answer within 15 minutes |
| 413 / 415 / 422 | `audio_too_long` / `audio_expected` / `audio_too_short` | | speech uploads |
| 422 | `invalid_request` | `errors` | validation |
| 422 | `weak_pin` | | PIN too easy to guess |
| 422 | `idempotency_key_reused` | | same Idempotency-Key, different body |
| 422 | `invalid_amount`, `invalid_account`, `account_dormant`, `unknown_bank` | | |
| 422 | `limit_exceeded` | `limit` (`per_transaction` or `daily`), `max_kobo` or `remaining_today_kobo` | |
| 422 | `insufficient_funds`, `failed` | `zenith_code` on `failed` | definite "not sent" |
| 429 | `rate_limited` | `retry_after` | name enquiries (5 a minute per customer), PIN checks (5 a minute per token), speech |
| 503 | `bank_unavailable` | `retry_after`, `zenith_code` | definite "not sent" (NIP 91 or 96, or the name enquiry failed); the draft stays open and can be confirmed again |
| 503 | `speech_unavailable` | `provider_code` | use the phone's own voice |

## Drafts and the confirm gate

Every money action is a draft first. Status machine:

```
awaiting_confirm ──confirm──> paused ──pause-answer stop──> cancelled
       │                        └──pause-answer chose_myself──> awaiting_confirm
       ├──confirm (PIN ok, over approver threshold)──> awaiting_approval ──approve──> executed
       │                                                       ├──decline──> cancelled
       │                                                       └──15 min──> expired
       ├──confirm──> executed | processing (in progress, or no answer yet; GET re-checks by key)
       ├──confirm, definite not-sent (NIP 91/96)──> awaiting_confirm (attempt + 1)
       ├──confirm, refused──> failed
       ├──cancel──> cancelled
       └──10 min──> expired
```

`POST /v1/drafts/{id}/confirm` runs these checks in order. The first that fails decides the answer.

1. **Real draft**: this customer's, open, not expired.
2. **Heard it**: name enquiry again, rebuild the readback; its hash must equal `readback_hash` sent.
   A draft made from an account number that belongs to a saved payee is that payee's draft, so
   `is_new`, `flagged` and the warning line all apply.
3. **Limits**: Zenith per-transaction and daily limits, then the customer's own lower daily limit.
4. **Scam pause** (transfers): (new payee AND amount ≥ pause threshold (₦50,000) AND `call_active`)
   OR payee flagged, and the customer has not already answered a pause on this draft.
5. **Pulse PIN**: the PIN block goes to Zenith; the Gateway learns only ok or tries left. Never stored or logged.
6. **Co-approver** (transfers): amount above the active approver's threshold → `awaiting_approval`.
   The PIN is checked before the approver is asked, so an approved draft executes without a second PIN.
7. **Execute** with Zenith idempotency key `{draft_id}:{attempt}`. Timeout or "in progress" keeps the
   key and the draft stays `processing` until Zenith gives a final answer for that key: it cannot be
   cancelled (`409 processing`) and never expires. `GET /v1/drafts/{id}` re-checks it, and if Zenith
   has no record of the key, sends it again under the same key, so a late arrival and the resend are
   one transfer. Only a definite "not sent" (91, 96) moves the attempt on.
8. **Audit** (hash-chained) and reply with `say`.

Steps 3 to 7 of a transfer run under a per-customer lock, so two drafts confirmed at once cannot
both fit under the same daily limit. Drafts and approvals that run out of time are closed at start
and whenever a draft is created, so no full account number lingers.

`readback_hash` = `"sha256:" + hex(sha256(canonical JSON of {kind, name, bank, account_last4, amount_kobo}))`,
canonical meaning sorted keys and `(",", ":")` separators. Clients never compute it; they echo it.

### Draft object

```json
{ "id": "dr_x8Kq2a", "kind": "transfer", "status": "awaiting_confirm", "source": "app",
  "readback": { "kind": "transfer", "name": "Adebayo Tolulope", "bank": "Zenith Bank", "account_last4": "4471",
                "amount_kobo": 2000000, "amount_text": "₦20,000", "lang": "en",
                "spoken": ["Twenty thousand naira.", "To Adebayo Tolulope.", "Zenith Bank, ending four, four, seven, one.",
                           "Pulse your PIN to send."] },
  "readback_hash": "sha256:...", "expires_at": "2026-10-21T10:30:00Z", "reference": null, "say": null }
```

`kind` is `transfer` or `card_block`. For `card_block`, `name` is `"Card ending 8830"`, `bank` is
`"Zenith Bank"`, `account_last4` is the card's last four, and `amount_kobo` and `amount_text` are null. `source` is `app`, `assistant`, `voice` or `ussd`.
`say` holds the latest outcome line once there is one.

## Endpoints

### Health and pages (no auth, outside /v1)

| | |
|---|---|
| `GET /health` | `{"ok": true, "zenith": "mock", "assistant": "claude"\|"rules", "speech": true\|false}` |
| `GET /approve?token=demo-approver` | Funmi's approval page (HTML), polls `GET /v1/approvals` |

### Onboarding (no auth)

| Call | Body | 2xx response |
|---|---|---|
| `POST /v1/onboarding/sessions` | `{"lang": "pcm"}` (optional: the language picked on the first screen) | `201 {"id": "ob_...", "liveness_digits": [4, 1, 9, 0]}`. Later `say` lines use that language, and `complete` saves it as the customer's `lang` |
| `POST /v1/onboarding/{id}/selfie` | `{"spoken_digits": [4,1,9,0]}` (optional) | `{"live": true}`; `false` when the digits sent do not match |
| `POST /v1/onboarding/{id}/id-document` | `{"full_name"}`, said or typed, with `"nin_last4"` and `"born_on"` when a card was read (all optional) | The same three keys, empty when not given. With a body, the name names the new account and only the name is kept; without one, the mock reads `{"full_name": "Ronke Adeyemi", "nin_last4": "5521", "born_on": "1961-03-14"}` |
| `POST /v1/onboarding/{id}/bvn` | `{"bvn": "22212345678"}` | `{"match": true, "say": "..."}` (`match` false for `12345678901`; not 11 digits is `422 invalid_request`) |
| `POST /v1/onboarding/{id}/bvn-check` | | With the customer's consent, Zenith finds the BVN with NIBSS from their name and phone, so nothing is said aloud. `{"match": true, "say": "..."}`, and the session counts as BVN matched |
| `POST /v1/onboarding/{id}/complete` | | `{"customer_id": "cus_...", "token": "..."}`: a new customer with their own Zenith account (₦100,000 in the mock), card and token, and no PIN until `pin`. The token is given once: called again, `409 onboarding_closed`. Or `409 onboarding_incomplete` until the BVN matched (and the selfie, if sent, was live) |
| `POST /v1/onboarding/{id}/pin` | `{"pin_block": "demo-..."}` | The new account's own Pulse PIN, sealed on the phone. `{"ok": true, "say": "..."}`; `409 onboarding_incomplete` before `complete`; `422 weak_pin` for one digit four times, a run up or down, or a repeated pair (try again). Once a PIN is set the sign-up is closed: another `pin` is `409 onboarding_closed` |

### Sign in (customer)

`POST /v1/auth/unlock` `{"pin_block": "..."}` → `{"ok": true, "say": "Welcome back."}` or `401 pin_incorrect`.
Every PIN check (unlock, confirm, protective settings) counts against 5 a minute per token; the
sixth is `429 rate_limited`.

**Placeholder PIN block**: `"demo-"` + each digit replaced by `9 - digit`. PIN 2 0 7 5 → `"demo-7924"`.
The pilot replaces it with an ISO 9564 block encrypted on the device with Zenith's PIN key.

### Money (customer or assistant)

| Call | Response |
|---|---|
| `GET /v1/accounts` | `[{"id": "ac_1", "name": "Current account", "last4": "4567", "balance_kobo": 84230000, "number": "2081234567"}]`. `number` goes to the customer only, to give to someone paying them; the assistant gets `last4` |
| `GET /v1/accounts/{id}/balance` | `{"balance_kobo": 84230000, "say": "You have eight hundred and forty-two thousand, three hundred naira in your current account."}` |
| `GET /v1/accounts/{id}/transactions?limit=20&direction=in\|out` | `[{"id", "who", "amount_kobo", "incoming", "at", "reference"}]`, newest first |
| `GET /v1/accounts/{id}/income-summary` | `{"say": "This week ₦... came in, from ..."}` |
| `GET /v1/banks` | `[{"code": "057", "name": "Zenith Bank Plc", "short": "Zenith Bank"}]` |
| `GET /v1/payees` | `[{"id", "nickname", "name", "bank", "account_last4", "is_new", "flagged"}]` |
| `POST /v1/payees/lookup` `{"bank_code", "account_number"}` | `{"name", "bank", "account_last4", "say"}`; 5 a minute per customer, shared with drafts to new accounts and the assistant's lookups |
| `GET /v1/cards` | `[{"id": "cd_1", "last4": "8830", "holder": "Ronke Adeyemi", "status": "active"\|"blocked"}]` |

### Drafts

| Call | Who | Body | Response |
|---|---|---|---|
| `POST /v1/drafts` | customer, assistant | `{"kind": "transfer", "amount_kobo", "payee_id"}` or `{"kind": "transfer", "amount_kobo", "bank_code", "account_number"}` or `{"kind": "card_block", "card_id"}`; optional `"source"` | `201` draft |
| `GET /v1/drafts/{id}` | customer | | draft (re-checks `processing`, expires drafts older than 10 minutes and stale approvals) |
| `POST /v1/drafts/{id}/confirm` | customer | `{"readback_hash", "pin_block", "call_active": false}` | see below |
| `POST /v1/drafts/{id}/pause-answer` | customer | `{"answer": "stop"\|"chose_myself"}` | `{"status": "cancelled"\|"awaiting_confirm", "say"}`. `stop` flags the payee and cancels every other open draft to them, withdrawing their approvals |
| `POST /v1/drafts/{id}/cancel` | customer | | `{"status": "cancelled", "say"}` |

Confirm answers:

- `200 {"status": "executed", "reference": "ZEN...", "say": "Sent. Tolu has it."}`
- `202 {"status": "awaiting_approval", "code": "approval_required", "approver": "Funmi", "say": "..."}`
- `202 {"status": "processing", "code": "processing", "reference", "say"}`
- or a problem from the table above.

After `chose_myself` the client confirms again with the same hash and a fresh PIN block.
While `awaiting_approval`, the client polls `GET /v1/drafts/{id}` every 2 s until the status changes.

### Co-approvers

| Call | Who | |
|---|---|---|
| `GET /v1/approvers` | customer | `[{"id", "name", "threshold_kobo"}]` |
| `POST /v1/approvers` `{"name", "phone", "threshold_kobo", "pin_block"}` | customer | `201`; replaces the active one. Needs the PIN |
| `DELETE /v1/approvers/{id}` with header `X-Pin-Block: <pin_block>` | customer | `{"removed": id}`. Needs the PIN |
| `GET /v1/approvals` | approver | `[{"id", "status", "customer", "amount_kobo", "amount_text", "to", "bank", "account_last4", "asked_at", "draft_status", "flagged"}]`, newest first, never the balance. `flagged` is true when the customer stopped a payment to this person after a call |
| `POST /v1/approvals/{id}/decision` `{"decision": "approve"\|"decline"}` | approver | `{"approval": "approved"\|"declined", "status", "say", "reference"?, "code"?}` |

Just before an approved transfer moves, the Gateway runs the name enquiry and the limits again. If
either changed, nothing moves: the answer is `{"approval": "approved", "status": "awaiting_confirm"
or "failed", "say", "code": "readback_mismatch" | "bank_unavailable" | "limit_exceeded"}`, and the
customer confirms again (a new readback, a new PIN, and a new approval) if they still want it.
If the customer flagged the payee while the request waited (and had not answered a pause on this
draft), nothing moves either: `{"approval": "approved", "status": "paused", "code": "pause_required", "say"}`,
and the customer answers the pause.
A decision on a request that waited more than 15 minutes is `410 approval_expired`.
Removing or replacing an approver withdraws their pending requests (those drafts are cancelled), and
their token gets `403 forbidden` from then on.

### Preferences (customer)

`GET /v1/me/preferences`, `PUT /v1/me/preferences` (partial):
`{"lang", "modes": ["voice"|"sign"|"switch"|"plain"], "theme": "system"|"light"|"dark"|"contrast",
"speech_rate": 0.5-2.0, "pulse_gap_ms": 400-3000, "daily_limit_kobo", "pause_threshold_kobo"}`.
Both answer with `"first_name"` too, read-only: the name the app greets the customer by.
Changing `lang` changes the language of every later `say`.
A change that weakens a protection needs `"pin_block"` in the same body: raising `pause_threshold_kobo`
(or clearing it when it is below the ₦50,000 default), and raising or clearing `daily_limit_kobo`.
Without it: `401 pin_required`; with a wrong one: `401 pin_incorrect`. Lowering a limit, and every
other field, needs no PIN.

### Assistant (customer)

| Call | Body | Response |
|---|---|---|
| `POST /v1/conversations` | | `201 {"id": "cv_..."}` |
| `POST /v1/conversations/{id}/turns` | `{"text": "send twenty thousand to Tolu"}` | `{"heard", "reply", "say", "engine": "claude"\|"rules", "draft": draft\|null}` |
| `POST /v1/conversations/{id}/voice-turns` | raw `audio/wav` body | as above plus `"reply_audio_url"` (nullable) and `"timings"` |

`heard` is the text with numbers masked: `ACCOUNT_1` (10 digits), `[BVN]` (11), `[CARD]` (12 to 19),
`[PIN]`. Numbers split by spaces, dots, commas, slashes or dashes into chunks of any size ("01234 50213",
"012345 0213"), and numbers glued to letters, are masked too; amounts with thousands commas ("1,500,000")
are not. An `ACCOUNT_n` label keeps its number for the whole conversation (held in memory on the
Gateway, never stored). A PIN is caught in either order ("my PIN is 2075", "2075 is my PIN"), written
"2,075", "PIN2075" or "P.I.N.", said "twenty seventy five", and in Pidgin, Yorùbá, Hausa and Igbo
phrasings. An amount near the word ("send Tolu 2000, I will pulse my pin") is not a PIN.
`engine` is `rules` for a turn stopped by the PIN guard and for a voice turn where nothing was heard. A turn that contains a spoken PIN
("my PIN is 2075") is caught in code before any model call: no draft, and `say` tells the customer
never to say it aloud. "Send money to Tolu" then "twenty thousand" works across two turns, and so does "send money to
0123456789 at GTBank" then "twenty thousand".
The rule parser understands sending, the balance, money in and blocking the card in all five languages,
with or without tone marks, and amounts in their words or digits: "fi ẹgbẹ̀rún ogún ránṣẹ́ sí Tolu",
"aika dubu ashirin zuwa ga Musa", "ziga puku iri abụọ nye Emeka", "Elo lo wa ninu account mi".

### Speech (YarnGPT behind the Gateway; the phone never holds the key)

| Call | Who | Response |
|---|---|---|
| `GET /v1/speech/status` | any token | `{"enabled": bool, "provider": "yarngpt"\|null}` |
| `POST /v1/speech/say` `{"text", "lang"?}` | customer | `{"audio_url", "expires_in": 300, "lang", "voice"}` (`voice` may be null: YarnGPT's default) |
| `POST /v1/speech/listen` raw `audio/wav` | customer | `{"text", "seconds", "say"}`, or `{"text": "", "pin_spoken": true, "seconds", "say"}` |

Recordings: 1 KB to 4 MB, any `audio/*` type. `say` is limited to 90 a minute and listening
(including voice turns) to 30 a minute per customer.

### Audit

`GET /v1/audit?limit=50` (customer: own entries, newest first) →
`[{"seq", "at", "actor", "action", "draft_id", "detail", "entry_hash"}]`.
`GET /v1/audit/verify` (any token) → `{"ok": true, "entries": n}` or `{"ok": false, "broken_at": seq, "entries": n}`.

### Mock admin (rehearsals only; off when `MOCK_ADMIN=false`; needs `X-Mock-Admin` when `MOCK_ADMIN_TOKEN` is set)

| Call | |
|---|---|
| `GET /_mock/state` | `{"controls", "balances": [{"account_id", "last4", "balance_kobo"}], "cards": [{"id", "last4", "status"}], "pin_tries_left": {ref: n}}` |
| `POST /_mock/reset` | back to the demo world, and the controls back to `ZENITH_PROFILE`; answers as `state` |
| `POST /_mock/controls` `{"profile": "realistic"\|"fast"\|"flaky", "banks_down": ["999992"], "next_transfer_code": "09"}` | steer the mock core; answers as `state` |

Optional control fields: `next_transfer_timeout` (`"after_send"`: the next transfer goes through but
its answer is lost; `"before_send"`: it never arrives), `settle_after_s` (how long a `09` transfer
stays in progress), `pending_rate` and `failure_rate` (0 to 1). A `profile` brings its own defaults
first. `null` clears `next_transfer_code` and `next_transfer_timeout`.

## The demo world

| | |
|---|---|
| Customer | Ronke Adeyemi, account `ac_1` (2081234567), balance ₦842,300, BVN 22212345678, Pulse PIN **2 0 7 5** |
| Payees | `py_tolu` Tolu: Adebayo Tolulope (Zenith Bank 057, 2044714471), `py_mama` Mama: Adeyemi Folake (GTBank 058, 0123450213), `py_musa` Musa: Musa Ibrahim (OPay 999992, 8031447710, **new**, never paid) |
| Co-approver | Funmi, for transfers above ₦50,000; 15 minutes to answer |
| Card | `cd_1`, ending 8830 |
| Limits | ₦1,000,000 per transfer, ₦5,000,000 a day |

The saved people are customers too, so money sent to them lands. Each has ₦250,000, Pulse PIN
**2 0 7 5**, Ronke's limits, a card, and Ronke saved as payee `py_ronke_<nickname>` ("Ronke", paid
before); no co-approver. Name enquiry gives the names below.

| Person | Customer | Token | `lang` | Bank, account | Name |
|---|---|---|---|---|---|
| Tolu | `cus_tolu` | `demo-tolu` | `yo` | Zenith Bank 057, 2044714471 | Adebayo Tolulope |
| Mama | `cus_mama` | `demo-mama` | `yo` | GTBank 058, 0123450213 | Adeyemi Folake |
| Musa | `cus_musa` | `demo-musa` | `ha` | OPay 999992, 8031447710 | Musa Ibrahim |
| Aisha | `cus_aisha` | `demo-aisha` | `ha` | UBA 033, 2150987654 | Bello Aisha |
| Emeka | `cus_emeka` | `demo-emeka` | `ig` | Zenith Bank 057, 2234567890 | Nwosu Emeka |
| Chika | `cus_chika` | `demo-chika` | `ig` | Access Bank 044, 0691234567 | Okonkwo Chika |

Mock account rules: ten identical digits → invalid (07); ending `0006`, or UBA `2109876543` → dormant (06);
BVN `12345678901` belongs to someone else; any other 10-digit number at a listed bank → a consistent
generated name.

Each sign-up is a customer of the mock bank too: a Zenith (057) account number, ₦100,000 and a card,
named from the card the phone read. Name enquiry on an account the mock bank holds gives its owner's
name, and a transfer between two such accounts, at any bank, debits one and credits the other in the
same database transaction, with an incoming line on the receiver's side. Transfers to any other account
only leave.

## Demo scripts the two halves must agree on

1. "Send twenty thousand to Tolu" → readback → PIN 2075 → `200 executed`, "Sent. Tolu has it."
2. "Send money to Tolu", then "twenty thousand" → same draft as 1.
3. ₦150,000 to Musa with `call_active: true` → `409 pause_required` → `chose_myself` → confirm again
   → `202 approval_required` (Funmi) → Funmi approves on `/approve` → poll shows `executed`.
4. "Block my card" → card_block draft → PIN → `200 executed`, "Card blocked. A new one is on its way."
5. Wrong PIN three times → `tries_left` 2, 1, 0 (locked).
