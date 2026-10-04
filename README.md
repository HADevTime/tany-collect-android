# TANY Collect — Android

Native Android app for TANY Collect merchants (Kotlin · Jetpack Compose · Material 3 + TANY design system):
**operate / verify / hand over / receive**. TANY Client (customers) lives in `HADevTime/tany-android`.
**Status: foundation.** Architecture, networking, merchant auth + active point, design system, localization,
QR/camera and photo foundations, and CI are in place. The pickup, return, deposit, revenue and settlement flows
come next as vertical slices.

## Source of truth

`HADevTime/tany-backend` is the **only business authority**: `docs/API_CONTRACT_V1.md` (frozen contract),
`docs/MVP_SCOPE_V1.md` (scope, § 2 TANY Collect), `docs/LOCALIZATION.md` (FR / EN / AR glossary),
`docs/MANUAL_QA_IOS_MVP.md` § B (expected merchant behaviors). When docs and code differ, the backend
implementation wins (see real-backend fixtures). The iOS app is a UX reference only — never read Swift for rules.

Merchant guarantees enforced by the backend and never bypassed by the app: double confirmation (only the customer
finalizes pickup / return / deposit refund), no merchant financial decision, no merchant cancellation, minimal
customer data (first name, short name, 4 last phone digits) and **never** TANY Trusted.

## Architecture

```
app/                    Hilt DI, shell (auth → point picker → tabs), Keystore session, DataStore prefs, features
  feature/              auth · point · today · scanner · activity · equipment · account · booking (skeletons)
  core/media/           Operation photo capture files (private cache + FileProvider)
  src/internal/         Design-system showcase — debug + staging ONLY
core/model/             Pure JVM — Collect contract models (operations, merchant booking, assets, revenue, settlement)
core/network/           Pure JVM — Retrofit Collect API, interceptors, semantic errors, session, repositories
core/designsystem/      TANY tokens + components (same as TANY Client; candidate for a shared library)
```

Navigation (MVP): **Aujourd'hui · Scanner · Activité · Matériel** (only when `features.assets`) **· Compte**.
Deep links `tanycollect://today|scan|activity|booking/{id}|return/{id}|revenue|revenue/settlement` open screens
that re-read server state (revenue / settlement open Compte until their slices exist).

Every booking request carries the active `collectPointId`; the server re-checks scope (`wrong_collect_point`).
ADMIN accounts with several points pick one (Compte › change point, confirmation sheet); a MERCHANT's single point
is selected automatically. Sign-out clears the active point and any operation photo.

## QR / camera / photos

- **Scanning**: CameraX preview + **ML Kit barcode (bundled model)** — on-device, offline. Payloads are opaque
  (`<id>.<hmac>` customer QR, raw Asset code labels, server-issued `TCR1…` settlement QR): never parsed client-side.
- **Fallback**: 6-digit code, same strength as the QR.
- **Permission**: CAMERA requested in context with a rationale; permanently denied ⇒ settings shortcut; the
  6-digit fallback always works.
- **Photos**: private cache files exposed via FileProvider, JPEG multipart upload in the pickup / return slices.

## Environments & variants

| Variant | Environment | API | Application ID | Name |
|---|---|---|---|---|
| `debug` | DEV | `http://10.0.2.2:3000` (`-Ptany.devApiBaseUrl=…`) | `ma.tany.collect.dev` | Collect dev |
| `staging` | STAGING | `https://staging.tany.ma` | `ma.tany.collect.staging` | Collect β |
| `release` | PROD | `https://tany.ma` | `ma.tany.collect` | TANY Collect |

Validated at configuration time, at runtime and by `EnvironmentConfigTest` for every variant.

## Build & test

```bash
./gradlew :core:model:test :core:network:test
./gradlew :core:designsystem:testDebugUnitTest
./gradlew :app:testDebugUnitTest :app:testStagingUnitTest :app:testReleaseUnitTest
./gradlew :app:lintDebug :core:designsystem:lintDebug
./gradlew :app:assembleStaging :app:assembleRelease
```

Demo merchant (DEV/STAGING, OTP shown on screen outside PROD): `+212600000002` (TANY Collect Maarif).
Contract fixtures: `core/model/src/test/resources/fixtures/real/` — captured from the real backend
(`tany-backend/scripts/mobile-e2e-server.ts`, flags ON, tokens replaced) and decoded by `RealCollectFixturesTest`.

## Deliberate MVP choices

- The design-system foundation is duplicated in `tany-android` and `tany-collect-android` on purpose (no shared
  Android library during the MVP).
- The launcher icon is a placeholder until the official TANY assets are integrated.

## Rules

FR / EN / AR (MSA, full RTL, brand names untranslated, identifiers LTR-isolated) · Africa/Casablanca for every
business time · white studio for product images in both themes · money as integer centimes, exact amounts ·
no automatic retry of any mutation · push via FCM (`POST/DELETE /collect/devices`, `platform:"android"`;
`BackendPushTokenRegistrar`, Firebase token source in the notifications slice).

## Authorization (canonical backend model)

- Collect sessions are `COLLECT_APP` sessions obtained by OTP: valid only for the Collect API — never for TANY
  Client, **never for the backoffice** (`/admin`, `/agent` require a separate `BACKOFFICE` email + password
  session), even for an ADMIN account.
- Roles accepted by `/collect/auth/otp/verify`: **MERCHANT** (attached to an active point) and **ADMIN**
  (multi-point). Customers, collection agents (web `/agent` only), blocked accounts and accounts without an active
  point get `account_not_allowed`. The app mirrors this defensively (no session stored for any other role).
- No OTP demo code (`devCode`) is ever returned for ADMIN / agent numbers, even in STAGING.
- The app never links to, opens or implies backoffice access; ADMIN only gains the point switcher (scope still
  re-checked by the server on every request).
- Collect specifics: 429 `qr_rate_limited` (6-digit fallback, 8 errors / 10 min / point — the QR scan still
  works); `deposit-refund { expectedAmount }` ⇒ 409 `deposit_amount_changed` + `currentAmount` (reload).
Git: never commit on `main`; feature branches, PR, CI green before merge, no force push. CI runs on PRs only.
