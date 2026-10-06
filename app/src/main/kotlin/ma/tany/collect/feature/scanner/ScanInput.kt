package ma.tany.collect.feature.scanner

import ma.tany.core.model.collect.AssetScanBody
import ma.tany.core.model.collect.ScanBody
import ma.tany.core.model.common.QrPurpose

/** What the scanner is currently expecting (decided by the operation flow, never guessed from the payload). */
enum class ScanTarget {
    /** Customer dynamic QR (`<tokenId>.<hmac>`, ~90 s, single use) or its 6-digit fallback. */
    BOOKING_QR,

    /** Physical Asset label: the raw asset code. */
    ASSET_LABEL,
}

/** A captured input. Payloads are OPAQUE: never parsed, validated or decoded client-side — the server decides. */
sealed interface ScanInput {
    data class Qr(val payload: String) : ScanInput

    data class ShortCode(val code: String) : ScanInput
}

object ScanInputs {
    const val SHORT_CODE_LENGTH = 6
    private const val MAX_QR_LENGTH = 512
    const val MAX_ASSET_CODE_LENGTH = 64

    /** Camera result → booking QR input (trimmed; over-long values are rejected before reaching the API). */
    fun fromCamera(raw: String): ScanInput.Qr? = raw.trim().takeIf { it.isNotEmpty() && it.length <= MAX_QR_LENGTH }?.let(ScanInput::Qr)

    /** Manual fallback: exactly 6 digits (spaces / dashes typed by the merchant are ignored). */
    fun shortCodeOrNull(text: String): ScanInput.ShortCode? =
        text.filter(Char::isDigit).takeIf { it.length == SHORT_CODE_LENGTH }?.let(ScanInput::ShortCode)

    fun toScanBody(collectPointId: String, input: ScanInput, bookingId: String? = null, purpose: QrPurpose? = null): ScanBody =
        when (input) {
            is ScanInput.Qr -> ScanBody(collectPointId = collectPointId, qrPayload = input.payload, bookingId = bookingId, purpose = purpose)
            is ScanInput.ShortCode -> ScanBody(collectPointId = collectPointId, shortCode = input.code, bookingId = bookingId, purpose = purpose)
        }

    /** Asset label QR content = raw asset code (`asset_mismatch` is decided by the server). */
    fun toAssetBody(collectPointId: String, purpose: QrPurpose, raw: String): AssetScanBody? =
        raw.trim().takeIf { it.isNotEmpty() && it.length <= MAX_ASSET_CODE_LENGTH }?.let { AssetScanBody(collectPointId, purpose, it) }
}

/** Suppresses repeated camera detections of the same code (one emission per code per [windowMs]). */
class ScanDebouncer(private val windowMs: Long = 2_500, private val clock: () -> Long = System::currentTimeMillis) {
    private var last: String? = null
    private var lastAt = 0L

    @Synchronized
    fun accept(value: String): Boolean {
        val now = clock()
        if (value == last && now - lastAt < windowMs) return false
        last = value
        lastAt = now
        return true
    }
}

/**
 * Camera gate: ONE code per arming. The gate closes as soon as a code is accepted and stays closed while the caller is
 * not [active] (request in flight, refusal on screen, manual entry open); it re-arms on the inactive → active edge and
 * ignores the code that was just refused for [cooldownMs] after re-arming (the same QR may still be in front of the
 * lens). Prevents a loop of POSTs with a rejected code — nothing is ever retried automatically.
 */
class ScanGate(private val cooldownMs: Long = 1_500, private val clock: () -> Long = System::currentTimeMillis) {
    private var active = true
    private var armed = true
    private var lastCode: String? = null
    private var rearmedAt = Long.MIN_VALUE / 2

    @Synchronized
    fun setActive(value: Boolean) {
        if (value && !active) {
            armed = true
            rearmedAt = clock()
        }
        active = value
    }

    @Synchronized
    fun accept(code: String): Boolean {
        if (!active || !armed) return false
        if (code == lastCode && clock() - rearmedAt < cooldownMs) return false
        armed = false
        lastCode = code
        return true
    }
}
