package ma.tany.core.network

import ma.tany.core.model.collect.CollectPointBody
import ma.tany.core.model.collect.RevenueOverview
import ma.tany.core.model.collect.SettlementCollection
import ma.tany.core.model.collect.SettlementConfirmBody
import ma.tany.core.model.collect.SettlementDisputeBody
import ma.tany.core.model.collect.SettlementOverview
import ma.tany.core.model.collect.SettlementQr
import ma.tany.core.model.common.ApiErrorCode
import ma.tany.core.model.common.NotificationReadResponse
import ma.tany.core.model.common.NotificationsPage
import ma.tany.core.model.common.UnreadCount

/**
 * Revenue (ESTIMATED earnings, read-only) and TANY settlement of the active point. Revenue ≠ settlement ≠ deposits:
 * three separate server computations, never mixed by the app. Money moved is never asserted by the app — the agent
 * declares "received X", the merchant confirms "handed over X" with EXACTLY the agent's declared amount.
 */
interface CollectBusinessRepository {
    /** [period] = `YYYY-MM` from the server's `periods` / `previousKey` / `nextKey` (null = current). */
    suspend fun revenue(pointId: String, period: String? = null): ApiResult<RevenueOverview>

    suspend fun settlement(pointId: String): ApiResult<SettlementOverview>

    /** Fresh settlement QR for the agent on site. Not idempotent (each call issues a token): user-triggered only. */
    suspend fun settlementQr(pointId: String, collectionId: String): ApiResult<SettlementQr>

    /** « J'ai remis X »: the agent's declared amount (cents) and confirmation id, sent back EXACTLY. */
    suspend fun confirmHandoff(pointId: String, collection: SettlementCollection): ApiResult<SettlementOverview>

    /** « Ce n'est pas le montant remis » — TANY settles the discrepancy. */
    suspend fun disputeHandoff(pointId: String, collectionId: String, reason: String?): ApiResult<SettlementOverview>
}

class DefaultCollectBusinessRepository(private val api: TanyCollectApi) : CollectBusinessRepository {
    override suspend fun revenue(pointId: String, period: String?) = apiCall { api.revenue(pointId, period) }

    override suspend fun settlement(pointId: String) = apiCall { api.settlement(pointId) }

    override suspend fun settlementQr(pointId: String, collectionId: String) = apiCall { api.settlementQr(pointId, collectionId) }

    override suspend fun confirmHandoff(pointId: String, collection: SettlementCollection): ApiResult<SettlementOverview> {
        val cents = collection.agentConfirmedAmountCents
        val confirmationId = collection.agentConfirmationId
        if (cents == null || confirmationId == null) {
            return ApiResult.Failure(ApiError.Http(409, ApiErrorCode.INVALID_STATE, "invalid_state", null))
        }
        return apiCall { api.settlementConfirm(pointId, collection.id, SettlementConfirmBody(cents, confirmationId)) }
    }

    override suspend fun disputeHandoff(pointId: String, collectionId: String, reason: String?) =
        apiCall { api.settlementDispute(pointId, collectionId, SettlementDisputeBody(reason?.trim()?.take(DISPUTE_REASON_MAX)?.ifBlank { null })) }

    companion object {
        const val DISPUTE_REASON_MAX = 300
    }
}

/** Typed settlement refusals (codes only). */
sealed interface SettlementActionError {
    /** 409 confirmation_stale — the agent changed the declared amount; re-read and check it again. */
    data object Stale : SettlementActionError

    /** 409 invalid_state / nothing_due — nothing to confirm or show now (already done, cancelled…). */
    data object NotAllowedNow : SettlementActionError

    data class Other(val error: ApiError) : SettlementActionError

    companion object {
        fun from(error: ApiError): SettlementActionError = when ((error as? ApiError.Http)?.code) {
            ApiErrorCode.CONFIRMATION_STALE -> Stale
            ApiErrorCode.INVALID_STATE, ApiErrorCode.NOTHING_DUE -> NotAllowedNow
            else -> Other(error)
        }
    }
}

/** Notification centre of the ACTIVE point (projection only: a notification never executes an action). */
interface CollectNotificationRepository {
    suspend fun page(pointId: String, cursor: String? = null): ApiResult<NotificationsPage>

    suspend fun unreadCount(pointId: String): ApiResult<UnreadCount>

    suspend fun markRead(pointId: String, id: String): ApiResult<NotificationReadResponse>

    suspend fun markAllRead(pointId: String): ApiResult<NotificationReadResponse>
}

class DefaultCollectNotificationRepository(private val api: TanyCollectApi) : CollectNotificationRepository {
    override suspend fun page(pointId: String, cursor: String?) = apiCall { api.notifications(pointId, PAGE_SIZE, cursor) }

    override suspend fun unreadCount(pointId: String) = apiCall { api.unreadCount(pointId) }

    override suspend fun markRead(pointId: String, id: String) = apiCall { api.markNotificationRead(id, CollectPointBody(pointId)) }

    override suspend fun markAllRead(pointId: String) = apiCall { api.markAllNotificationsRead(CollectPointBody(pointId)) }

    private companion object {
        const val PAGE_SIZE = 30
    }
}
