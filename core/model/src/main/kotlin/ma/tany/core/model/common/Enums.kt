package ma.tany.core.model.common

import kotlinx.serialization.Serializable

// Exact wire values from docs/API_CONTRACT_V1.md § 3 (tany-backend). Never rename a value: add new ones.

/** Booking lifecycle (lib/booking-status.ts). */
@Serializable(with = BookingStatus.Serializer::class)
enum class BookingStatus(override val wire: String) : WireEnum {
    RESERVED("RESERVED"),
    COLLECTED("COLLECTED"),
    RETURNED("RETURNED"),
    COMPLETED("COMPLETED"),
    CANCELLED("CANCELLED"),
    EXPIRED("EXPIRED"),
    UNKNOWN("");

    object Serializer : WireEnumSerializer<BookingStatus>("BookingStatus", entries, UNKNOWN)
}

/** Pickup mode (lib/booking-rules.ts). */
@Serializable(with = PickupType.Serializer::class)
enum class PickupType(override val wire: String) : WireEnum {
    PREVIOUS_DAY("PREVIOUS_DAY"),
    SAME_DAY_MORNING("SAME_DAY_MORNING"),
    UNKNOWN("");

    object Serializer : WireEnumSerializer<PickupType>("PickupType", entries, UNKNOWN)
}

/** Only cash at the TANY Collect in MVP. */
@Serializable(with = PaymentMethod.Serializer::class)
enum class PaymentMethod(override val wire: String) : WireEnum {
    CASH_AT_COLLECT_POINT("CASH_AT_COLLECT_POINT"),
    UNKNOWN("");

    object Serializer : WireEnumSerializer<PaymentMethod>("PaymentMethod", entries, UNKNOWN)
}

@Serializable(with = PaymentStatus.Serializer::class)
enum class PaymentStatus(override val wire: String) : WireEnum {
    PENDING("PENDING"),
    PAID("PAID"),
    REFUNDED("REFUNDED"),
    UNKNOWN("");

    object Serializer : WireEnumSerializer<PaymentStatus>("PaymentStatus", entries, UNKNOWN)
}

/** Deposit ledger state (lib/deposit-ledger.ts, derived server-side). */
@Serializable(with = DepositLedgerState.Serializer::class)
enum class DepositLedgerState(override val wire: String) : WireEnum {
    NOT_REQUIRED("NOT_REQUIRED"),
    PENDING("PENDING"),
    AWAITING_PAYMENT_CONFIRMATION("AWAITING_PAYMENT_CONFIRMATION"),
    HELD("HELD"),
    UNDER_REVIEW("UNDER_REVIEW"),
    PENDING_DECISION("PENDING_DECISION"),
    DISPUTED("DISPUTED"),
    REFUND_PENDING("REFUND_PENDING"),
    PARTIAL_REFUND_PENDING("PARTIAL_REFUND_PENDING"),
    REFUND_AWAITING_CUSTOMER("REFUND_AWAITING_CUSTOMER"),
    REFUNDED("REFUNDED"),
    PARTIALLY_REFUNDED("PARTIALLY_REFUNDED"),
    FORFEITED("FORFEITED"),
    UNKNOWN("");

    /** The ledger waits for a TANY decision: nothing may be handed back until the server sets an amount. */
    val awaitsTanyDecision: Boolean get() = this == UNDER_REVIEW || this == PENDING_DECISION

    object Serializer : WireEnumSerializer<DepositLedgerState>("DepositLedgerState", entries, UNKNOWN)
}

/** Stored deposit projection (`depositStatus`, Collect `deposit.status`). */
@Serializable(with = DepositStatus.Serializer::class)
enum class DepositStatus(override val wire: String) : WireEnum {
    NOT_APPLICABLE("NOT_APPLICABLE"),
    PENDING("PENDING"),
    HELD("HELD"),
    RETURNED("RETURNED"),
    FORFEITED("FORFEITED"),
    UNKNOWN("");

    object Serializer : WireEnumSerializer<DepositStatus>("DepositStatus", entries, UNKNOWN)
}

/** Admin deposit decision. */
@Serializable(with = DepositDecision.Serializer::class)
enum class DepositDecision(override val wire: String) : WireEnum {
    FULL_REFUND("FULL_REFUND"),
    PARTIAL_REFUND("PARTIAL_REFUND"),
    NO_REFUND("NO_REFUND"),
    UNKNOWN("");

    object Serializer : WireEnumSerializer<DepositDecision>("DepositDecision", entries, UNKNOWN)
}

/** Dynamic QR purpose (lib/qr-tokens.ts). */
@Serializable(with = QrPurpose.Serializer::class)
enum class QrPurpose(override val wire: String) : WireEnum {
    PICKUP("PICKUP"),
    RETURN("RETURN"),
    DEPOSIT_REFUND("DEPOSIT_REFUND"),
    SETTLEMENT_COLLECTION("SETTLEMENT_COLLECTION"),
    UNKNOWN("");

    object Serializer : WireEnumSerializer<QrPurpose>("QrPurpose", entries, UNKNOWN)
}

/** Availability `reasonCode` (lib/availability.ts). */
@Serializable(with = AvailabilityReason.Serializer::class)
enum class AvailabilityReason(override val wire: String) : WireEnum {
    INVALID_DATES("invalid_dates"),
    END_BEFORE_START("end_before_start"),
    TOO_LONG("too_long"),
    MULTI_DAY_DISABLED("multi_day_disabled"),
    RETURN_DAY_CLOSED("return_day_closed"),
    NO_PICKUP_MODE("no_pickup_mode"),
    NO_PICKUP_WINDOW("no_pickup_window"),
    FULLY_BOOKED("fully_booked"),
    UNKNOWN("");

    object Serializer : WireEnumSerializer<AvailabilityReason>("AvailabilityReason", entries, UNKNOWN)
}

/** `rental_period_invalid.reason`. */
@Serializable(with = RentalPeriodInvalidReason.Serializer::class)
enum class RentalPeriodInvalidReason(override val wire: String) : WireEnum {
    INVALID_DATES("invalid_dates"),
    END_BEFORE_START("end_before_start"),
    TOO_LONG("too_long"),
    MULTI_DAY_DISABLED("multi_day_disabled"),
    UNKNOWN("");

    object Serializer : WireEnumSerializer<RentalPeriodInvalidReason>("RentalPeriodInvalidReason", entries, UNKNOWN)
}

@Serializable(with = AssetCondition.Serializer::class)
enum class AssetCondition(override val wire: String) : WireEnum {
    GOOD("good"),
    ISSUE_REPORTED("issue_reported"),
    UNKNOWN("");

    object Serializer : WireEnumSerializer<AssetCondition>("AssetCondition", entries, UNKNOWN)
}

@Serializable(with = PhotoType.Serializer::class)
enum class PhotoType(override val wire: String) : WireEnum {
    PICKUP("PICKUP"),
    RETURN("RETURN"),
    UNKNOWN("");

    object Serializer : WireEnumSerializer<PhotoType>("PhotoType", entries, UNKNOWN)
}

@Serializable(with = IncidentType.Serializer::class)
enum class IncidentType(override val wire: String) : WireEnum {
    DAMAGED("DAMAGED"),
    MISSING_ACCESSORY("MISSING_ACCESSORY"),
    VERY_DIRTY("VERY_DIRTY"),
    WRONG_ASSET("WRONG_ASSET"),
    BOOKING_PROBLEM("BOOKING_PROBLEM"),
    OTHER("OTHER"),
    DEPOSIT_DISPUTE("DEPOSIT_DISPUTE"),

    /** Customer contested the handover: the item must be located (tany-backend `lib/types.ts`). */
    HANDOVER_DISPUTED("HANDOVER_DISPUTED"),

    /** Rental closed without a confirmed return location: the item must be located. */
    ASSET_LOCATION_UNRESOLVED("ASSET_LOCATION_UNRESOLVED"),
    UNKNOWN("");

    object Serializer : WireEnumSerializer<IncidentType>("IncidentType", entries, UNKNOWN)
}

@Serializable(with = IncidentStatus.Serializer::class)
enum class IncidentStatus(override val wire: String) : WireEnum {
    OPEN("OPEN"),
    UNDER_REVIEW("UNDER_REVIEW"),
    RESOLVED("RESOLVED"),
    UNKNOWN("");

    object Serializer : WireEnumSerializer<IncidentStatus>("IncidentStatus", entries, UNKNOWN)
}

/** Identity V1 status (lib/identity.ts). */
@Serializable(with = IdentityStatus.Serializer::class)
enum class IdentityStatus(override val wire: String) : WireEnum {
    NONE("NONE"),
    PENDING("PENDING"),
    VERIFIED("VERIFIED"),
    REJECTED("REJECTED"),
    EXPIRED("EXPIRED"),
    UNKNOWN("");

    object Serializer : WireEnumSerializer<IdentityStatus>("IdentityStatus", entries, UNKNOWN)
}

@Serializable(with = IdentityRejectionReason.Serializer::class)
enum class IdentityRejectionReason(override val wire: String) : WireEnum {
    DOCUMENT_UNREADABLE("DOCUMENT_UNREADABLE"),
    DOCUMENT_EXPIRED("DOCUMENT_EXPIRED"),
    INCONSISTENT_INFORMATION("INCONSISTENT_INFORMATION"),
    SELFIE_UNUSABLE("SELFIE_UNUSABLE"),
    CIN_ALREADY_USED("CIN_ALREADY_USED"),
    OTHER("OTHER"),
    UNKNOWN("");

    object Serializer : WireEnumSerializer<IdentityRejectionReason>("IdentityRejectionReason", entries, UNKNOWN)
}

/** Structured opening state (use instead of FR `openingStatus`). */
@Serializable(with = OpeningStateKind.Serializer::class)
enum class OpeningStateKind(override val wire: String) : WireEnum {
    OPEN_UNTIL("OPEN_UNTIL"),
    OPENS_TODAY("OPENS_TODAY"),
    OPENS_TOMORROW("OPENS_TOMORROW"),
    OPENS_ON_DAY("OPENS_ON_DAY"),
    CLOSED("CLOSED"),
    UNKNOWN("");

    object Serializer : WireEnumSerializer<OpeningStateKind>("OpeningStateKind", entries, UNKNOWN)
}

@Serializable(with = NotificationCategory.Serializer::class)
enum class NotificationCategory(override val wire: String) : WireEnum {
    IDENTITY("IDENTITY"),
    BOOKING("BOOKING"),
    PICKUP("PICKUP"),
    RETURN("RETURN"),
    DEPOSIT("DEPOSIT"),
    INCIDENT("INCIDENT"),
    OPERATIONS("OPERATIONS"),
    ACCOUNT("ACCOUNT"),
    UNKNOWN("");

    object Serializer : WireEnumSerializer<NotificationCategory>("NotificationCategory", entries, UNKNOWN)
}

@Serializable(with = NotificationTone.Serializer::class)
enum class NotificationTone(override val wire: String) : WireEnum {
    INFO("INFO"),
    SUCCESS("SUCCESS"),
    ACTION_REQUIRED("ACTION_REQUIRED"),
    ATTENTION("ATTENTION"),
    UNKNOWN("");

    object Serializer : WireEnumSerializer<NotificationTone>("NotificationTone", entries, UNKNOWN)
}

@Serializable(with = NotificationActionKind.Serializer::class)
enum class NotificationActionKind(override val wire: String) : WireEnum {
    CONFIRM_PICKUP("CONFIRM_PICKUP"),
    CONFIRM_RETURN("CONFIRM_RETURN"),
    CONFIRM_DEPOSIT_REFUND("CONFIRM_DEPOSIT_REFUND"),
    CORRECT_IDENTITY("CORRECT_IDENTITY"),
    SHOW_PICKUP_QR("SHOW_PICKUP_QR"),
    SHOW_RETURN_QR("SHOW_RETURN_QR"),
    HAND_BACK_DEPOSIT("HAND_BACK_DEPOSIT"),
    PREPARE_PICKUP("PREPARE_PICKUP"),
    SHOW_DEPOSIT_REFUND_QR("SHOW_DEPOSIT_REFUND_QR"),
    UNKNOWN("");

    object Serializer : WireEnumSerializer<NotificationActionKind>("NotificationActionKind", entries, UNKNOWN)
}

/** Customer (27) + merchant (20) notification types (lib/notifications/catalog.ts). */
@Serializable(with = NotificationType.Serializer::class)
enum class NotificationType(override val wire: String) : WireEnum {
    IDENTITY_VERIFIED("IDENTITY_VERIFIED"),
    IDENTITY_REJECTED("IDENTITY_REJECTED"),
    BOOKING_CANCELLED_BY_TANY("BOOKING_CANCELLED_BY_TANY"),
    BOOKING_EXPIRED("BOOKING_EXPIRED"),
    BOOKING_CLOSED_BY_TANY("BOOKING_CLOSED_BY_TANY"),
    PICKUP_WINDOW_OPEN("PICKUP_WINDOW_OPEN"),
    PICKUP_CONFIRMATION_REQUIRED("PICKUP_CONFIRMATION_REQUIRED"),
    PICKUP_RECORDED_BY_TANY("PICKUP_RECORDED_BY_TANY"),
    RETURN_DUE_SOON("RETURN_DUE_SOON"),
    RETURN_LATE("RETURN_LATE"),
    RETURN_CONFIRMATION_REQUIRED("RETURN_CONFIRMATION_REQUIRED"),
    RETURN_RECORDED_BY_TANY("RETURN_RECORDED_BY_TANY"),
    CONFIRMATION_REMINDER("CONFIRMATION_REMINDER"),
    DEPOSIT_REFUND_DECIDED("DEPOSIT_REFUND_DECIDED"),
    DEPOSIT_PARTIAL_REFUND_DECIDED("DEPOSIT_PARTIAL_REFUND_DECIDED"),
    DEPOSIT_RETAINED("DEPOSIT_RETAINED"),
    DEPOSIT_DECISION_REVISED("DEPOSIT_DECISION_REVISED"),
    DEPOSIT_UNDER_REVIEW("DEPOSIT_UNDER_REVIEW"),
    DEPOSIT_REFUND_TO_COLLECT("DEPOSIT_REFUND_TO_COLLECT"),
    DEPOSIT_REFUND_CONFIRMATION_REQUIRED("DEPOSIT_REFUND_CONFIRMATION_REQUIRED"),
    DEPOSIT_REFUND_RECORDED_BY_TANY("DEPOSIT_REFUND_RECORDED_BY_TANY"),
    INCIDENT_OPENED("INCIDENT_OPENED"),
    INCIDENT_RESOLVED("INCIDENT_RESOLVED"),
    BOOKING_RESTRICTION_STARTED("BOOKING_RESTRICTION_STARTED"),
    BOOKING_RESTRICTION_ENDED("BOOKING_RESTRICTION_ENDED"),
    ACCOUNT_REVIEW_REQUIRED("ACCOUNT_REVIEW_REQUIRED"),
    MERCHANT_NEW_BOOKING("MERCHANT_NEW_BOOKING"),
    MERCHANT_BOOKING_CANCELLED("MERCHANT_BOOKING_CANCELLED"),
    MERCHANT_BOOKING_UPDATED_BY_TANY("MERCHANT_BOOKING_UPDATED_BY_TANY"),
    MERCHANT_DEPOSIT_TO_HAND_BACK("MERCHANT_DEPOSIT_TO_HAND_BACK"),
    MERCHANT_DEPOSIT_RETAINED("MERCHANT_DEPOSIT_RETAINED"),
    MERCHANT_DEPOSIT_REFUND_CONFIRMED("MERCHANT_DEPOSIT_REFUND_CONFIRMED"),
    MERCHANT_DEPOSIT_DISPUTED("MERCHANT_DEPOSIT_DISPUTED"),
    MERCHANT_INCIDENT_OPENED_BY_TANY("MERCHANT_INCIDENT_OPENED_BY_TANY"),
    MERCHANT_INCIDENT_RESOLVED("MERCHANT_INCIDENT_RESOLVED"),
    MERCHANT_BOOKING_EXPIRED("MERCHANT_BOOKING_EXPIRED"),
    MERCHANT_ASSET_REPLACED("MERCHANT_ASSET_REPLACED"),
    MERCHANT_CUSTOMER_CONFIRMED("MERCHANT_CUSTOMER_CONFIRMED"),
    MERCHANT_BONUS_UNLOCKED("MERCHANT_BONUS_UNLOCKED"),
    MERCHANT_PARTNERSHIP_UPDATED("MERCHANT_PARTNERSHIP_UPDATED"),
    MERCHANT_SETTLEMENT_READY("MERCHANT_SETTLEMENT_READY"),
    MERCHANT_COLLECTION_SCHEDULED("MERCHANT_COLLECTION_SCHEDULED"),
    MERCHANT_COLLECTION_CONFIRMATION_REQUIRED("MERCHANT_COLLECTION_CONFIRMATION_REQUIRED"),
    MERCHANT_COLLECTION_PARTIAL("MERCHANT_COLLECTION_PARTIAL"),
    MERCHANT_COLLECTION_COMPLETED("MERCHANT_COLLECTION_COMPLETED"),
    MERCHANT_SETTLEMENT_OVERDUE("MERCHANT_SETTLEMENT_OVERDUE"),
    UNKNOWN("");

    object Serializer : WireEnumSerializer<NotificationType>("NotificationType", entries, UNKNOWN)
}
