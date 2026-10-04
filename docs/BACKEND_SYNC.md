# Backend contract sync

| Date | tany-backend `main` | Contract sections read | Android changes |
|---|---|---|---|
| 2026-10-04 | `c641fa4` | API_CONTRACT_V1 § 1–5, MVP_SCOPE_V1, LOCALIZATION | Foundation |
| 2026-10-04 | `871d74f` (pre-prod hardening) | API_CONTRACT_V1 § 6, API.md, SECURITY.md, ACCOUNT_DELETION.md; MVP_SCOPE_V1 unchanged | FCM device registration (`/collect/devices`, `platform:"android"`), `deposit-refund {expectedAmount}`, `qr_rate_limited` / `deposit_amount_changed`, COLLECT_APP role policy (MERCHANT / ADMIN only, never backoffice), OTP `codeLength` 6 |

Verified against the real backend at `871d74f` (local `scripts/mobile-e2e-server.ts`, flags ON):
- `/collect/devices` accepts `platform:"android"` + FCM token; a client token is refused by Collect and a merchant token by the client API.
- ADMIN OTP request returns `devCode: null`.

Resolved gaps (API_CONTRACT_V1 § 5): **A-1** Android push (backend side).
Still open: **A-4** settlement sub-shapes (typed in the settlement slice).
