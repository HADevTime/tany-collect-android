package ma.tany.core.model.collect

import kotlinx.serialization.Serializable
import ma.tany.core.model.common.DayHours
import ma.tany.core.model.common.InstantSerializer
import ma.tany.core.model.common.OpeningState
import java.time.Instant

/** Collect `user` (MERCHANT or ADMIN). */
@Serializable
data class CollectUser(
    val id: String,
    val firstName: String,
    val lastName: String,
    val phone: String,
    val role: CollectRole,
)

/** TANY Collect point as seen by the merchant (no coordinates, no weekly hours). */
@Serializable
data class MerchantPoint(
    val id: String,
    val name: String,
    /** Name without the "TANY Collect " prefix. */
    val shortName: String,
    val address: String,
    val city: String,
    val phone: String? = null,
    val isOpenNow: Boolean = false,
    /** FR prose — use [openingState]. */
    val openingStatus: String? = null,
    val openingState: OpeningState? = null,
    val todayHours: DayHours? = null,
)

/** `POST /collect/auth/otp/verify` (OTP requested via the shared `/auth/otp/request`). No `profile_required` branch. */
@Serializable
data class CollectAuthResponse(
    val status: String,
    val token: String,
    val user: CollectUser,
    val collectPoints: List<MerchantPoint> = emptyList(),
)

/** `GET /collect/me`. [features] mirror server flags; flags OFF change response shapes. */
@Serializable
data class CollectMe(
    val user: CollectUser,
    val collectPoints: List<MerchantPoint> = emptyList(),
    val features: CollectFeatures = CollectFeatures(),
    @Serializable(with = InstantSerializer::class) val serverTime: Instant? = null,
)

@Serializable
data class CollectFeatures(val assets: Boolean = false, val depositRefundPickup: Boolean = false)
