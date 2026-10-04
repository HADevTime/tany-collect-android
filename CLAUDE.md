# TANY Collect Android — engineering rules

Native Android app for TANY Collect merchants. Read `README.md` first (architecture, variants, commands, QR).

## Business authority
- `HADevTime/tany-backend` is the ONLY business authority. Contract: `docs/API_CONTRACT_V1.md`; scope:
  `docs/MVP_SCOPE_V1.md`; localization: `docs/LOCALIZATION.md`. Backend code wins over docs when they differ.
- NEVER derive a business rule client-side: availability, dayCount, prices/totals, cancellation kind, Trusted
  consequences, pickup eligibility, booking restrictions, deposit states, stages, allowed actions. Render the
  server's structured fields (`stage`, `actions`, `reasonCode`, `cancellationPolicy`, `deposit.state`…).
- NEVER read Swift (tany-collect-ios) to infer behavior; iOS is a UX reference only.
- MVP is frozen: no feature outside `MVP_SCOPE_V1.md` without an explicit product decision (`POST_MVP.md`).

## Contract & models (`core/model`)
- Models mirror the contract exactly; never invent fields. New field ⇒ check the backend serializer first.
- Every enum implements `WireEnum` with an `UNKNOWN` fallback; exact wire values; never rename.
- Money: `MoneyAmount` (centimes). Never `Double`/`Float` for money. Confirmed amounts are sent back exactly.
- Dates: instants = `Instant`; civil days = `LocalDate` (Africa/Casablanca); never use the device zone for
  business times. Historical one-day bookings: `effectiveUsagePeriod()`.
- Contract change ⇒ refresh `fixtures/real/*` from the real backend and keep `RealBackendFixturesTest` green.

## Network (`core/network`)
- Errors: `ApiError` with canonical `ApiErrorCode` (no translation in the network layer).
- Never auto-retry a non-idempotent POST (bookings, confirmations, deposit, QR). After a network error, re-read.
- Tokens: only via `SessionStore` (Keystore-encrypted); never log headers/bodies; logging is DEV-only BASIC.
- No Admin authentication in this app; a mobile session NEVER implies backoffice access (backend session contexts).
- Push: FCM via the backend device endpoint (`platform:"android"`); the app only provides the token — never
  compute notification content locally. Do not reimplement backend features (deletion, push, OTP limits) client-side.
- OTP: always read `codeLength` (6 today); handle 429 `otp_too_many_attempts` (+ `retryAfterSeconds`) and 503
  `otp_delivery_failed`.

## UI
- Colors/typography/spacing only from `TanyTheme` — no hex in feature code. Light / Dark / System.
- Product & Asset images: `ProductImageSurface` (white studio in both themes, never tinted).
- Major operations: `ConfirmationSheetHost` (one at a time, double-tap guarded); destructive/financial kinds.
- All user-facing text in `strings.xml` FR (default) + `values-en` + `values-ar` (MSA, glossary). RTL must work
  structurally; identifiers via `ltrIsolated`. Never display the server's French `message`/prose fields when a
  structured code exists.
- Accessibility: touch targets ≥ 48dp, semantic labels, state never conveyed by color alone (chips have text).
- Internal/debug tools live in `app/src/internal` (debug + staging); never reachable in release.

## Environments
- debug = DEV (local backend), staging = https://staging.tany.ma, release = https://tany.ma. Never point PROD at
  staging; never put secrets in BuildConfig; never touch PROD data.

## Workflow
- Branch `claude/<topic>`, small commits, PR, CI green before merge; never push to `main`, never force push.
- Before declaring success: `./gradlew :core:model:test :core:network:test :core:designsystem:testDebugUnitTest
  :app:testDebugUnitTest :app:testStagingUnitTest :app:testReleaseUnitTest :app:lintDebug :app:assembleStaging :app:assembleRelease`
  (CI runs exactly this).

## Merchant-specific rules
- The merchant NEVER finalizes what the customer must confirm (pickup, return, deposit refund) and never takes a
  financial decision; no cancellation from Collect. Render `phase` / `depositAction`; never infer the next step.
- Customer data is minimal by contract. TANY Trusted is deliberately absent from the models — never add it.
- Every booking call carries the active `collectPointId`; never cache operations across points.
- QR / label payloads are opaque: never parse, validate or transform them client-side (trim only).
- Camera permission: in context, with rationale; the 6-digit fallback must always remain available.
- Operation photos: private cache + FileProvider only; deleted after upload and on sign-out.
- Revenue = ESTIMATED earnings (never "paid / settled / balance"); deposits are not revenue; settlement is
  separate. Untyped sub-objects (`JsonElement`) are typed only from real backend output in their slice.
