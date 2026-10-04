package ma.tany.core.model.common

import kotlinx.serialization.Serializable
import java.time.Instant

/** `GET /config` — public server configuration (some flags only; see contract § 4). */
@Serializable
data class AppConfig(
    val apiVersion: Int,
    val rentalUnit: String,
    val previousDayPickupEnabled: Boolean = false,
    val sameDayMorningPickupEnabled: Boolean = false,
    val qrTokenTtlSeconds: Int = 0,
    val multiDayRentalsEnabled: Boolean = false,
    val minRentalDays: Int = 1,
    val maxRentalDays: Int = 1,
    val earlyReturnRefund: Boolean = false,
    val paymentMethods: List<PaymentMethod> = emptyList(),
    val identityVerificationRequired: Boolean = false,
    val notificationsEnabled: Boolean = false,
    @Serializable(with = InstantSerializer::class) val serverTime: Instant? = null,
) {
    companion object {
        /** The contract version this app was built against. */
        const val SUPPORTED_API_VERSION: Int = 1
    }
}
