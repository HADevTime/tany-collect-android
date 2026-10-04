package ma.tany.core.network

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import ma.tany.core.model.common.ApiErrorCode
import ma.tany.core.model.common.TanyJson
import retrofit2.HttpException
import java.io.IOException

/**
 * Runs one API call and maps every failure to an [ApiError].
 * Coroutine cancellation is always rethrown (cancellation-safe); nothing is retried here
 * (safe GET retries live in [SafeRetryInterceptor]).
 */
suspend fun <T> apiCall(block: suspend () -> T): ApiResult<T> = try {
    ApiResult.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: HttpException) {
    ApiResult.Failure(ErrorEnvelopeParser.fromHttp(e.code(), e.response()?.errorBody()?.string()))
} catch (e: IOException) {
    ApiResult.Failure(ApiError.Network(e))
} catch (e: SerializationException) {
    ApiResult.Failure(ApiError.Decoding(e))
} catch (e: IllegalArgumentException) {
    // kotlinx.serialization reports some contract mismatches as IllegalArgumentException.
    ApiResult.Failure(ApiError.Decoding(e))
} catch (e: ApiException) {
    ApiResult.Failure(e.error)
}

object ErrorEnvelopeParser {
    fun fromHttp(status: Int, body: String?): ApiError {
        val error = body?.let { runCatching { TanyJson.parseToJsonElement(it).jsonObject["error"] as? JsonObject }.getOrNull() }
        val rawCode = (error?.get("code") as? JsonPrimitive)?.contentOrNull
        if (status == 401) return ApiError.Unauthorized
        val code = ApiErrorCode.Serializer.fromWire(rawCode)
        return ApiError.Http(
            status = status,
            code = if (code == ApiErrorCode.UNKNOWN && rawCode == null && status >= 500) ApiErrorCode.SERVER_ERROR else code,
            rawCode = rawCode,
            message = (error?.get("message") as? JsonPrimitive)?.contentOrNull,
            details = JsonObject(error.orEmpty().filterKeys { it != "code" && it != "message" }),
        )
    }
}
