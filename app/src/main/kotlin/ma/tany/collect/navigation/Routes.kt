package ma.tany.collect.navigation

import kotlinx.serialization.Serializable

/**
 * TANY Collect destinations — MVP information architecture: Aujourd'hui · Scanner · Activité · Matériel (flag
 * `features.assets`) · Compte. Deep links (`tanycollect://…`, API_CONTRACT_V1 § 2) only OPEN a screen that re-reads
 * server state; unknown sub-paths open the parent screen.
 */
@Serializable data object TodayRoute

@Serializable data object ScanRoute

@Serializable data object ActivityRoute

@Serializable data object EquipmentRoute

@Serializable data object AccountRoute

@Serializable data class BookingRoute(val bookingId: String)

@Serializable data object ShowcaseRoute

@Serializable data object AuthPhoneRoute

@Serializable data class AuthOtpRoute(val phone: String, val devCode: String? = null)

object DeepLinks {
    const val SCHEME = "tanycollect"
    const val TODAY = "$SCHEME://today"
    const val SCAN = "$SCHEME://scan"
    const val ACTIVITY = "$SCHEME://activity"
    const val BOOKING = "$SCHEME://booking/{bookingId}"
    const val RETURN = "$SCHEME://return/{bookingId}"
    /** Revenue / settlement screens arrive with their slices; until then the links open Compte. */
    const val REVENUE = "$SCHEME://revenue"
    const val SETTLEMENT = "$SCHEME://revenue/settlement"
}
