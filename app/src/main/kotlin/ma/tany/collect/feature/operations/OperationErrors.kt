package ma.tany.collect.feature.operations

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ma.tany.collect.R
import ma.tany.collect.core.ui.messageRes
import ma.tany.core.designsystem.component.LocalTanyFormatters
import ma.tany.core.designsystem.format.ltrIsolated
import ma.tany.core.model.common.ApiErrorCode
import ma.tany.core.network.ApiError
import ma.tany.core.network.CollectOperationError
import java.time.Instant

/** Localized copy for a server refusal (structured code + details only; the server's French message is never shown). */
@Composable
fun CollectOperationError.text(): String = when (this) {
    is CollectOperationError.Qr -> stringResource(
        when (code) {
            ApiErrorCode.QR_EXPIRED -> R.string.op_qr_expired
            ApiErrorCode.QR_ALREADY_USED -> R.string.op_qr_used
            ApiErrorCode.QR_WRONG_BOOKING -> R.string.op_qr_wrong_booking
            ApiErrorCode.QR_WRONG_PURPOSE -> R.string.op_qr_wrong_purpose
            ApiErrorCode.QR_STALE -> R.string.op_qr_stale
            else -> R.string.op_qr_unknown
        },
    )
    is CollectOperationError.AssetMismatch -> if (expected != null && scanned != null) {
        stringResource(R.string.op_asset_mismatch_codes, ltrIsolated(scanned), ltrIsolated(expected))
    } else {
        stringResource(R.string.op_asset_mismatch)
    }
    CollectOperationError.WrongPoint -> stringResource(R.string.op_wrong_point)
    is CollectOperationError.TooEarly -> {
        val formatters = LocalTanyFormatters.current
        val start = windowStart?.let { runCatching { Instant.parse(it) }.getOrNull() }
        val end = windowEnd?.let { runCatching { Instant.parse(it) }.getOrNull() }
        if (start != null && end != null) {
            stringResource(R.string.op_too_early_window, formatters.businessWindow(start, end))
        } else {
            stringResource(R.string.op_too_early)
        }
    }
    CollectOperationError.IdentityRequired -> stringResource(R.string.op_identity_required)
    CollectOperationError.Photo -> stringResource(R.string.op_photo_error)
    CollectOperationError.NotAllowedNow -> stringResource(R.string.op_not_allowed_now)
    CollectOperationError.RateLimited -> stringResource(R.string.op_rate_limited)
    is CollectOperationError.Other -> if (error is ApiError.Network) {
        stringResource(R.string.op_unknown_outcome)
    } else {
        stringResource(error.messageRes())
    }
}
