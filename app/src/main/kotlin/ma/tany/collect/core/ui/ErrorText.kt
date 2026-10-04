package ma.tany.collect.core.ui

import androidx.annotation.StringRes
import ma.tany.collect.R
import ma.tany.core.model.common.ApiErrorCode
import ma.tany.core.network.ApiError

/**
 * Maps a semantic API error to localized merchant copy. Exact canonical codes (API_CONTRACT_V1 § 4); the server's
 * French `message` is never shown. QR errors always lead to « ask for a new QR ».
 */
@StringRes
fun ApiError.messageRes(): Int = when (this) {
    is ApiError.Network -> R.string.error_network
    ApiError.Unauthorized -> R.string.error_session_expired
    is ApiError.Decoding -> R.string.error_update_required
    is ApiError.Unexpected -> R.string.error_generic
    is ApiError.Http -> when (code) {
        ApiErrorCode.OTP_INVALID -> R.string.error_otp_invalid
        ApiErrorCode.OTP_EXPIRED -> R.string.error_otp_expired
        ApiErrorCode.OTP_TOO_MANY_ATTEMPTS -> R.string.error_otp_too_many_attempts
        ApiErrorCode.OTP_NO_PENDING_CODE -> R.string.error_otp_no_pending_code
        ApiErrorCode.ACCOUNT_NOT_ALLOWED -> R.string.error_account_not_allowed
        ApiErrorCode.INVALID_REQUEST -> R.string.error_invalid_request
        ApiErrorCode.NOT_FOUND -> R.string.error_not_found
        ApiErrorCode.UNAUTHORIZED -> R.string.error_session_expired
        ApiErrorCode.FORBIDDEN -> R.string.error_forbidden
        ApiErrorCode.WRONG_COLLECT_POINT -> R.string.error_wrong_collect_point
        ApiErrorCode.QR_EXPIRED -> R.string.error_qr_expired
        ApiErrorCode.QR_ALREADY_USED -> R.string.error_qr_already_used
        ApiErrorCode.QR_NOT_FOUND -> R.string.error_qr_not_found
        ApiErrorCode.QR_MALFORMED -> R.string.error_qr_malformed
        ApiErrorCode.QR_WRONG_PURPOSE -> R.string.error_qr_wrong_purpose
        ApiErrorCode.QR_WRONG_BOOKING -> R.string.error_qr_wrong_booking
        ApiErrorCode.QR_STALE -> R.string.error_qr_stale
        ApiErrorCode.ASSET_MISMATCH -> R.string.error_asset_mismatch
        ApiErrorCode.PICKUP_TOO_EARLY -> R.string.error_pickup_too_early
        ApiErrorCode.SERVER_ERROR -> R.string.error_server
        else -> R.string.error_generic
    }
}
