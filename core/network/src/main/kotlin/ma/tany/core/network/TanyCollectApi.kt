package ma.tany.core.network

import ma.tany.core.model.collect.ActivityResponse
import ma.tany.core.model.collect.AssetDetailResponse
import ma.tany.core.model.collect.AssetScanBody
import ma.tany.core.model.collect.AssetsResponse
import ma.tany.core.model.collect.CollectAuthResponse
import ma.tany.core.model.collect.CollectMe
import ma.tany.core.model.collect.CollectPointBody
import ma.tany.core.model.collect.DepositRefundBody
import ma.tany.core.model.collect.HandoverBody
import ma.tany.core.model.collect.IncidentBody
import ma.tany.core.model.collect.IncidentsResponse
import ma.tany.core.model.collect.MerchantBookingResponse
import ma.tany.core.model.collect.MerchantEventBody
import ma.tany.core.model.collect.NudgeResponse
import ma.tany.core.model.collect.PaymentBody
import ma.tany.core.model.collect.PhotoUploadResponse
import ma.tany.core.model.collect.ReturnBody
import ma.tany.core.model.collect.RevenueOverview
import ma.tany.core.model.collect.ScanBody
import ma.tany.core.model.collect.ScanResponse
import ma.tany.core.model.collect.SettlementOverview
import ma.tany.core.model.common.AppConfig
import ma.tany.core.model.common.DeviceRegistrationBody
import ma.tany.core.model.common.DeviceTokenBody
import ma.tany.core.model.common.LogoutBody
import ma.tany.core.model.common.NotificationReadResponse
import ma.tany.core.model.common.NotificationsPage
import ma.tany.core.model.common.OkResponse
import ma.tany.core.model.common.OtpRequestBody
import ma.tany.core.model.common.OtpRequestResponse
import ma.tany.core.model.common.OtpVerifyBody
import ma.tany.core.model.common.UnreadCount
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * TANY Collect endpoints — `/api/mobile/v1` + `/collect/…` (API_CONTRACT_V1 § 1a, 1c).
 * Every `bookings/{id}` route carries `collectPointId`; the server re-checks the merchant's scope
 * (`wrong_collect_point` / `forbidden`). No mutation is ever retried automatically.
 * Sessions are `COLLECT_APP` sessions (MERCHANT or ADMIN only): never valid for TANY Client nor for the backoffice.
 * NOT exposed yet: multipart `bookings/{id}/photos` (arrives with the pickup slice), settlement statement/collection details
 * and QR/confirm/dispute (settlement slice, gap A-4).
 */
interface TanyCollectApi {
    @GET("config")
    suspend fun config(): AppConfig

    // — Auth (OTP requested through the shared client route) —
    @POST("auth/otp/request")
    suspend fun requestOtp(@Body body: OtpRequestBody): OtpRequestResponse

    @POST("collect/auth/otp/verify")
    suspend fun verifyOtp(@Body body: OtpVerifyBody): CollectAuthResponse

    @POST("auth/logout")
    suspend fun logout(@Body body: LogoutBody): OkResponse

    @GET("collect/me")
    suspend fun me(): CollectMe

    // — Operations of the active point —
    @GET("collect/points/{pointId}/today")
    suspend fun today(@Path("pointId") pointId: String): ma.tany.core.model.collect.TodayResponse

    @GET("collect/points/{pointId}/activity")
    suspend fun activity(
        @Path("pointId") pointId: String,
        @Query("q") query: String? = null,
        @Query("limit") limit: Int? = null,
    ): ActivityResponse

    @GET("collect/points/{pointId}/incidents")
    suspend fun incidents(@Path("pointId") pointId: String): IncidentsResponse

    @GET("collect/points/{pointId}/revenue")
    suspend fun revenue(@Path("pointId") pointId: String, @Query("period") period: String? = null): RevenueOverview

    @GET("collect/points/{pointId}/settlement")
    suspend fun settlement(@Path("pointId") pointId: String): SettlementOverview

    @GET("collect/points/{pointId}/assets")
    suspend fun assets(
        @Path("pointId") pointId: String,
        @Query("q") query: String? = null,
        @Query("filter") filter: String? = null,
    ): AssetsResponse

    @GET("collect/points/{pointId}/assets/lookup")
    suspend fun assetLookup(@Path("pointId") pointId: String, @Query("code") code: String): AssetDetailResponse

    @GET("collect/points/{pointId}/assets/{assetId}")
    suspend fun asset(@Path("pointId") pointId: String, @Path("assetId") assetId: String): AssetDetailResponse

    // — Booking operations (all scoped by collectPointId) —
    @POST("collect/scan")
    suspend fun scan(@Body body: ScanBody): ScanResponse

    @GET("collect/bookings/{id}")
    suspend fun booking(@Path("id") id: String, @Query("collectPointId") collectPointId: String): MerchantBookingResponse

    @POST("collect/bookings/{id}/asset")
    suspend fun verifyAsset(@Path("id") id: String, @Body body: AssetScanBody): MerchantBookingResponse

    /** Multipart: `photo` (JPEG), `purpose` PICKUP|RETURN, `condition` good|issue_reported, `collectPointId` (1–3 per gesture). */
    @Multipart
    @POST("collect/bookings/{id}/photos")
    suspend fun uploadPhoto(@Path("id") id: String, @Part parts: List<MultipartBody.Part>): PhotoUploadResponse

    @POST("collect/bookings/{id}/payment")
    suspend fun confirmPayment(@Path("id") id: String, @Body body: PaymentBody): MerchantBookingResponse

    @POST("collect/bookings/{id}/handover")
    suspend fun handover(@Path("id") id: String, @Body body: HandoverBody): MerchantBookingResponse

    @POST("collect/bookings/{id}/return")
    suspend fun declareReturn(@Path("id") id: String, @Body body: ReturnBody): MerchantBookingResponse

    /** « J'ai remis X » — `expectedAmount` = amount shown; re-evaluated server-side ⇒ 409 `deposit_amount_changed`. */
    @POST("collect/bookings/{id}/deposit-refund")
    suspend fun depositRefund(@Path("id") id: String, @Body body: DepositRefundBody): MerchantBookingResponse

    @POST("collect/bookings/{id}/incidents")
    suspend fun reportIncident(@Path("id") id: String, @Body body: IncidentBody): MerchantBookingResponse

    /** Throttled server-side (60 s): `nudged:false` is not an error. */
    @POST("collect/bookings/{id}/nudge")
    suspend fun nudge(@Path("id") id: String, @Body body: CollectPointBody): NudgeResponse

    @POST("collect/bookings/{id}/events")
    suspend fun reportEvent(@Path("id") id: String, @Body body: MerchantEventBody): OkResponse

    // — Push (FCM, `platform:"android"` ⇒ stored as android-collect) —
    @POST("collect/devices")
    suspend fun registerDevice(@Body body: DeviceRegistrationBody): OkResponse

    @HTTP(method = "DELETE", path = "collect/devices", hasBody = true)
    suspend fun unregisterDevice(@Body body: DeviceTokenBody): OkResponse

    // — Notifications of the active point —
    @GET("collect/notifications")
    suspend fun notifications(
        @Query("collectPointId") collectPointId: String,
        @Query("limit") limit: Int? = null,
        @Query("cursor") cursor: String? = null,
    ): NotificationsPage

    @GET("collect/notifications/unread-count")
    suspend fun unreadCount(@Query("collectPointId") collectPointId: String): UnreadCount

    @POST("collect/notifications/{id}/read")
    suspend fun markNotificationRead(@Path("id") id: String, @Body body: CollectPointBody): NotificationReadResponse

    @POST("collect/notifications/read-all")
    suspend fun markAllNotificationsRead(@Body body: CollectPointBody): NotificationReadResponse
}
