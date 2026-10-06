# TANY Collect — iOS ↔ Android parity

The iOS app (`tany-collect-ios`) is the **product / UX reference** for this matrix: information hierarchy, flows,
states and microcopy. It is **never a source of business rules**. `HADevTime/tany-backend` remains the only business
authority, so Android renders server fields (`phase`, `actions`, `reasonCode`, `depositAction`, counters, flags…).

Android keeps its own design system (`TanyTheme`, Material 3, skeletons, motion, Light/Dark/System). Nothing is
pixel-copied.

Legend:
- ✅ at parity.
- ≈ same intent, different native treatment.
- ⛔ intentionally not copied (a client-side rule on iOS).
- 🕓 post-MVP or needs a backend endpoint / real fixture.

## Matrix

| Feature | iOS | Android | Parity | Notes |
|---|---|---|---|---|
| **Branding** — launcher icon | `AppIconCollect-1024` | Same artwork as an adaptive icon (foreground + `#23262B` background + themed monochrome) | ✅ | Same icon for all variants, as on iOS |
| **Navigation** — tabs | Today / Scanner (centre, accent) / Activity / Account | Today / Activity / **Scanner action** (centre) / Equipment* / Account | ≈ | Scanner is an action opening a full-screen scanner, not a tab (docs/UX_REWORK.md § 5). *Equipment is a tab when the module flag is ON (iOS: inside Account) |
| Navigation — Today badge | late + blocked | `counts.late + counts.blocked` (server counters), plural spoken label | ✅ | |
| Navigation — point switch resets navigation | `selectPoint` + `resetNavigation` | `key(activePointId)` recreates the whole shell (back stacks, ViewModels) | ✅ | No data of the previous point survives |
| **Today** — header | wordmark, bell, pink Scanner, point name, status, "n terminées" | TANY COLLECT brand line, bell, Scanner pill (tonal), point name (display), structured opening chip, completed today | ≈ | Page-coloured header instead of the graphite band; tonal Scanner so the hero CTA keeps the pink focus |
| Today — KPI tiles | 2 × 2 `KPITile` (icon, number, label, chevron), tap scrolls | 2 × 2 stat cards: semantic icon, large number, label, chevron when > 0; zeros quiet; late outlined danger; tap scrolls to the hero or section | ✅ | |
| Today — sections | late, attention, to collect, to return, awaiting customer, no-show | Hero + « À traiter maintenant » (late + TANY review), to collect / to return today, awaiting customer, no-show; hero never repeated, empty groups hidden | ✅ | |
| Today — row anatomy | time · kind, short status, product, multi-day period, ref + amount, countdown | Time first (large), status, 80 dp image, full product name, unit code, customer · ref, cash only when relevant, exception line | ✅ | |
| Today — refresh | poll 30 s + foreground | silent refresh on resume + every 30 s, pull-to-refresh, "Mis à jour à", stale/offline notice | ✅ | |
| Today — "next operation" hero | `NextOperationCard` (graphite, eyebrow + kind badge, 40 pt time + ref, product, customer · asset, pink « Ouvrir ») | Same structure on the dark `chrome` surface in both themes, window below the time, contextual pink CTA (Préparer / Commencer la collecte / Continuer / …), placed after the stats as on iOS | ✅ | Hero chosen from server phase tiers (never a client business rule) |
| Today — transition toasts | `detectTransitions` | — | ⛔ | Client inference; push/inbox come from the server |
| Today — local reminders | `CollectNotifications.sync` | — | ⛔ | Notification content is never computed locally |
| Today — offline disk cache | yes | memory only | 🕓 | |
| **Scanner** — camera + 6-digit fallback | yes | CameraX + ML Kit, manual code always available, torch, haptics | ✅ | |
| Scanner — one code per arming | yes | `ScanGate` (re-arm edge + same-code cooldown) | ✅ | Unit tested |
| Scanner — refusal card | title, message, Scan again, type code | Same, typed from `ApiErrorCode` / reason, never the FR message | ✅ | |
| Scanner — wrong item screen | full-screen danger | Full-screen danger container, Expected / Scanned boxes | ✅ | |
| Scanner — camera unavailable | fallback | `CameraUnavailablePanel` → manual input | ✅ | |
| Scanner — QR payload | opaque | Opaque (trim only), never echoed | ✅ | |
| Scanner — asset label lookup | Account › Equipment | Equipment › scan (`assets/lookup?code=`) | ✅ | |
| **Pickup** — guided flow | one step at a time | One step at a time: the first missing server fact (QR → label → photo → cash → handover → customer); before the window a preparation view without any control | ✅ | Step order = backend guards |
| Pickup — cash "J'ai reçu X" | financial confirmation | `ConfirmationSheetHost` FINANCIAL, server amount sent back exactly | ✅ | |
| Pickup — photos + condition | yes | Private cache + FileProvider, condition, guide text, zoom; deleted after upload | ✅ | |
| Pickup — handover condition | sends condition | Sends `pickup.condition` recorded by the server | ✅ | **Bug fixed** (was null) |
| Pickup — report a problem | incident sheet | `POST incidents` sheet; handover can continue, TANY decides | ✅ | |
| Pickup — waiting for customer | poll + nudge + overdue | 3 s poll, nudge with the server `retryAfterSeconds`, overdue only from the server flag | ✅ | iOS local 3-min timer ⛔ |
| Pickup — success state | success card | Shown only when `customerConfirmedAt` appears in a server read | ✅ | |
| Pickup — retry photo without retake | yes | retake required | 🕓 | |
| Pickup — sheet stays open on error | yes | sheet closes, error on the card, booking re-read | ≈ | Non-idempotent POST never retried |
| **Return** — late return | banner, never blocks | `lateMinutes` banner, return always possible | ✅ | "Return must never be blocked because it is late" |
| Return — accessories / incident | yes | Missing accessories, incident type + description; issue ⇒ ISSUE_REPORTED | ✅ | |
| Return — countdown 2 h threshold | local threshold | server times only | ⛔ | |
| **Deposit** — hand-back | QR then hand back | Hand-back gated on `refundPickup.qrRequired && verifiedAt == null`; `deposit_amount_changed` ⇒ re-read, never resent | ✅ | |
| Deposit — customer closes | yes | "customer confirms" text from `depositAction` | ✅ | The merchant never finalises |
| Deposit — "asset released" claim | shown | — | ⛔ | Not a server fact |
| **Booking detail** — sections | summary, customer, schedule, photos, history | Same + incidents, step timestamps, event / actor labels | ✅ | Minimal customer data only |
| Booking detail — cancelled / intervention | yes | Cancelled and TANY-intervention cards from server phase | ✅ | No cancellation from Collect |
| Booking detail — `MERCHANT_OPENED_BOOKING` event | reported | — | 🕓 | |
| **Activity** — list | grouped by day | Today / Yesterday / History buckets on `updatedAt ?: scheduledAt` | ✅ | **Bug fixed** (was grouped by scheduledAt, could show "Demain") |
| Activity — search | yes | Server search, list kept while searching | ✅ | |
| Activity — incidents | segment | Operations / Incidents segmented control (`points/{id}/incidents`) | ✅ | iOS `isOpen = resolvedAt == nil` ⛔ |
| **Equipment** — list | filters + counts | Server filters ALL / AVAILABLE / OUT / ATTENTION + counts, server search, scan | ✅ | |
| Equipment — module OFF vs empty | yes | Distinct disabled / empty states | ✅ | |
| Equipment — detail | lifecycle, attention, bookings | Lifecycle card + progress, attention notice, usage, recent bookings, return window | ✅ | **Bug fixed** (lifecycle `percentage` is a Double, date an Instant) |
| **Revenue** — hero | "Gains estimés ce mois" | Same eyebrow, ESTIMATED chip, non-zero breakdown only, agreement line | ✅ | Never "payé / solde" |
| Revenue — periods | dropdown | Prev / next + dropdown over server `periods[]`, figures kept while loading | ✅ | |
| Revenue — empty period | card | Empty-period card | ✅ | |
| Revenue — activity | ± by kind, 8 rows | Sign by `amountKind`, deposits neutral, 8 rows | ✅ | |
| Revenue — bonus | tiers + next | Tiers + server `bonus.next` | ✅ | |
| Revenue — deposits | separate | Separate neutral card, empty buckets hidden | ✅ | Deposits are never revenue |
| Revenue — settlement card | yes | Settlement summary card (status + amount due) | ✅ | |
| Revenue — delta vs previous month | % computed locally | previous amount shown, no computed delta | ⛔ | |
| **Settlement** — QR | QR + countdown | Server QR, countdown from `serverTime` offset, expired overlay, "Afficher un nouveau QR", deposits note | ✅ | |
| Settlement — "J'ai remis X" | confirmation | Auto-opened once per `agentConfirmationId`, shortfall from server amounts, FINANCIAL | ✅ | |
| Settlement — dispute | yes | STANDARD confirmation with the amount, nothing recorded as handed over | ✅ | |
| Settlement — live updates | poll | 4 s poll while in progress / awaiting merchant / QR shown | ✅ | |
| Settlement — statement / receipt detail | screens | — | 🕓 | Needs endpoints + real fixtures |
| **Notifications** — inbox | Today / Yesterday / Older | Same buckets (business day), section counts, "Action requise" / "Traité" chips | ✅ | |
| Notifications — module OFF | hidden | Bell and Account row hidden (`unread-count.enabled`) | ✅ | |
| Notifications — deeplink | yes | `deepLink` or `tanycollect://booking/{id}` fallback | ✅ | |
| Notifications — OS permission / FCM status row | yes | — | 🕓 | Once FCM is wired |
| Notifications — pending deep-link replay after sign-in | yes | lands on Today | 🕓 | |
| **Account** — profile | name, phone, role | Same (phone LTR-isolated) | ✅ | No Trusted, no Saved Places, no admin notes |
| Account — active point | name, address, status, switch | Structured opening chip; switch with confirmation | ✅ | |
| Account — revenue / settlement / notifications | rows | Rows; notifications only when inbox ON | ✅ | |
| Account — preferences | appearance, language | Light / Dark / System, FR / EN / AR | ✅ | |
| Account — help / support / legal | help sheet, support, privacy | Help sheet, `{base}/support`, `{base}/confidentialite` | ✅ | |
| Account — contact TANY (call) | button | support page | ≈ | |
| Account — version | version (build) | version (build) | ✅ | |
| Account — logout | "Se déconnecter de TANY Collect ?" | Same, DESTRUCTIVE confirmation | ✅ | |
| Account — account deletion | — (no merchant API) | — | ✅ | Not available for merchants |
| Account — admin backoffice | never | never | ✅ | ADMIN role ≠ backoffice access |
| **Point picker** | greeting, choose, logout | "Bonjour X", opening chip, logout, no-point state | ✅ | |
| **Auth** — OTP | `codeLength`, auto-submit, resend countdown, change number | Same; 429 `retryAfterSeconds` lock, code cleared on failure | ✅ | |
| Auth — session expired | notice | Notice on the phone screen after a 401 | ✅ | |
| **Live Activities** | yes | — | ⛔ | Non-goal |
| **Offline monitor** | banner | stale notice after a failed refresh | 🕓 | |

## Intentional differences (summary)

1. Equipment stays a bottom tab when the module is ON (Android navigation convention); iOS reaches it from Account.
2. Pickup and return are guided one step at a time like iOS, but the current step is always the first missing SERVER fact (their order is the backend's own guard order).
3. iOS client-side rules are not copied:
   - transition toasts and local reminders;
   - the local 3-minute overdue timer;
   - the 2 h countdown threshold;
   - local incident "open" state and local count fallbacks;
   - the "asset released" claim;
   - the revenue delta vs the previous month.

## Post-MVP / backend asks

- Settlement statement and receipt detail screens: need endpoints and real fixtures.
- Disk cache and an offline connectivity monitor.
- FCM status row and pending deep-link replay.
- Photo retry without retake.
- `MERCHANT_OPENED_BOOKING` reporting.
- Backend ask: `invalid_state` carries no `reason`, so the scanner can only say "not possible now".
