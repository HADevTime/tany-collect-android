package ma.tany.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EnvironmentValidatorTest {
    @Test
    fun prodOnlyTargetsProduction() {
        assertEquals("https://tany.ma/api/mobile/v1/", ApiEndpoint.of(ApiEnvironment.PROD, "https://tany.ma/").apiRoot)
        assertThrows(IllegalArgumentException::class.java) { ApiEndpoint.of(ApiEnvironment.PROD, "https://staging.tany.ma") }
        assertThrows(IllegalArgumentException::class.java) { ApiEndpoint.of(ApiEnvironment.PROD, "http://tany.ma") }
    }

    @Test
    fun stagingOnlyTargetsStaging() {
        ApiEndpoint.of(ApiEnvironment.STAGING, "https://staging.tany.ma")
        assertThrows(IllegalArgumentException::class.java) { ApiEndpoint.of(ApiEnvironment.STAGING, "https://tany.ma") }
    }

    @Test
    fun devNeverTargetsProduction() {
        ApiEndpoint.of(ApiEnvironment.DEV, "http://10.0.2.2:3000")
        assertThrows(IllegalArgumentException::class.java) { ApiEndpoint.of(ApiEnvironment.DEV, "https://tany.ma") }
        assertThrows(IllegalArgumentException::class.java) { ApiEndpoint.of(ApiEnvironment.DEV, "https://www.tany.ma") }
        assertThrows(IllegalArgumentException::class.java) { ApiEndpoint.of(ApiEnvironment.DEV, "http://10.0.2.2:3000/api") }
    }

    @Test
    fun resolvesRelativeMediaPaths() {
        val endpoint = ApiEndpoint.of(ApiEnvironment.STAGING, "https://staging.tany.ma")
        assertEquals("https://staging.tany.ma/images/p.png", endpoint.resolveMedia("/images/p.png"))
        assertEquals(null, endpoint.resolveMedia(null))
    }
}
