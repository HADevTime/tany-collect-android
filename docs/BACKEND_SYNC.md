# Backend contract sync

| Date | tany-backend `main` | Contract sections read | Android changes |
|---|---|---|---|
| 2026-10-04 | `c641fa4` | API_CONTRACT_V1 § 1–5, MVP_SCOPE_V1, LOCALIZATION | Foundation |
| 2026-10-04 | `871d74f` (pre-prod hardening) | API_CONTRACT_V1 § 6, API.md, SECURITY.md, ACCOUNT_DELETION.md; MVP_SCOPE_V1 unchanged | FCM device registration (`/collect/devices`, `platform:"android"`), `deposit-refund {expectedAmount}`, `qr_rate_limited` / `deposit_amount_changed`, COLLECT_APP role policy (MERCHANT / ADMIN only, never backoffice), OTP `codeLength` 6 |
| 2026-10-09 | `2d535c0` (rental kit V1 + editable bag) | RENTAL-KIT.md, API_CONTRACT_V1 § 11, API.md | Rental kit: `booking.kit` frozen snapshot (overview card), handover « Kit à remettre » (`handover` `kit.checks`), return « Kit rendu » Présent / Manquant / Endommagé (`return` `kit.checks`, replaces `missingAccessories`) — differences only, never blocking, no amount |

Verified against the real backend at `871d74f` (local `scripts/mobile-e2e-server.ts`, flags ON):
- `/collect/devices` accepts `platform:"android"` + FCM token; a client token is refused by Collect and a merchant token by the client API.
- ADMIN OTP request returns `devCode: null`.

Resolved gaps (API_CONTRACT_V1 § 5): **A-1** Android push (backend side).
Still open: **A-4** settlement sub-shapes (typed in the settlement slice).

Verified against the real backend at `2d535c0` (route handlers called from a tsx capture script, PGlite, `TANY_RENTAL_KIT_ENABLED`
default ON; fixtures `fixtures/real/booking_detail_kit_pickup|return|returned`):
- `kit` is the frozen snapshot (bag last); an element noted MISSING at the handover has `handedOver:false` and is never sent at
  the return (400 `invalid_request`); a return difference becomes TANY's incident at the customer's confirmation.
