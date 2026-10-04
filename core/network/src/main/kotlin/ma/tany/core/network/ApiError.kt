package ma.tany.core.network

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import ma.tany.core.model.common.ApiErrorCode

/**
 * Semantic API failure. Codes are the exact canonical backend codes — never translated here.
 * Features map [ApiError] to localized copy (the French `message` is a last-resort fallback only).
 */
sealed interface ApiError {
    /** Backend answered with the `{ error: { code, message, …details } }` envelope (or a bare HTTP error). */
    data class Http(
        val status: Int,
        val code: ApiErrorCode,
        /** Raw wire code — kept so an unknown (newer) code is still visible in logs. */
        val rawCode: String?,
        /** French prose, fallback only. */
        val message: String?,
        /** Extra fields of the error object (`reason`, `pickupWindowStart`, restriction fields…). */
        val details: JsonObject = JsonObject(emptyMap()),
    ) : ApiError {
        fun detailString(key: String): String? = (details[key] as? JsonPrimitive)?.contentOrNull

        fun detailInt(key: String): Int? = (details[key] as? JsonPrimitive)?.intOrNull
    }

    /** 401: the session is missing/expired. The session has already been cleared by the interceptor. */
    data object Unauthorized : ApiError

    /** No connectivity / timeout / connection reset. A business POST is never replayed automatically. */
    data class Network(val cause: Throwable) : ApiError

    /** The response did not match the contract (model drift). */
    data class Decoding(val cause: Throwable) : ApiError

    data class Unexpected(val cause: Throwable) : ApiError
}

class ApiException(val error: ApiError) : Exception(error.toString())

sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>

    data class Failure(val error: ApiError) : ApiResult<Nothing>

    fun getOrNull(): T? = (this as? Success)?.value

    fun errorOrNull(): ApiError? = (this as? Failure)?.error
}

inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(value))
    is ApiResult.Failure -> this
}
