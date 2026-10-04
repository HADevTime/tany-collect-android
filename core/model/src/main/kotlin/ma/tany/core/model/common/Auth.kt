package ma.tany.core.model.common

import kotlinx.serialization.Serializable

/** `POST /auth/otp/request` (shared by TANY and TANY Collect). The backend normalizes the phone number. */
@Serializable
data class OtpRequestBody(val phone: String)

@Serializable
data class OtpRequestResponse(
    /** Normalized E.164 phone. */
    val phone: String,
    val codeLength: Int,
    val expiresInSeconds: Int,
    /** Advisory only. */
    val resendAfterSeconds: Int,
    /** Only returned outside production (DEV/STAGING); never present in PROD. */
    val devCode: String? = null,
)

@Serializable
data class OtpVerifyBody(val phone: String, val code: String)

@Serializable
data class LogoutBody(val deviceToken: String? = null)
