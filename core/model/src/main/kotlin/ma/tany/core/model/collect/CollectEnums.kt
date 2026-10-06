package ma.tany.core.model.collect

import kotlinx.serialization.Serializable
import ma.tany.core.model.common.WireEnum
import ma.tany.core.model.common.WireEnumSerializer

// Exact wire values from docs/API_CONTRACT_V1.md § 3 (tany-backend). Never rename a value: add new ones.

/** Collect session role (ADMIN = multi-point). */
@Serializable(with = CollectRole.Serializer::class)
enum class CollectRole(override val wire: String) : WireEnum {
    MERCHANT("MERCHANT"),
    ADMIN("ADMIN"),
    UNKNOWN("");

    object Serializer : WireEnumSerializer<CollectRole>("CollectRole", entries, UNKNOWN)
}

@Serializable(with = OperationKind.Serializer::class)
enum class OperationKind(override val wire: String) : WireEnum {
    PICKUP("PICKUP"),
    RETURN("RETURN"),
    UNKNOWN("");

    object Serializer : WireEnumSerializer<OperationKind>("OperationKind", entries, UNKNOWN)
}

/** Operational phase computed by the server (lib/collect-operations.ts). */
@Serializable(with = MerchantPhase.Serializer::class)
enum class MerchantPhase(override val wire: String) : WireEnum {
    PICKUP_UPCOMING("pickup_upcoming"),
    PICKUP_READY("pickup_ready"),
    PICKUP_IN_PROGRESS("pickup_in_progress"),
    PICKUP_AWAITING_CUSTOMER("pickup_awaiting_customer"),
    NO_SHOW("no_show"),
    WITH_CUSTOMER("with_customer"),
    RETURN_DUE("return_due"),
    RETURN_LATE("return_late"),
    RETURN_IN_PROGRESS("return_in_progress"),
    RETURN_AWAITING_CUSTOMER("return_awaiting_customer"),
    DEPOSIT_TO_REFUND("deposit_to_refund"),
    DEPOSIT_AWAITING_CUSTOMER("deposit_awaiting_customer"),
    DEPOSIT_DISPUTED("deposit_disputed"),
    BLOCKED_PENDING_REVIEW("blocked_pending_review"),
    COMPLETED("completed"),
    CANCELLED("cancelled"),
    UNKNOWN("");

    object Serializer : WireEnumSerializer<MerchantPhase>("MerchantPhase", entries, UNKNOWN)
}

/** The only physical deposit gesture expected from the merchant. */
@Serializable(with = MerchantDepositAction.Serializer::class)
enum class MerchantDepositAction(override val wire: String) : WireEnum {
    COLLECT("COLLECT"),
    HAND_BACK("HAND_BACK"),
    NONE("NONE"),
    UNKNOWN("");

    object Serializer : WireEnumSerializer<MerchantDepositAction>("MerchantDepositAction", entries, UNKNOWN)
}

/**
 * Deposit hand-back orchestration (server, additive `deposit.handBackMode`): [IMMEDIATE] = handed back at the counter
 * in the same session as the return, NO client QR; [DEFERRED] = the customer comes back later, the `DEPOSIT_REFUND` QR
 * (or 6-digit fallback) is required. Absent = no hand-back expected / older backend.
 */
@Serializable(with = DepositHandBackMode.Serializer::class)
enum class DepositHandBackMode(override val wire: String) : WireEnum {
    IMMEDIATE("IMMEDIATE"),
    DEFERRED("DEFERRED"),
    UNKNOWN("");

    object Serializer : WireEnumSerializer<DepositHandBackMode>("DepositHandBackMode", entries, UNKNOWN)
}

/** History actor role. */
@Serializable(with = ActorRole.Serializer::class)
enum class ActorRole(override val wire: String) : WireEnum {
    SELF("SELF"),
    CUSTOMER("CUSTOMER"),
    TANY("TANY"),
    MERCHANT("MERCHANT"),
    SYSTEM("SYSTEM"),
    UNKNOWN("");

    object Serializer : WireEnumSerializer<ActorRole>("ActorRole", entries, UNKNOWN)
}

/** History event types (collect-operations EVENT_LABELS). */
@Serializable(with = BookingEventType.Serializer::class)
enum class BookingEventType(override val wire: String) : WireEnum {
    BOOKING_CREATED("BOOKING_CREATED"),
    PICKUP_QR_VERIFIED("PICKUP_QR_VERIFIED"),
    PICKUP_ASSET_VERIFIED("PICKUP_ASSET_VERIFIED"),
    PICKUP_PHOTO_CAPTURED("PICKUP_PHOTO_CAPTURED"),
    PAYMENT_CONFIRMED("PAYMENT_CONFIRMED"),
    PICKUP_CONFIRMED_MERCHANT("PICKUP_CONFIRMED_MERCHANT"),
    PICKUP_CONFIRMED_CUSTOMER("PICKUP_CONFIRMED_CUSTOMER"),
    ASSET_COLLECTED("ASSET_COLLECTED"),
    RETURN_QR_VERIFIED("RETURN_QR_VERIFIED"),
    RETURN_ASSET_VERIFIED("RETURN_ASSET_VERIFIED"),
    RETURN_PHOTO_CAPTURED("RETURN_PHOTO_CAPTURED"),
    RETURN_CONFIRMED_MERCHANT("RETURN_CONFIRMED_MERCHANT"),
    RETURN_CONFIRMED_CUSTOMER("RETURN_CONFIRMED_CUSTOMER"),
    ASSET_RETURNED("ASSET_RETURNED"),
    DEPOSIT_REFUND_QR_VERIFIED("DEPOSIT_REFUND_QR_VERIFIED"),
    DEPOSIT_REFUND_MERCHANT("DEPOSIT_REFUND_MERCHANT"),
    DEPOSIT_REFUND_CUSTOMER("DEPOSIT_REFUND_CUSTOMER"),
    DEPOSIT_DISPUTED("DEPOSIT_DISPUTED"),
    DEPOSIT_DECISION_RECORDED("DEPOSIT_DECISION_RECORDED"),
    DEPOSIT_FORFEITED("DEPOSIT_FORFEITED"),
    DEPOSIT_KEPT_PENDING("DEPOSIT_KEPT_PENDING"),
    DEPOSIT_REFUND_ADMIN_OVERRIDE("DEPOSIT_REFUND_ADMIN_OVERRIDE"),
    BOOKING_COMPLETED("BOOKING_COMPLETED"),
    BOOKING_CANCELLED("BOOKING_CANCELLED"),
    BOOKING_EXPIRED("BOOKING_EXPIRED"),
    INCIDENT_REPORTED("INCIDENT_REPORTED"),
    INCIDENT_RESOLVED("INCIDENT_RESOLVED"),
    MERCHANT_NUDGED_CUSTOMER("MERCHANT_NUDGED_CUSTOMER"),
    MERCHANT_OPENED_BOOKING("MERCHANT_OPENED_BOOKING"),
    UNKNOWN("");

    object Serializer : WireEnumSerializer<BookingEventType>("BookingEventType", entries, UNKNOWN)
}

/** Client-reported audit events (`POST bookings/{id}/events`). */
@Serializable(with = MerchantReportedEvent.Serializer::class)
enum class MerchantReportedEvent(override val wire: String) : WireEnum {
    MERCHANT_OPENED_BOOKING("MERCHANT_OPENED_BOOKING"),
    QR_SCAN_REJECTED("QR_SCAN_REJECTED"),
    ASSET_SCAN_REJECTED("ASSET_SCAN_REJECTED"),
    UNKNOWN("");

    object Serializer : WireEnumSerializer<MerchantReportedEvent>("MerchantReportedEvent", entries, UNKNOWN)
}

/** Collect scan `reason` (UPPERCASE). */
@Serializable(with = QrErrorReason.Serializer::class)
enum class QrErrorReason(override val wire: String) : WireEnum {
    MALFORMED("MALFORMED"),
    NOT_FOUND("NOT_FOUND"),
    ALREADY_USED("ALREADY_USED"),
    EXPIRED("EXPIRED"),
    WRONG_PURPOSE("WRONG_PURPOSE"),
    WRONG_BOOKING("WRONG_BOOKING"),
    STALE("STALE"),
    UNKNOWN("");

    object Serializer : WireEnumSerializer<QrErrorReason>("QrErrorReason", entries, UNKNOWN)
}

/** Stored asset status. */
@Serializable(with = AssetStatus.Serializer::class)
enum class AssetStatus(override val wire: String) : WireEnum {
    AVAILABLE("AVAILABLE"),
    WITH_CUSTOMER("WITH_CUSTOMER"),
    INSPECTION("INSPECTION"),
    OUT_OF_SERVICE("OUT_OF_SERVICE"),
    LOST("LOST"),
    RETIRED("RETIRED"),
    UNKNOWN("");

    object Serializer : WireEnumSerializer<AssetStatus>("AssetStatus", entries, UNKNOWN)
}

/** Derived Collect asset status (lib/collect-assets.ts). */
@Serializable(with = CollectAssetStatus.Serializer::class)
enum class CollectAssetStatus(override val wire: String) : WireEnum {
    AVAILABLE("AVAILABLE"),
    RESERVED("RESERVED"),
    TO_HAND_OVER("TO_HAND_OVER"),
    WITH_CUSTOMER("WITH_CUSTOMER"),
    RETURN_DUE("RETURN_DUE"),
    RETURN_LATE("RETURN_LATE"),
    INSPECTION("INSPECTION"),
    OUT_OF_SERVICE("OUT_OF_SERVICE"),
    LOST("LOST"),
    LOCATION_TO_CONFIRM("LOCATION_TO_CONFIRM"),
    UNKNOWN("");

    object Serializer : WireEnumSerializer<CollectAssetStatus>("CollectAssetStatus", entries, UNKNOWN)
}

@Serializable(with = AssetGroup.Serializer::class)
enum class AssetGroup(override val wire: String) : WireEnum {
    AVAILABLE("AVAILABLE"),
    RESERVED("RESERVED"),
    OUT("OUT"),
    UNAVAILABLE("UNAVAILABLE"),
    UNKNOWN("");

    object Serializer : WireEnumSerializer<AssetGroup>("AssetGroup", entries, UNKNOWN)
}

/** Server tone for an asset row. */
@Serializable(with = AssetTone.Serializer::class)
enum class AssetTone(override val wire: String) : WireEnum {
    SUCCESS("SUCCESS"),
    INFO("INFO"),
    ACTION("ACTION"),
    WARNING("WARNING"),
    DANGER("DANGER"),
    NEUTRAL("NEUTRAL"),
    UNKNOWN("");

    object Serializer : WireEnumSerializer<AssetTone>("AssetTone", entries, UNKNOWN)
}

@Serializable(with = AssetAttentionReason.Serializer::class)
enum class AssetAttentionReason(override val wire: String) : WireEnum {
    RETURN_LATE("RETURN_LATE"),
    LOCATION_TO_CONFIRM("LOCATION_TO_CONFIRM"),
    INSPECTION("INSPECTION"),
    OUT_OF_SERVICE("OUT_OF_SERVICE"),
    LOST("LOST"),
    ISSUE_REPORTED("ISSUE_REPORTED"),
    REPLACE_NOW("REPLACE_NOW"),
    REPLACE_SOON("REPLACE_SOON"),
    UNKNOWN("");

    object Serializer : WireEnumSerializer<AssetAttentionReason>("AssetAttentionReason", entries, UNKNOWN)
}

@Serializable(with = AssetLifecycleStatus.Serializer::class)
enum class AssetLifecycleStatus(override val wire: String) : WireEnum {
    NOT_CONFIGURED("not_configured"),
    GOOD("good"),
    WATCH("watch"),
    REPLACE_SOON("replace_soon"),
    REPLACE_NOW("replace_now"),
    UNKNOWN("");

    object Serializer : WireEnumSerializer<AssetLifecycleStatus>("AssetLifecycleStatus", entries, UNKNOWN)
}

/** `…/assets?filter=`. */
@Serializable(with = AssetFilter.Serializer::class)
enum class AssetFilter(override val wire: String) : WireEnum {
    ALL("ALL"),
    AVAILABLE("AVAILABLE"),
    OUT("OUT"),
    ATTENTION("ATTENTION"),
    UNKNOWN("");

    object Serializer : WireEnumSerializer<AssetFilter>("AssetFilter", entries, UNKNOWN)
}

/** Revenue is always ESTIMATED — never “paid”. */
@Serializable(with = EarningsStatus.Serializer::class)
enum class EarningsStatus(override val wire: String) : WireEnum {
    ESTIMATED("ESTIMATED"),
    UNKNOWN("");

    object Serializer : WireEnumSerializer<EarningsStatus>("EarningsStatus", entries, UNKNOWN)
}

@Serializable(with = RevenueActivityKind.Serializer::class)
enum class RevenueActivityKind(override val wire: String) : WireEnum {
    RENTAL_COMPLETED("RENTAL_COMPLETED"),
    BONUS_UNLOCKED("BONUS_UNLOCKED"),
    DEPOSIT_RECEIVED("DEPOSIT_RECEIVED"),
    DEPOSIT_REFUNDED("DEPOSIT_REFUNDED"),
    ADJUSTMENT("ADJUSTMENT"),
    UNKNOWN("");

    object Serializer : WireEnumSerializer<RevenueActivityKind>("RevenueActivityKind", entries, UNKNOWN)
}

@Serializable(with = RevenueAmountKind.Serializer::class)
enum class RevenueAmountKind(override val wire: String) : WireEnum {
    EARNING("EARNING"),
    DEPOSIT("DEPOSIT"),
    UNKNOWN("");

    object Serializer : WireEnumSerializer<RevenueAmountKind>("RevenueAmountKind", entries, UNKNOWN)
}

@Serializable(with = RevenueMetric.Serializer::class)
enum class RevenueMetric(override val wire: String) : WireEnum {
    RENTAL_COUNT("RENTAL_COUNT"),
    RENTAL_REVENUE("RENTAL_REVENUE"),
    UNKNOWN("");

    object Serializer : WireEnumSerializer<RevenueMetric>("RevenueMetric", entries, UNKNOWN)
}

/** `revenue.actions[].kind`. */
@Serializable(with = RevenueActionKind.Serializer::class)
enum class RevenueActionKind(override val wire: String) : WireEnum {
    HAND_BACK_DEPOSIT("HAND_BACK_DEPOSIT"),
    UNKNOWN("");

    object Serializer : WireEnumSerializer<RevenueActionKind>("RevenueActionKind", entries, UNKNOWN)
}

/** `settlement.summary.status`. */
@Serializable(with = SettlementStatus.Serializer::class)
enum class SettlementStatus(override val wire: String) : WireEnum {
    NOT_CONFIGURED("NOT_CONFIGURED"),
    NOTHING_DUE("NOTHING_DUE"),
    DUE("DUE"),
    PARTIALLY_COLLECTED("PARTIALLY_COLLECTED"),
    COLLECTION_IN_PROGRESS("COLLECTION_IN_PROGRESS"),
    CONFIRMATION_REQUIRED("CONFIRMATION_REQUIRED"),
    DISPUTED("DISPUTED"),
    UNKNOWN("");

    object Serializer : WireEnumSerializer<SettlementStatus>("SettlementStatus", entries, UNKNOWN)
}

/** Settlement statement status. */
@Serializable(with = StatementStatus.Serializer::class)
enum class StatementStatus(override val wire: String) : WireEnum {
    DUE("DUE"),
    PARTIALLY_COLLECTED("PARTIALLY_COLLECTED"),
    COLLECTED("COLLECTED"),
    UNKNOWN("");

    object Serializer : WireEnumSerializer<StatementStatus>("StatementStatus", entries, UNKNOWN)
}

/** Settlement collection status. */
@Serializable(with = CollectionStatus.Serializer::class)
enum class CollectionStatus(override val wire: String) : WireEnum {
    SCHEDULED("SCHEDULED"),
    IN_PROGRESS("IN_PROGRESS"),
    AWAITING_MERCHANT("AWAITING_MERCHANT"),
    DISPUTED("DISPUTED"),
    COMPLETED("COMPLETED"),
    PARTIALLY_COLLECTED("PARTIALLY_COLLECTED"),
    CANCELLED("CANCELLED"),
    UNKNOWN("");

    object Serializer : WireEnumSerializer<CollectionStatus>("CollectionStatus", entries, UNKNOWN)
}
