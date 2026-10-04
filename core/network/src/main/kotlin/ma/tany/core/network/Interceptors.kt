package ma.tany.core.network

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/** Supplies the current session token from memory (never blocks, never logs). */
fun interface AccessTokenProvider {
    fun currentToken(): String?
}

/** Called when the backend rejects the token that was sent (HTTP 401). */
fun interface UnauthorizedHandler {
    fun onUnauthorized(rejectedToken: String)
}

/** Current app language for `Accept-Language` (`fr` | `en` | `ar`). */
fun interface LanguageProvider {
    fun languageTag(): String
}

/** Adds `Authorization: Bearer <token>` when a session exists. */
class AuthInterceptor(private val tokens: AccessTokenProvider) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = tokens.currentToken()
        if (token.isNullOrEmpty() || request.header(AUTHORIZATION) != null) return chain.proceed(request)
        return chain.proceed(request.newBuilder().header(AUTHORIZATION, "Bearer $token").build())
    }

    companion object {
        const val AUTHORIZATION = "Authorization"
    }
}

/**
 * On 401 for a request that carried a token, tells the session layer to drop THAT token (a newer session obtained
 * meanwhile is never cleared). Contract: no refresh token — 401 ⇒ sign in again with OTP.
 */
class UnauthorizedInterceptor(private val handler: UnauthorizedHandler) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (response.code == 401) {
            request.header(AuthInterceptor.AUTHORIZATION)
                ?.removePrefix("Bearer ")
                ?.takeIf { it.isNotEmpty() }
                ?.let(handler::onUnauthorized)
        }
        return response
    }
}

/**
 * `Accept-Language` (localized catalog + notifications; error messages stay French) and a descriptive User-Agent.
 * The backend does not consume a request-id header today (contract § 5.13), so none is sent.
 */
class ClientHeadersInterceptor(
    private val language: LanguageProvider,
    private val userAgent: String,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(
        chain.request().newBuilder()
            .header("Accept-Language", language.languageTag())
            .header("User-Agent", userAgent)
            .header("Accept", "application/json")
            .build(),
    )
}

/**
 * Retries ONLY idempotent reads (GET/HEAD) on I/O failure or 502/503/504.
 * Business mutations (bookings, payments, photos, confirmations) are never replayed: the backend has no
 * idempotency key (contract § 2) — after a failure the app re-reads server state instead.
 */
class SafeRetryInterceptor(private val maxRetries: Int = 2) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.method != "GET" && request.method != "HEAD") return chain.proceed(request)
        var attempt = 0
        while (true) {
            try {
                val response = chain.proceed(request)
                if (response.code in RETRYABLE_STATUS && attempt < maxRetries && !chain.call().isCanceled()) {
                    response.close()
                    attempt++
                    continue
                }
                return response
            } catch (e: IOException) {
                if (attempt >= maxRetries || chain.call().isCanceled()) throw e
                attempt++
            }
        }
    }

    private companion object {
        val RETRYABLE_STATUS = setOf(502, 503, 504)
    }
}
