# TANY Collect Android — operational UX rework

This rework changes **information architecture, hierarchy and timing**. It keeps the TANY design system as is: tokens,
typography, components, Light / Dark / System, motion and loading.

Every screen state below comes from the server:
- `phase`;
- pickup / return / deposit timestamps;
- `payment.status`;
- `deposit.merchantAction`;
- the Today counters.

The backend still validates every gesture.

## 1. Home (« Aujourd'hui »)

Order:
1. **Header**: date, the point's name as the large title, and the opening state chip (structured `openingState`). Two
   quiet 48 dp round actions sit on the right: Scanner (tonal accent) and notifications (only when the module is ON).
2. **Hero card**: the next meaningful operation, chosen by `pickHero`. Its time or window is the dominant line, above a
   112 dp product image, the full product name, the customer / reference, the unit code, and the cash only when it is
   relevant. The CTA is a real action only when the server phase makes the operation workable now.
3. **Summary**: one quiet strip of the four server counters. Zeros are muted, and a late return is the only red. Tapping
   a counter scrolls to its section. When everything is zero, the strip collapses to one line: « Rien en attente pour le
   moment. »
4. **Sections**:
   - « À traiter maintenant » (late returns + TANY reviews);
   - « À collecter aujourd'hui »;
   - « À retourner aujourd'hui »;
   - « En attente du client »;
   - no-shows.

   The hero is never repeated, and empty groups are hidden.
5. « Terminées aujourd'hui » (folded) and « Mis à jour à ».

**Hero priority** (`heroTier`) uses the server phase only:
1. late return;
2. operation in progress;
3. operation open now (`pickup_ready`, `return_due`, `deposit_to_refund`);
4. upcoming (`pickup_upcoming`, `with_customer`);
5. waiting for the customer.

Within a tier, the earliest server time wins. TANY reviews and no-shows are never the hero. This orders server data;
it never decides what the merchant may do.

**Cards**: time first (large), then type + server status chip, an 80 dp image, the full product name (wraps, never
« Coll… »), the unit code, customer · reference, the multi-day period, cash only in pickup / deposit phases, and one
exception or countdown line.

**Empty day**: one calm card (« Aucune opération prévue aujourd'hui ») and shortcuts. The Today endpoint only returns
today's operations, so the next operation on another day is not shown (it would need a new endpoint).

## 2. Booking screen: three experiences

`BookingStage` maps the server phase. `detailMode` then picks the experience.

| Stage | Server phases | Screen |
|---|---|---|
| PICKUP_PLANNED | `pickup_upcoming` | **Preparation**: « Collecte prévue », the window, the object, the customer and the amount to collect. « La collecte pourra commencer à 09:30 ». No execution control. |
| PICKUP_READY | `pickup_ready` | Overview + « Commencer la collecte » → guided flow |
| PICKUP_ACTIVE | `pickup_in_progress`, `pickup_awaiting_customer` | Guided flow opens directly |
| RENTAL_ACTIVE | `with_customer` | Status, object, expected return, deposit held. « Commencer le retour » is secondary (early return). |
| RETURN_EXPECTED | `return_due`, `return_late` | « Commencer le retour ». Late = an information banner, **never a blocker**. |
| RETURN_ACTIVE | `return_in_progress`, `return_awaiting_customer`, `deposit_to_refund`, `deposit_awaiting_customer` | Guided return opens directly |
| ATTENTION | `deposit_disputed`, `blocked_pending_review` | TANY intervention card, no action |
| CLOSED | `completed`, `cancelled`, `no_show` | Read-only summary (« Réservation annulée par … ») |

Overview order: **status card** (stage, large time, object, the single action) → customer (light, filled card) →
**money** (one primary amount + breakdown) → **planning** (period, pickup, return) → photos → incidents → **history**,
folded by default (« Historique · n événements »).

## 3. Guided pickup

There is one active step at a time, with a progress bar and « Étape n sur 6 » in the top bar. The current step is the
first one whose **server fact** is missing. Their order is the order the backend enforces (`pickup-return.ts`,
`booking-photos.ts`):

1. **Vérifier le client** (`pickup.clientVerifiedAt`): customer card (verified photo when the server sends it,
   •••• last 4, identity verified) and « Scanner le QR du client ». The scanner keeps the 6-digit fallback.
2. **Vérifier l'objet** (`assetVerifiedAt`): large image, unit code, « Scanner l'étiquette de l'objet ».
3. **État et photo** (`photoCount`): condition first. « Signaler un problème » progressively reveals the TANY incident
   sheet. Then the capture, a preview, « Reprendre une photo » and « Continuer ». « Continuer » is a UI acknowledgement
   only; the server still requires the photo.
4. **Encaisser** (`payment.status`): « À encaisser 467 DH », with the location / caution / espèces breakdown below.
   « Confirmer 467 DH encaissés » opens the financial confirmation sheet.
5. **Remettre l'objet** (`merchantConfirmedAt`): object + customer, « Objet remis » with a confirmation sheet. It sends
   the condition recorded by the server.
6. **Confirmation client** (`customerConfirmedAt`): calm waiting state, a 3 s re-read and « Relancer le client »
   (server delay).

When the customer confirmation arrives, the screen shows a short animated **« Collecte terminée »** with the object,
the unit and the expected return, then « Terminer » → Today. Steps slide between each other, and a haptic fires each
time the server moves the flow forward.

## 4. Guided return

The return uses the same principle:
1. customer QR;
2. object label;
3. condition + photo;
4. statement (missing accessories / incident, « Confirmer la réception »);
5. customer confirmation;
6. deposit hand-back (deferred QR when required, « Remettre X »). This step appears only when the server expects it.

A late return shows the information banner and keeps exactly the same steps. The statement sends the condition the
server recorded with the return photos.

## 5. Navigation decision

**Previous model**: 5 equal tabs (Aujourd'hui, Scanner, Activité, Matériel, Compte). Scanner was a tab with a pink pill,
and every selected tab added an M3 indicator pill. That made the bar heavy, and Scanner competed with Aujourd'hui.

Options considered:
- **A**: 5-tab NavigationBar with a lighter selection. Rejected: Scanner remains a « place » the merchant lands on and
  must leave. It loses state, and it takes the same weight as content destinations used much less often.
- **B**: 4 persistent destinations + Scanner as a separate action. **Chosen.**

**Chosen model (B)**:
- Persistent destinations: Aujourd'hui, Activité, Matériel (only when the server module is ON), Compte.
- Calm selection: ink icon + semibold label, no indicator pill, M3 `selected` semantics for TalkBack.
- **Scanner = action** in the centre of the bar (after Aujourd'hui and Activité, under the thumb). It is a filled
  56 × 40 dp accent button with a label, not a selectable tab.
- The action opens the **full-screen scanner** (bar hidden). After a successful scan, the operation opens directly and
  the scanner leaves the back stack, so Back from the operation returns to where the merchant was.
- The scanner is also reachable from the Home header, the guided flow steps (scan the customer / label / deposit QR)
  and the `tanycollect://scan` deep link.

A floating action button was rejected. It would overlap list content on every tab and duplicate the header action.

## 6. Accessibility, localisation, themes

- **Accessibility**: 48 dp minimum on all actions; the summary counters are announced as « 2 À collecter »; step
  progress has a state description; the history row announces « déplié / replié ». Waiting and success states are
  polite live regions. The status chips always carry text.
- **Localisation**: all new copy is in FR (default), EN and AR (MSA, TANY glossary). TNY references, amounts, unit
  codes, times and phone digits are `ltrIsolated`. The slide transitions are mirrored by the layout direction.
- **Themes**: only `TanyTheme` tokens are used. Product images stay on the white studio surface in both themes.
