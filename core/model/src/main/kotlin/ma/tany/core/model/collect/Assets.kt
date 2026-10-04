package ma.tany.core.model.collect

import kotlinx.serialization.Serializable
import ma.tany.core.model.common.AssetCondition
import ma.tany.core.model.common.BookingPricing
import ma.tany.core.model.common.BookingStatus
import ma.tany.core.model.common.BusinessDateSerializer
import ma.tany.core.model.common.InstantSerializer
import ma.tany.core.model.common.UsagePeriod
import java.time.Instant
import java.time.LocalDate

/**
 * Collect equipment (READ-ONLY inventory of the active point, flag `features.assets`).
 * States are derived by the server; no customer data. `…Label` fields are FR prose — use the codes.
 */
@Serializable
data class AssetsResponse(
    val enabled: Boolean,
    val collectPointId: String? = null,
    val collectPointName: String? = null,
    val filter: AssetFilter? = null,
    val query: String? = null,
    val counts: AssetCounts? = null,
    val assets: List<AssetItem> = emptyList(),
    @Serializable(with = InstantSerializer::class) val serverTime: Instant? = null,
)

@Serializable
data class AssetCounts(
    val all: Int = 0,
    val available: Int = 0,
    val reserved: Int = 0,
    val out: Int = 0,
    val unavailable: Int = 0,
    val attention: Int = 0,
)

@Serializable
data class AssetItem(
    val id: String,
    val code: String,
    val product: OperationProduct,
    val status: CollectAssetStatus,
    val statusLabel: String? = null,
    val group: AssetGroup,
    val tone: AssetTone,
    val attention: Boolean = false,
    val attentionLabel: String? = null,
    val attentionReason: AssetAttentionReason? = null,
    val maintenance: Boolean = false,
    val condition: AssetCondition,
    val currentBooking: BookingLite? = null,
    val nextBooking: BookingLite? = null,
)

/** Booking reference WITHOUT any customer data. */
@Serializable
data class BookingLite(
    val id: String,
    val reference: String,
    val status: BookingStatus,
    @Serializable(with = BusinessDateSerializer::class) val usageDate: LocalDate,
    @Serializable(with = BusinessDateSerializer::class) val usageEndDate: LocalDate? = null,
    val dayCount: Int = 1,
    @Serializable(with = InstantSerializer::class) val pickupWindowStart: Instant,
    @Serializable(with = InstantSerializer::class) val pickupWindowEnd: Instant,
    @Serializable(with = InstantSerializer::class) val returnWindowStart: Instant? = null,
    @Serializable(with = InstantSerializer::class) val returnDeadline: Instant,
    val usagePeriod: UsagePeriod? = null,
    val pricing: BookingPricing? = null,
)

@Serializable
data class AssetDetailResponse(val asset: AssetDetail)

@Serializable
data class AssetDetail(
    val id: String,
    val code: String,
    val product: OperationProduct,
    val status: CollectAssetStatus,
    val statusLabel: String? = null,
    val group: AssetGroup,
    val tone: AssetTone,
    val attention: Boolean = false,
    val attentionLabel: String? = null,
    val attentionReason: AssetAttentionReason? = null,
    val maintenance: Boolean = false,
    val condition: AssetCondition,
    val currentBooking: BookingLite? = null,
    val nextBooking: BookingLite? = null,
    val collectPoint: AssetPointRef? = null,
    @Serializable(with = InstantSerializer::class) val inServiceSince: Instant? = null,
    val completedBookings: Int = 0,
    val upcomingBookings: Int = 0,
    val turnaroundMinutes: Int = 0,
    val incidentsCount: Int = 0,
    @Serializable(with = InstantSerializer::class) val lastIncidentAt: Instant? = null,
    val lifecycle: AssetLifecycle? = null,
    val recentBookings: List<AssetRecentBooking> = emptyList(),
    @Serializable(with = InstantSerializer::class) val serverTime: Instant? = null,
)

@Serializable
data class AssetPointRef(val id: String, val name: String)

@Serializable
data class AssetLifecycle(
    val status: AssetLifecycleStatus,
    val label: String? = null,
    val percentage: Int? = null,
    /** Shape not yet observed with a value (null when not configured) — typed in the equipment slice. */
    val estimatedReplacementDate: String? = null,
    val estimatedRemainingUses: Int? = null,
)

@Serializable
data class AssetRecentBooking(
    val reference: String,
    @Serializable(with = BusinessDateSerializer::class) val usageDate: LocalDate,
    @Serializable(with = BusinessDateSerializer::class) val usageEndDate: LocalDate? = null,
    val dayCount: Int = 1,
    val usagePeriod: UsagePeriod? = null,
    val status: BookingStatus,
    val statusLabel: String? = null,
)
