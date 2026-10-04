package ma.tany.collect.core

import ma.tany.collect.BuildConfig
import ma.tany.core.network.ApiEndpoint
import ma.tany.core.network.ApiEnvironment

/** The variant's environment, validated at startup (a PROD build pointing elsewhere crashes immediately). */
object AppEnvironment {
    val endpoint: ApiEndpoint by lazy {
        ApiEndpoint.of(ApiEnvironment.parse(BuildConfig.TANY_ENVIRONMENT), BuildConfig.TANY_API_BASE_URL)
    }

    val isProduction: Boolean get() = endpoint.environment == ApiEnvironment.PROD

    /** OTP `devCode` is only ever displayed outside PROD. */
    val mayShowDevCode: Boolean get() = !isProduction

    val userAgent: String get() = "TANY-Android/${BuildConfig.VERSION_NAME} (collect; ${BuildConfig.TANY_ENVIRONMENT})"
}
