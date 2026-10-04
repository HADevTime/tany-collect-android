package ma.tany.core.network

import kotlinx.coroutines.CancellationException
import ma.tany.core.model.collect.ActivityResponse
import ma.tany.core.model.collect.AssetsResponse
import ma.tany.core.model.collect.CollectAuthResponse
import ma.tany.core.model.collect.CollectMe
import ma.tany.core.model.collect.MerchantBookingDetail
import ma.tany.core.model.collect.TodayResponse
import ma.tany.core.model.common.LogoutBody
import ma.tany.core.model.common.OtpRequestBody
import ma.tany.core.model.common.OtpRequestResponse
import ma.tany.core.model.common.OtpVerifyBody

/**
 * Merchant authentication: shared OTP request + Collect verify (MERCHANT or ADMIN only — a customer number gets
 * `account_not_allowed`). No profile step on Collect. No Admin email/password login in this app.
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
        session.signIn(StoredSession(response.token, response.user.id))
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
