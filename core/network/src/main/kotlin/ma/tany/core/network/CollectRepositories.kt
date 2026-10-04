package ma.tany.core.network

import kotlinx.coroutines.CancellationException
import ma.tany.core.model.collect.ActivityResponse
import ma.tany.core.model.collect.AssetsResponse
import ma.tany.core.model.collect.CollectAuthResponse
import ma.tany.core.model.collect.CollectMe
import ma.tany.core.model.collect.CollectRole
import ma.tany.core.model.collect.MerchantBookingDetail
import ma.tany.core.model.collect.TodayResponse
import ma.tany.core.model.common.ApiErrorCode
import ma.tany.core.model.common.LogoutBody
import ma.tany.core.model.common.OtpRequestBody
import ma.tany.core.model.common.OtpRequestResponse
import ma.tany.core.model.common.OtpVerifyBody

/**
 * Merchant authentication: shared OTP request + Collect verify. Canonical backend policy (auth hardening):
 * - the token is a `COLLECT_APP` session: valid ONLY for the `/collect/…` routes — never for TANY Client and NEVER for the
 *   backoffice (`/admin`, `/agent` require a `BACKOFFICE` email + password session), even for an ADMIN account;
 * - roles accepted: MERCHANT (attached to an active point) and ADMIN (multi-point); a customer, a collection agent,
 *   a blocked account or an account without an active point gets `account_not_allowed`;
 * - no OTP demo code (`devCode`) is ever returned for ADMIN / agent numbers, even in STAGING.
 * The app mirrors this defensively: a session is only stored for MERCHANT / ADMIN, and nothing in the app
 * links to, opens or implies backoffice access. No profile step, no email/password login in this app.
 */
interface CollectAuthRepository {
    suspend fun requestOtp(phone: String): ApiResult<OtpRequestResponse>

    /** On success the session is stored; the caller picks the active point from the returned points. */
    suspend fun verifyOtp(phone: String, code: String): ApiResult<CollectAuthResponse>

    suspend fun logout()
}

class DefaultCollectAuthRepository(
    private val api: TanyCollectApi,
    private val session: SessionManager,
    private val push: PushTokenRegistrar,
) : CollectAuthRepository {
    override suspend fun requestOtp(phone: String): ApiResult<OtpRequestResponse> = apiCall { api.requestOtp(OtpRequestBody(phone.trim())) }

    override suspend fun verifyOtp(phone: String, code: String): ApiResult<CollectAuthResponse> = apiCall {
        val response = api.verifyOtp(OtpVerifyBody(phone.trim(), code.trim()))
        if (response.user.role != CollectRole.MERCHANT && response.user.role != CollectRole.ADMIN) {
            // Never expected (the backend refuses other roles); never keep a token for an unknown role.
            throw ApiException(ApiError.Http(403, ApiErrorCode.ACCOUNT_NOT_ALLOWED, "account_not_allowed", null))
        }
        session.signIn(StoredSession(response.token, response.user.id))
        push.register()
        response
    }

    override suspend fun logout() {
        try {
            runCatching { push.unregister() }
            apiCall { api.logout(LogoutBody()) }
        } catch (e: CancellationException) {
            session.signOut()
            throw e
        }
        session.signOut()
    }
}

/** Read access for the foundation screens; operation flows (scan, payment, handover, return) come per slice. */
interface CollectRepository {
    suspend fun me(): ApiResult<CollectMe>

    suspend fun today(pointId: String): ApiResult<TodayResponse>

    suspend fun activity(pointId: String, query: String? = null): ApiResult<ActivityResponse>

    suspend fun booking(bookingId: String, pointId: String): ApiResult<MerchantBookingDetail>

    /** `enabled:false` when the equipment module is OFF. */
    suspend fun assets(pointId: String): ApiResult<AssetsResponse>
}

class DefaultCollectRepository(private val api: TanyCollectApi) : CollectRepository {
    override suspend fun me() = apiCall { api.me() }

    override suspend fun today(pointId: String) = apiCall { api.today(pointId) }

    override suspend fun activity(pointId: String, query: String?) = apiCall { api.activity(pointId, query?.take(60)) }

    override suspend fun booking(bookingId: String, pointId: String) = apiCall { api.booking(bookingId, pointId).booking }

    override suspend fun assets(pointId: String) = apiCall { api.assets(pointId) }
}
