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

@Serializable
data class HandoverBody(val collectPointId: String, val condition: AssetCondition? = null)

@Serializable
data class ReturnIncident(val type: IncidentType, val description: String? = null)

@Serializable
data class ReturnBody(
    val collectPointId: String,
    val condition: AssetCondition,
    val missingAccessories: List<String> = emptyList(),
    val incident: ReturnIncident? = null,
)

@Serializable
data class IncidentBody(val collectPointId: String, val type: IncidentType, val description: String? = null)

/** Body for routes that only need the point (`deposit-refund`, `nudge`, notifications read). */
@Serializable
data class CollectPointBody(val collectPointId: String)

@Serializable
data class NudgeResponse(val nudged: Boolean, val retryAfterSeconds: Int = 0, val booking: MerchantBookingDetail? = null)

@Serializable
data class MerchantEventBody(
    val collectPointId: String,
    val type: MerchantReportedEvent,
    val metadata: Map<String, String>? = null,
)
