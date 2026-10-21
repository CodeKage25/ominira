# Òmìnira API contract

The one agreement between the Android app (`android/`) and the Gateway (`gateway/`). If the code and
this file disagree, fix one of them in the same change.

- JSON over HTTPS. Base path `/v1`. Times are ISO 8601 UTC with a trailing `Z`.
- Money is **kobo as integers** everywhere on the wire (`amount_kobo`, `balance_kobo`). ₦1 = 100 kobo.
- Auth: `Authorization: Bearer <token>`. Hackathon tokens stand in for device-bound passkeys.
- Every state-changing call may send `Idempotency-Key: <uuid>`. The Gateway stores the response for
  24 h per token and replays it for the same key and body; the same key with a different body is
  `422 idempotency_key_reused`.
- Anything a customer hears carries `say`: one line, already in the customer's language.
- Languages: `en`, `pcm` (Pidgin), `yo`, `ha`, `ig`.

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
| 403 | `forbidden` | | wrong role for this call |
| 404 | `not_found`, `payee_not_found`, `card_not_found` | | |
| 409 | `pause_required` | `draft_id` | scam pause; answer with pause-answer |
| 409 | `readback_mismatch` | `readback`, `readback_hash` | read the new readback to the customer, then confirm with the new hash |
| 409 | `already_executed` | `reference` | |
| 409 | `processing` | | transfer already on its way |
| 409 | `draft_closed` | `status_now` | cancelled or failed |
| 409 | `already_blocked` | | card already blocked |
| 409 | `not_paused`, `already_decided`, `onboarding_incomplete` | | |
| 410 | `draft_expired` | `draft_id` | older than 10 minutes |
| 410 | `approval_expired` | `draft_id` | approver did not answer within 15 minutes |
| 413 / 415 / 422 | `audio_too_long` / `audio_expected` / `audio_too_short` | | speech uploads |
| 422 | `invalid_request` | `errors` | validation |
| 422 | `invalid_amount`, `invalid_account`, `account_dormant`, `unknown_bank` | | |
| 422 | `limit_exceeded` | `limit` (`per_transaction` or `daily`), `max_kobo` or `remaining_today_kobo` | |
| 422 | `insufficient_funds`, `failed` | `zenith_code` on `failed` | definite "not sent" |
| 429 | `rate_limited` | `retry_after` | |
| 503 | `bank_unavailable` | `retry_after`, `zenith_code` | definite "not sent"; the draft stays open and can be confirmed again |
| 503 | `speech_unavailable` | `provider_code` | use the phone's own voice |

## Drafts and the confirm gate

Every money action is a draft first. Status machine:

```
awaiting_confirm ──confirm──> paused ──pause-answer stop──> cancelled
       │                        └──pause-answer chose_myself──> awaiting_confirm
       ├──confirm (PIN ok, over approver threshold)──> awaiting_approval ──approve──> executed
       │                                                       ├──decline──> cancelled
       │                                                       └──15 min──> expired
       ├──confirm──> executed | processing (in progress at Zenith; GET re-checks by reference)
       ├──confirm, definite not-sent (NIP 91/96)──> awaiting_confirm (attempt + 1)
       ├──confirm, refused──> failed
       ├──cancel──> cancelled
       └──10 min──> expired
```

`POST /v1/drafts/{id}/confirm` runs these checks in order. The first that fails decides the answer.

1. **Real draft**: this customer's, open, not expired.
2. **Heard it**: name enquiry again, rebuild the readback; its hash must equal `readback_hash` sent.
3. **Limits**: Zenith per-transaction and daily limits, then the customer's own lower daily limit.
4. **Scam pause** (transfers): (new payee AND amount ≥ pause threshold (₦50,000) AND `call_active`)
   OR payee flagged, and the customer has not already answered a pause on this draft.
5. **Pulse PIN**: the PIN block goes to Zenith; the Gateway learns only ok or tries left. Never stored or logged.
6. **Co-approver** (transfers): amount above the active approver's threshold → `awaiting_approval`.
   The PIN is checked before the approver is asked, so an approved draft executes without a second PIN.
7. **Execute** with Zenith idempotency key `{draft_id}:{attempt}`. Timeout or "in progress" keeps the
   key and is re-queried by reference, so a retry can never pay twice.
8. **Audit** (hash-chained) and reply with `say`.

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
`"Zenith Bank"`, `amount_kobo` is null. `source` is `app`, `assistant`, `voice` or `ussd`.
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
| `POST /v1/onboarding/sessions` | | `201 {"id": "ob_...", "liveness_digits": [4, 1, 9, 0]}` |
| `POST /v1/onboarding/{id}/selfie` | `{"spoken_digits": [4,1,9,0]}` (optional) | `{"live": true}` |
| `POST /v1/onboarding/{id}/id-document` | | `{"full_name": "Ronke Adeyemi", "nin_last4": "5521", "born_on": "1961-03-14"}` |
| `POST /v1/onboarding/{id}/bvn` | `{"bvn": "22212345678"}` | `{"match": true, "say": "..."}` (`match` false for `12345678901`) |
| `POST /v1/onboarding/{id}/complete` | | `{"customer_id": "cus_ronke", "token": "demo-customer"}` or `409 onboarding_incomplete` |

### Sign in (customer)

`POST /v1/auth/unlock` `{"pin_block": "..."}` → `{"ok": true, "say": "Welcome back."}` or `401 pin_incorrect`.

**Placeholder PIN block**: `"demo-"` + each digit replaced by `9 - digit`. PIN 2 0 7 5 → `"demo-7924"`.
The pilot replaces it with an ISO 9564 block encrypted on the device with Zenith's PIN key.

### Money (customer or assistant)

| Call | Response |
|---|---|
| `GET /v1/accounts` | `[{"id": "ac_1", "name": "Current account", "last4": "4567", "balance_kobo": 84230000}]` |
| `GET /v1/accounts/{id}/balance` | `{"balance_kobo": 84230000, "say": "You have eight hundred and forty-two thousand, three hundred naira."}` |
| `GET /v1/accounts/{id}/transactions?limit=20&direction=in\|out` | `[{"id", "who", "amount_kobo", "incoming", "at", "reference"}]`, newest first |
| `GET /v1/accounts/{id}/income-summary` | `{"say": "This week ₦... came in, from ..."}` |
| `GET /v1/banks` | `[{"code": "057", "name": "Zenith Bank Plc", "short": "Zenith Bank"}]` |
| `GET /v1/payees` | `[{"id", "nickname", "name", "bank", "account_last4", "is_new", "flagged"}]` |
| `POST /v1/payees/lookup` `{"bank_code", "account_number"}` | `{"name", "bank", "account_last4", "say"}`; 5 a minute per customer |
| `GET /v1/cards` | `[{"id": "cd_1", "last4": "8830", "holder": "Ronke Adeyemi", "status": "active"\|"blocked"}]` |

### Drafts

| Call | Who | Body | Response |
|---|---|---|---|
| `POST /v1/drafts` | customer, assistant | `{"kind": "transfer", "amount_kobo", "payee_id"}` or `{"kind": "transfer", "amount_kobo", "bank_code", "account_number"}` or `{"kind": "card_block", "card_id"}`; optional `"source"` | `201` draft |
| `GET /v1/drafts/{id}` | customer | | draft (re-checks `processing`, expires stale approvals) |
| `POST /v1/drafts/{id}/confirm` | customer | `{"readback_hash", "pin_block", "call_active": false}` | see below |
| `POST /v1/drafts/{id}/pause-answer` | customer | `{"answer": "stop"\|"chose_myself"}` | `{"status": "cancelled"\|"awaiting_confirm", "say"}` |
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
| `POST /v1/approvers` `{"name", "phone", "threshold_kobo"}` | customer | `201`; replaces the active one |
| `DELETE /v1/approvers/{id}` | customer | `{"removed": id}` |
| `GET /v1/approvals` | approver | `[{"id", "status", "customer", "amount_kobo", "amount_text", "to", "bank", "account_last4", "asked_at", "draft_status"}]`, newest first, never the balance |
| `POST /v1/approvals/{id}/decision` `{"decision": "approve"\|"decline"}` | approver | `{"approval": "approved"\|"declined", "status", "say", "reference"?, "code"?}` |

### Preferences (customer)

`GET /v1/me/preferences`, `PUT /v1/me/preferences` (partial):
`{"lang", "modes": ["voice"|"sign"|"switch"|"plain"], "theme": "system"|"light"|"dark"|"contrast",
"speech_rate": 0.5-2.0, "pulse_gap_ms": 400-3000, "daily_limit_kobo", "pause_threshold_kobo"}`.
Changing `lang` changes the language of every later `say`.

### Assistant (customer)

| Call | Body | Response |
|---|---|---|
| `POST /v1/conversations` | | `201 {"id": "cv_..."}` |
| `POST /v1/conversations/{id}/turns` | `{"text": "send twenty thousand to Tolu"}` | `{"heard", "reply", "say", "engine": "claude"\|"rules", "draft": draft\|null}` |
| `POST /v1/conversations/{id}/voice-turns` | raw `audio/wav` body | as above plus `"reply_audio_url"` (nullable) and `"timings"` |

`heard` is the text with account numbers and BVNs masked. A turn that contains a spoken PIN
("my PIN is 2075") is caught in code before any model call: no draft, and `say` tells the customer
never to say it aloud. "Send money to Tolu" then "twenty thousand" works across two turns.

### Speech (YarnGPT behind the Gateway; the phone never holds the key)

| Call | Response |
|---|---|
| `GET /v1/speech/status` | `{"enabled": bool, "provider": "yarngpt"\|null}` |
| `POST /v1/speech/say` `{"text", "lang"?}` | `{"audio_url", "expires_in": 300, "lang", "voice"}` |
| `POST /v1/speech/listen` raw `audio/wav` | `{"text", "seconds", "say"}`, or `{"text": "", "pin_spoken": true, "say"}` |

### Audit

`GET /v1/audit` (customer: own entries), `GET /v1/audit/verify` → `{"ok": true, "entries": n}` or
`{"ok": false, "broken_at": seq}`.

### Mock admin (rehearsals only; off when `MOCK_ADMIN=false`; needs `X-Mock-Admin` when `MOCK_ADMIN_TOKEN` is set)

| Call | |
|---|---|
| `GET /_mock/state` | balances, cards, controls |
| `POST /_mock/reset` | back to the demo world |
| `POST /_mock/controls` `{"profile": "realistic"\|"fast"\|"flaky", "banks_down": ["999992"], "next_transfer_code": "09"}` | steer the mock core |

## The demo world

| | |
|---|---|
| Customer | Ronke Adeyemi, account `ac_1` (2081234567), balance ₦842,300, BVN 22212345678, Pulse PIN **2 0 7 5** |
| Payees | `py_tolu` Tolu: Adebayo Tolulope (Zenith Bank 057, 2044714471), `py_mama` Mama: Adeyemi Folake (GTBank 058, 0123450213), `py_musa` Musa: Musa Ibrahim (OPay 999992, 8031447710, **new**, never paid) |
| Co-approver | Funmi, for transfers above ₦50,000; 15 minutes to answer |
| Card | `cd_1`, ending 8830 |
| Limits | ₦1,000,000 per transfer, ₦5,000,000 a day |

Mock account rules: ten identical digits → invalid (07); ending `0006`, or UBA `2109876543` → dormant (06);
BVN `12345678901` belongs to someone else; any other 10-digit number at a listed bank → a consistent
generated name.

## Demo scripts the two halves must agree on

1. "Send twenty thousand to Tolu" → readback → PIN 2075 → `200 executed`, "Sent. Tolu has it."
2. "Send money to Tolu", then "twenty thousand" → same draft as 1.
3. ₦150,000 to Musa with `call_active: true` → `409 pause_required` → `chose_myself` → confirm again
   → `202 approval_required` (Funmi) → Funmi approves on `/approve` → poll shows `executed`.
4. "Block my card" → card_block draft → PIN → `200 executed`, "Card blocked. A new one is on its way."
5. Wrong PIN three times → `tries_left` 2, 1, 0 (locked).
