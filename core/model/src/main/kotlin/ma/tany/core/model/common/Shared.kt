package ma.tany.core.model.common

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** A rental = 1..N consecutive usage days (never hours). Server-computed. */
@Serializable
data class UsagePeriod(
    @Serializable(with = BusinessDateSerializer::class) val startDate: LocalDate,
    @Serializable(with = BusinessDateSerializer::class) val endDate: LocalDate,
    val dayCount: Int,
)

/** Server totals for the whole period (daily × days, ONE deposit). Never recomputed by the app. */
@Serializable
data class BookingPricing(
    val dailyPrice: MoneyAmount,
    val dayCount: Int,
    val rentalTotal: MoneyAmount,
    val deposit: MoneyAmount? = null,
    val currency: String,
    val totalDueAtPickup: MoneyAmount,
)

@Serializable
data class BookingPayment(
    val method: PaymentMethod,
    val status: PaymentStatus,
    @Serializable(with = InstantSerializer::class) val paidAt: Instant? = null,
    val rentalAmount: MoneyAmount,
    val depositAmount: MoneyAmount? = null,
    val totalDueAtPickup: MoneyAmount,
)

/** Structured opening state (use instead of the FR `openingStatus`). Times are Africa/Casablanca. */
@Serializable
data class OpeningState(
    val kind: OpeningStateKind,
    @Serializable(with = BusinessClockTimeSerializer::class) val time: LocalTime? = null,
    val day: Weekday? = null,
)

@Serializable
data class DayHours(
    @Serializable(with = BusinessClockTimeSerializer::class) val open: LocalTime,
    @Serializable(with = BusinessClockTimeSerializer::class) val close: LocalTime,
)
