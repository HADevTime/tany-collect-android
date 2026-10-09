package ma.tany.core.model.collect

import kotlinx.serialization.Serializable
import ma.tany.core.model.common.AssetCondition
import ma.tany.core.model.common.IncidentType
import ma.tany.core.model.common.MoneyAmount
import ma.tany.core.model.common.QrPurpose

// Every `bookings/{id}/…` route requires `collectPointId` (body or query) — the server re-checks the merchant's scope.
// None of these mutations is idempotent: they are never retried automatically (re-read the booking instead).

/** `POST /collect/scan` — dynamic QR payload OR 6-digit fallback code (same strength). */
@Serializable
data class ScanBody(
    val collectPointId: String,
    val qrPayload: String? = null,
    val shortCode: String? = null,
    val bookingId: String? = null,
    val purpose: QrPurpose? = null,
) {
    init {
        require((qrPayload == null) != (shortCode == null)) { "Exactly one of qrPayload / shortCode" }
    }
}

@Serializable
data class ScanResponse(val purpose: QrPurpose, val booking: MerchantBookingDetail)

/** `POST bookings/{id}/asset` — the scanned Asset label (raw code) must match the assigned Asset (`asset_mismatch`). */
@Serializable
data class AssetScanBody(val collectPointId: String, val purpose: QrPurpose, val assetCode: String)

/** `POST bookings/{id}/payment` — must equal rental total + deposit EXACTLY (server-validated). */
@Serializable
data class PaymentBody(val collectPointId: String, val amountReceived: MoneyAmount)

/** [kit] (rental kit V1, additive): what was handed over — differences only (`checks: []` = everything); null = historical body. */
@Serializable
data class HandoverBody(val collectPointId: String, val condition: AssetCondition? = null, val kit: KitChecks? = null)

@Serializable
data class ReturnIncident(val type: IncidentType, val description: String? = null)

@Serializable
data class ReturnBody(
    val collectPointId: String,
    val condition: AssetCondition,
    @kotlinx.serialization.EncodeDefault val missingAccessories: List<String> = emptyList(),
    val incident: ReturnIncident? = null,
    /**
     * Rental kit V1 (additive): elements returned MISSING / DAMAGED (handed-over elements only, differences only). When
     * present the server uses it instead of [missingAccessories]; a difference becomes TANY's incident, never blocking.
     */
    val kit: KitChecks? = null,
)

@Serializable
data class IncidentBody(val collectPointId: String, val type: IncidentType, val description: String? = null)

/** Body for routes that only need the point (`deposit-refund`, `nudge`, notifications read). */
@Serializable
data class CollectPointBody(val collectPointId: String)

/**
 * `POST bookings/{id}/deposit-refund` — [expectedAmount] (additive) is the amount displayed to the merchant; if the
 * deposit decision was re-evaluated meanwhile the backend answers 409 `deposit_amount_changed` + `currentAmount`
 * and the screen must reload the booking (never hand back a stale amount).
 */
@Serializable
data class DepositRefundBody(val collectPointId: String, val expectedAmount: MoneyAmount? = null)

@Serializable
data class NudgeResponse(val nudged: Boolean, val retryAfterSeconds: Int = 0, val booking: MerchantBookingDetail? = null)

@Serializable
data class MerchantEventBody(
    val collectPointId: String,
    val type: MerchantReportedEvent,
    val metadata: Map<String, String>? = null,
)
