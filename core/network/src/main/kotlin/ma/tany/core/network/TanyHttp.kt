package ma.tany.core.network

import ma.tany.core.model.common.TanyJson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object TanyHttp {
    /**
     * Builds the single OkHttp client of the app. Interceptor order: headers → auth → 401 handling → safe retry.
     * Logging is only enabled for DEV and never logs headers or bodies (tokens, OTP codes, personal data).
     */
    fun okHttpClient(
        endpoint: ApiEndpoint,
        tokens: AccessTokenProvider,
        unauthorized: UnauthorizedHandler,
        language: LanguageProvider,
        userAgent: String,
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS) // photo / identity uploads
        .callTimeout(90, TimeUnit.SECONDS)
        .addInterceptor(ClientHeadersInterceptor(language, userAgent))
        .addInterceptor(AuthInterceptor(tokens))
        .addInterceptor(UnauthorizedInterceptor(unauthorized))
        .addInterceptor(SafeRetryInterceptor())
        .apply {
            if (endpoint.environment == ApiEnvironment.DEV) {
                addInterceptor(
                    HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                        redactHeader(AuthInterceptor.AUTHORIZATION)
                    },
                )
            }
        }
        .build()

    fun retrofit(endpoint: ApiEndpoint, client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(endpoint.apiRoot)
        .client(client)
        .addConverterFactory(TanyJson.asConverterFactory("application/json; charset=UTF-8".toMediaType()))
        .build()
}
