package ma.tany.core.network

import ma.tany.core.model.collect.AssetScanBody
import ma.tany.core.model.collect.DepositRefundBody
import ma.tany.core.model.collect.CollectPointBody
import ma.tany.core.model.collect.HandoverBody
import ma.tany.core.model.collect.KitChecks
import ma.tany.core.model.collect.IncidentBody
import ma.tany.core.model.collect.MerchantBookingDetail
import ma.tany.core.model.collect.NudgeResponse
import ma.tany.core.model.collect.PaymentBody
import ma.tany.core.model.collect.ReturnBody
import ma.tany.core.model.collect.ScanBody
import ma.tany.core.model.collect.ScanResponse
import ma.tany.core.model.common.ApiErrorCode
import ma.tany.core.model.common.AssetCondition
import ma.tany.core.model.common.MoneyAmount
import ma.tany.core.model.common.QrPurpose
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Merchant operation gestures (pickup slice). Every call carries the ACTIVE `collectPointId` (the server re-checks
 * the scope); none is idempotent ⇒ never retried automatically — after any failure the screen re-reads the booking.
 * The order of the steps and every rule (QR validity, asset match, amounts, window, identity) are the server's.
 */
interface CollectOperationsRepository {
    /** `POST /collect/scan` — dynamic QR or 6-digit code; [bookingId] / [purpose] when scanning for a known booking. */
    suspend fun scan(body: ScanBody): ApiResult<ScanResponse>

    suspend fun verifyAsset(bookingId: String, body: AssetScanBody): ApiResult<MerchantBookingDetail>

    suspend fun uploadPhoto(
        bookingId: String,
        collectPointId: String,
        purpose: QrPurpose,
        condition: AssetCondition,
        jpeg: ByteArray,
    ): ApiResult<MerchantBookingDetail>

    /** [amount] = the server's amount due, sent back EXACTLY (`amountReceived`). */
    suspend fun confirmPayment(bookingId: String, collectPointId: String, amount: MoneyAmount): ApiResult<MerchantBookingDetail>

    /** Merchant half of the pickup: the customer alone then moves the booking to COLLECTED. */
    suspend fun handover(bookingId: String, collectPointId: String, condition: AssetCondition?, kit: KitChecks? = null): ApiResult<MerchantBookingDetail>

    /**
     * Merchant return statement (condition, missing accessories, incident). The customer alone then confirms the return
     * in TANY; the incident is only applied at that moment, and any deposit decision is TANY's.
     */
    suspend fun declareReturn(bookingId: String, body: ReturnBody): ApiResult<MerchantBookingDetail>

    /**
     * « J'ai remis X » — merchant half of the deposit refund; the customer alone confirms the amount received.
     * [expectedAmount] = the server's amount shown on screen, sent back EXACTLY: if TANY changed the decision meanwhile the
     * server refuses (`deposit_amount_changed` + `currentAmount`) and nothing is recorded.
     */
    suspend fun depositRefund(bookingId: String, collectPointId: String, expectedAmount: MoneyAmount): ApiResult<MerchantBookingDetail>

    /**
     * `POST bookings/{id}/incidents` — problem reported to TANY during a pickup (allowed by the server while the booking
     * is RESERVED / COLLECTED; a return problem goes inside the return statement). Never retried.
     */
    suspend fun reportIncident(bookingId: String, body: IncidentBody): ApiResult<MerchantBookingDetail>

    /** `POST bookings/{id}/nudge` — reminds the customer to confirm; throttled server-side (`nudged:false` is not an error). */
    suspend fun nudge(bookingId: String, collectPointId: String): ApiResult<NudgeResponse>
}

class DefaultCollectOperationsRepository(private val api: TanyCollectApi) : CollectOperationsRepository {
    override suspend fun scan(body: ScanBody): ApiResult<ScanResponse> = apiCall { api.scan(body) }

    override suspend fun verifyAsset(bookingId: String, body: AssetScanBody): ApiResult<MerchantBookingDetail> =
        apiCall { api.verifyAsset(bookingId, body).booking }

    override suspend fun uploadPhoto(
        bookingId: String,
        collectPointId: String,
        purpose: QrPurpose,
        condition: AssetCondition,
        jpeg: ByteArray,
    ): ApiResult<MerchantBookingDetail> =
        apiCall { api.uploadPhoto(bookingId, OperationPhotoMultipart.parts(collectPointId, purpose, condition, jpeg)).booking }

    override suspend fun confirmPayment(bookingId: String, collectPointId: String, amount: MoneyAmount): ApiResult<MerchantBookingDetail> =
        apiCall { api.confirmPayment(bookingId, PaymentBody(collectPointId, amount)).booking }

    override suspend fun handover(bookingId: String, collectPointId: String, condition: AssetCondition?, kit: KitChecks?): ApiResult<MerchantBookingDetail> =
        apiCall { api.handover(bookingId, HandoverBody(collectPointId, condition, kit)).booking }

    override suspend fun declareReturn(bookingId: String, body: ReturnBody): ApiResult<MerchantBookingDetail> =
        apiCall { api.declareReturn(bookingId, body).booking }

    override suspend fun depositRefund(bookingId: String, collectPointId: String, expectedAmount: MoneyAmount): ApiResult<MerchantBookingDetail> =
        apiCall { api.depositRefund(bookingId, DepositRefundBody(collectPointId, expectedAmount)).booking }

    override suspend fun reportIncident(bookingId: String, body: IncidentBody): ApiResult<MerchantBookingDetail> =
        apiCall { api.reportIncident(bookingId, body).booking }

    override suspend fun nudge(bookingId: String, collectPointId: String): ApiResult<NudgeResponse> =
        apiCall { api.nudge(bookingId, CollectPointBody(collectPointId)) }
}

/** Multipart layout of `POST bookings/{id}/photos` (contract field names). */
object OperationPhotoMultipart {
    private val JPEG = "image/jpeg".toMediaType()

    fun parts(collectPointId: String, purpose: QrPurpose, condition: AssetCondition, jpeg: ByteArray): List<MultipartBody.Part> = listOf(
        MultipartBody.Part.createFormData("collectPointId", collectPointId),
        MultipartBody.Part.createFormData("purpose", purpose.wire),
        MultipartBody.Part.createFormData("condition", condition.wire),
        MultipartBody.Part.createFormData("photo", "photo.jpg", jpeg.toRequestBody(JPEG)),
    )
}

/** Typed reading of operation errors (codes + structured details; the server's French message is never shown). */
sealed interface CollectOperationError {
    /** 409 qr_* — the customer must show a fresh code (expired, used, unknown, wrong booking / purpose, stale…). */
    data class Qr(val code: ApiErrorCode) : CollectOperationError

    /** 409 asset_mismatch + expected / scanned codes. */
    data class AssetMismatch(val expected: String?, val scanned: String?) : CollectOperationError

    /** 403 wrong_collect_point — booking of another TANY Collect. */
    data object WrongPoint : CollectOperationError

    /** 409 pickup_too_early (+ window). */
    data class TooEarly(val windowStart: String?, val windowEnd: String?) : CollectOperationError

    /** 403 identity_required — the customer's identity is not verified (server gate). */
    data object IdentityRequired : CollectOperationError

    /** 422 photo_error — unusable image, too many photos, wrong order. */
    data object Photo : CollectOperationError

    /** 409 invalid_state — step not allowed now (order, already done, amount mismatch…): re-read the booking. */
    data object NotAllowedNow : CollectOperationError

    /** 429 qr_rate_limited — too many fallback-code attempts. */
    data object RateLimited : CollectOperationError

    /** 409 deposit_amount_changed — TANY changed the amount to hand back; [currentAmount] = the new server amount. */
    data class DepositAmountChanged(val currentAmount: MoneyAmount?) : CollectOperationError

    /** 409 deposit_refund_qr_required — scan the customer's deposit QR first (deferred refund, ≤ 15 min). */
    data object DepositRefundQrRequired : CollectOperationError

    data class Other(val error: ApiError) : CollectOperationError

    companion object {
        private val QR_CODES = setOf(
            ApiErrorCode.QR_ERROR, ApiErrorCode.QR_EXPIRED, ApiErrorCode.QR_ALREADY_USED, ApiErrorCode.QR_NOT_FOUND,
            ApiErrorCode.QR_MALFORMED, ApiErrorCode.QR_WRONG_PURPOSE, ApiErrorCode.QR_WRONG_BOOKING, ApiErrorCode.QR_STALE,
        )

        fun from(error: ApiError): CollectOperationError {
            if (error !is ApiError.Http) return Other(error)
            return when (error.code) {
                in QR_CODES -> Qr(error.code)
                ApiErrorCode.ASSET_MISMATCH -> AssetMismatch(error.detailString("expectedAssetCode"), error.detailString("scannedAssetCode"))
                ApiErrorCode.WRONG_COLLECT_POINT -> WrongPoint
                ApiErrorCode.PICKUP_TOO_EARLY -> TooEarly(error.detailString("pickupWindowStart"), error.detailString("pickupWindowEnd"))
                ApiErrorCode.IDENTITY_REQUIRED -> IdentityRequired
                ApiErrorCode.PHOTO_ERROR -> Photo
                ApiErrorCode.INVALID_STATE -> NotAllowedNow
                ApiErrorCode.QR_RATE_LIMITED -> RateLimited
                ApiErrorCode.DEPOSIT_AMOUNT_CHANGED ->
                    DepositAmountChanged(error.detailString("currentAmount")?.let { runCatching { MoneyAmount.parse(it) }.getOrNull() })
                ApiErrorCode.DEPOSIT_REFUND_QR_REQUIRED -> DepositRefundQrRequired
                else -> Other(error)
            }
        }
    }
}
