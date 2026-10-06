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
    is CollectOperationError.AssetMismatch -> {
        // Local copies: properties from another module are not smart-castable.
        val expectedCode = expected
        val scannedCode = scanned
        if (expectedCode != null && scannedCode != null) {
            stringResource(R.string.op_asset_mismatch_codes, ltrIsolated(scannedCode), ltrIsolated(expectedCode))
        } else {
            stringResource(R.string.op_asset_mismatch)
        }
    }
    CollectOperationError.WrongPoint -> stringResource(R.string.op_wrong_point)
    is CollectOperationError.TooEarly -> {
        val formatters = LocalTanyFormatters.current
        val start = windowStart?.let { runCatching { Instant.parse(it) }.getOrNull() }
        val end = windowEnd?.let { runCatching { Instant.parse(it) }.getOrNull() }
        if (start != null && end != null) {
            stringResource(R.string.op_too_early_window, formatters.businessTime(start), formatters.businessWindow(start, end))
        } else {
            stringResource(R.string.op_too_early)
        }
    }
    CollectOperationError.IdentityRequired -> stringResource(R.string.op_identity_required)
    CollectOperationError.Photo -> stringResource(R.string.op_photo_error)
    CollectOperationError.NotAllowedNow -> stringResource(R.string.op_not_allowed_now)
    CollectOperationError.RateLimited -> stringResource(R.string.op_rate_limited)
    is CollectOperationError.DepositAmountChanged -> {
        val amount = currentAmount
        if (amount != null) {
            stringResource(R.string.op_deposit_amount_changed, LocalTanyFormatters.current.money(amount))
        } else {
            stringResource(R.string.op_deposit_amount_changed_plain)
        }
    }
    CollectOperationError.DepositRefundQrRequired -> stringResource(R.string.op_deposit_qr_required)
    is CollectOperationError.Other -> if (error is ApiError.Network) {
        stringResource(R.string.op_unknown_outcome)
    } else {
        stringResource(error.messageRes())
    }
}

/** Short title of a refusal (iOS « CollectFailure » titles) — the message comes from [text] / [scanText]. */
@Composable
fun CollectOperationError.title(): String = stringResource(
    when (this) {
        is CollectOperationError.Qr -> when (code) {
            ApiErrorCode.QR_EXPIRED -> R.string.op_err_title_qr_expired
            ApiErrorCode.QR_ALREADY_USED -> R.string.op_err_title_qr_used
            ApiErrorCode.QR_WRONG_PURPOSE -> R.string.op_err_title_qr_wrong_purpose
            ApiErrorCode.QR_WRONG_BOOKING -> R.string.op_err_title_qr_wrong_booking
            ApiErrorCode.QR_STALE -> R.string.op_err_title_amount_updated
            else -> R.string.op_err_title_qr_unknown
        }
        is CollectOperationError.AssetMismatch -> R.string.op_asset_wrong_item_title
        CollectOperationError.WrongPoint -> R.string.op_err_title_wrong_point
        is CollectOperationError.TooEarly -> R.string.op_err_title_too_early
        CollectOperationError.IdentityRequired -> R.string.op_err_title_identity
        CollectOperationError.Photo -> R.string.op_err_title_photo
        CollectOperationError.NotAllowedNow -> R.string.op_err_title_not_possible
        CollectOperationError.RateLimited -> R.string.op_err_title_rate_limited
        is CollectOperationError.DepositAmountChanged -> R.string.op_err_title_amount_updated
        CollectOperationError.DepositRefundQrRequired -> R.string.op_err_title_deposit_qr
        is CollectOperationError.Other -> when (val e = error) {
            is ApiError.Network -> R.string.op_err_title_offline
            ApiError.Unauthorized -> R.string.op_err_title_session
            is ApiError.Http -> when (e.code) {
                ApiErrorCode.FORBIDDEN -> R.string.op_err_title_forbidden
                ApiErrorCode.UNAUTHORIZED -> R.string.op_err_title_session
                else -> R.string.op_err_title_generic
            }
            else -> R.string.op_err_title_generic
        }
    },
)

/**
 * Message of a refusal on the SCANNER (no booking on screen yet): same codes as [text], but the recovery is « scan a new
 * QR » — nothing was re-read, and after a network failure the token may already be consumed (never retried).
 */
@Composable
fun CollectOperationError.scanText(): String = when (this) {
    CollectOperationError.NotAllowedNow -> stringResource(R.string.op_scan_not_possible)
    CollectOperationError.RateLimited -> stringResource(R.string.error_qr_rate_limited)
    is CollectOperationError.Other -> if (error is ApiError.Network) stringResource(R.string.op_scan_network) else text()
    else -> text()
}

/** Glyph of a refusal card. */
fun CollectOperationError.icon(): Int = when (this) {
    CollectOperationError.WrongPoint -> ma.tany.core.designsystem.R.drawable.ic_tany_store
    CollectOperationError.IdentityRequired -> ma.tany.core.designsystem.R.drawable.ic_tany_shield
    is CollectOperationError.TooEarly -> ma.tany.core.designsystem.R.drawable.ic_tany_clock
    is CollectOperationError.DepositAmountChanged -> ma.tany.core.designsystem.R.drawable.ic_tany_cash
    is CollectOperationError.Other -> if (error is ApiError.Network) {
        ma.tany.core.designsystem.R.drawable.ic_tany_refresh
    } else {
        ma.tany.core.designsystem.R.drawable.ic_tany_error
    }
    is CollectOperationError.Qr, CollectOperationError.RateLimited, CollectOperationError.DepositRefundQrRequired ->
        ma.tany.core.designsystem.R.drawable.ic_tany_qr
    else -> ma.tany.core.designsystem.R.drawable.ic_tany_error
}
