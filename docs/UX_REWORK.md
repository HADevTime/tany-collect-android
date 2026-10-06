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

## 1. Home (« Aujourd'hui ») — aligned on TANY Collect iOS

Order, matching the iOS Today: **header → 2 × 2 stat cards → « Prochaine opération » hero → À collecter → À retourner →
urgent / waiting groups → completed today and « Mis à jour à »**.

1. **Header**:
   - the TANY COLLECT brand line, with the notifications bell (only when the inbox module is ON) and the Scanner pill;
   - the date;
   - the point name as the dominant title (display);
   - the structured opening state chip (« Fermé · ouvre à 09:30 »);
   - « n terminées aujourd'hui ».
2. **Stats**: four real cards in a 2 × 2 grid (iOS `KPITile`): À collecter (pickup icon, TANY accent), À retourner,
   En attente client (clock), En retard (alert). Each card has a semantic icon, a large number and a label.
   - A card with a value > 0 gets its tint and a chevron, and a tap scrolls to its group: the hero when it belongs to
     that group, else the section header.
   - At 0 the card stays quiet (muted, not tappable).
   - « En retard » > 0 gets a danger outline and number, without filling the card.
3. **Hero « Prochaine opération »**: dark chrome surface in both themes (the `chrome` token: TANY black in Light, raised
   dark chrome in Dark).
   - First line: eyebrow « PROCHAINE OPÉRATION » with a compact badge (COLLECTE / RETOUR, pink accent; danger when the
     return is late).
   - **The start time at 40 sp is the dominant value**, with the TNY reference beside it and the window
     (« Créneau 09:30–11:30 ») below.
   - Then the **full product name** (wraps, never truncated), multi-day period, customer · unit code, lateness, and a
     quiet cash line (« 467 DH · à encaisser »).
   - **Full-width pink CTA** whose label follows the server phase: Préparer · Commencer la collecte · Continuer ·
     Commencer le retour · Rendre la caution · Voir la location · Ouvrir. A start CTA opens the booking straight on its
     guided flow (`BookingRoute(start = true)`). The booking screen honours it only in stages where the server allows
     an operation.
   - No product image: as on iOS, time / type / product / customer dominate. Images stay on the list cards.
   - No next operation ⇒ a calm card « Aucune opération à venir · Les prochaines collectes et retours apparaîtront
     ici. » The Today endpoint returns today only, so a later-day operation cannot be shown.
4. **Lists**: « À collecter » and « À retourner » are always listed (iOS). They are followed by « À traiter maintenant »
   (late + TANY review), « En attente du client » and no-shows when they have rows.
   - **Duplication decision: option B.** The hero is the next operation; the lists hold the remaining operations. An
     empty list under the hero's own group says « Aucune autre collecte / aucun autre retour pour le moment », so the
     hero is never seen twice.
   - **Cards**: « 09:30 · Collecte » and the server status chip on one line, an 80 dp image, the full product name, unit
     code, customer · TNY reference, cash only in pickup / deposit phases, and one exception line.

**Hero priority** (`heroTier`) uses the server phase only:
1. late return;
2. under way;
3. open now;
4. upcoming;
5. waiting for the customer.

Within a tier, the earliest server time wins. TANY reviews and no-shows are never the hero.

**Pink accent budget on Home**: the hero CTA and its type badge carry the TANY pink. The header Scanner pill is tonal
(soft accent container), and the persistent centre Scanner action of the bar stays filled. This way the hero remains
the strongest surface of the screen.

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
