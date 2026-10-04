package ma.tany.core.model.common

import kotlinx.serialization.Serializable

/**
 * Stable backend error codes (`error.code`, API_CONTRACT_V1 § 4) — exact canonical values.
 * The networking layer never translates them; features map codes (+ details) to localized copy.
 * `error.message` is always French prose and is never shown as primary copy.
 */
@Serializable(with = ApiErrorCode.Serializer::class)
enum class ApiErrorCode(override val wire: String) : WireEnum {
    UNAUTHORIZED("unauthorized"),
    FORBIDDEN("forbidden"),
    ACCOUNT_NOT_ALLOWED("account_not_allowed"),
    WRONG_COLLECT_POINT("wrong_collect_point"),
    IDENTITY_REQUIRED("identity_required"),
    BOOKING_RESTRICTED("booking_restricted"),
    NOT_FOUND("not_found"),
    INVALID_REQUEST("invalid_request"),
    RENTAL_PERIOD_INVALID("rental_period_invalid"),
    PHOTO_ERROR("photo_error"),
    IDENTITY_INVALID_EMAIL("identity_invalid_email"),
    IDENTITY_INVALID_CIN("identity_invalid_cin"),
    IDENTITY_INVALID_PHOTO("identity_invalid_photo"),
    IDENTITY_NOT_AVAILABLE("identity_not_available"),
    IDENTITY_ALREADY_PENDING("identity_already_pending"),
    IDENTITY_ALREADY_VERIFIED("identity_already_verified"),
    SLOT_UNAVAILABLE("slot_unavailable"),
    INVALID_STATE("invalid_state"),
    PICKUP_TOO_EARLY("pickup_too_early"),
    PICKUP_ALREADY_STARTED("pickup_already_started"),
    BOOKING_EXPIRED("booking_expired"),
    CANCELLATION_POLICY_CHANGED("cancellation_policy_changed"),
    QR_ERROR("qr_error"),
    QR_EXPIRED("qr_expired"),
    QR_ALREADY_USED("qr_already_used"),
    QR_NOT_FOUND("qr_not_found"),
    QR_MALFORMED("qr_malformed"),
    QR_WRONG_PURPOSE("qr_wrong_purpose"),
    QR_WRONG_BOOKING("qr_wrong_booking"),
    QR_STALE("qr_stale"),
    ASSET_MISMATCH("asset_mismatch"),
    DEPOSIT_REFUND_QR_REQUIRED("deposit_refund_qr_required"),
    OTP_INVALID("otp_invalid"),
    OTP_EXPIRED("otp_expired"),
    OTP_TOO_MANY_ATTEMPTS("otp_too_many_attempts"),
    OTP_NO_PENDING_CODE("otp_no_pending_code"),
    SERVER_ERROR("server_error"),
    NOT_ENABLED("not_enabled"),
    INVALID_INPUT("invalid_input"),
    NOTHING_DUE("nothing_due"),
    CONFIRMATION_STALE("confirmation_stale"),
    UNKNOWN("");

    object Serializer : WireEnumSerializer<ApiErrorCode>("ApiErrorCode", entries, UNKNOWN)
}
