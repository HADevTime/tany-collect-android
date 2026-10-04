package ma.tany.core.network

import java.net.URI

/**
 * TANY environments. The base URL of each Android variant is fixed at build time (BuildConfig) and validated here
 * AND by a unit test per variant, so a PROD build can never point to STAGING (or the reverse).
 */
enum class ApiEnvironment {
    DEV,
    STAGING,
    PROD,
    ;

    companion object {
        const val PRODUCTION_BASE_URL = "https://tany.ma"
        const val STAGING_BASE_URL = "https://staging.tany.ma"

        fun parse(value: String): ApiEnvironment =
            entries.firstOrNull { it.name == value } ?: throw IllegalArgumentException("Unknown TANY environment '$value'")
    }
}

/** Validated API endpoint: [baseUrl] has no trailing slash; the mobile API lives under `/api/mobile/v1/`. */
class ApiEndpoint private constructor(val environment: ApiEnvironment, val baseUrl: String) {
    val apiRoot: String get() = "$baseUrl/api/mobile/v1/"

    /** Media paths are RELATIVE (`/images/…`, `/api/photos/…`): resolve them against the base URL. */
    fun resolveMedia(path: String?): String? = when {
        path.isNullOrBlank() -> null
        path.startsWith("https://") || path.startsWith("http://") -> path
        path.startsWith("/") -> baseUrl + path
        else -> "$baseUrl/$path"
    }

    override fun toString(): String = "ApiEndpoint($environment, $baseUrl)"

    companion object {
        fun of(environment: ApiEnvironment, baseUrl: String): ApiEndpoint {
            val normalized = baseUrl.trim().trimEnd('/')
            EnvironmentValidator.validate(environment, normalized)
            return ApiEndpoint(environment, normalized)
        }
    }
}

object EnvironmentValidator {
    private val productionHosts = setOf("tany.ma", "www.tany.ma")

    fun validate(environment: ApiEnvironment, baseUrl: String) {
        val uri = runCatching { URI(baseUrl) }.getOrNull()
        require(uri != null && uri.host != null && (uri.scheme == "https" || uri.scheme == "http")) {
            "Invalid API base URL for $environment: '$baseUrl'"
        }
        require(uri.path.isNullOrEmpty() && uri.query == null) { "API base URL must be an origin only: '$baseUrl'" }
        when (environment) {
            ApiEnvironment.PROD -> require(baseUrl == ApiEnvironment.PRODUCTION_BASE_URL) {
                "PROD must target ${ApiEnvironment.PRODUCTION_BASE_URL}, got '$baseUrl'"
            }
            ApiEnvironment.STAGING -> require(baseUrl == ApiEnvironment.STAGING_BASE_URL) {
                "STAGING must target ${ApiEnvironment.STAGING_BASE_URL}, got '$baseUrl'"
            }
            ApiEnvironment.DEV -> require(uri.host !in productionHosts) {
                "DEV must never target production ('$baseUrl')"
            }
        }
    }
}
