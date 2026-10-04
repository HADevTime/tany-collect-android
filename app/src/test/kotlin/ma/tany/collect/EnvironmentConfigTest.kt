package ma.tany.collect

import ma.tany.collect.core.AppEnvironment
import ma.tany.core.network.ApiEnvironment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runs for EVERY variant (testDebugUnitTest, testStagingUnitTest, testReleaseUnitTest):
 * each build type is bound to exactly one environment and origin; PROD never points to STAGING.
 */
class EnvironmentConfigTest {
    @Test
    fun variantTargetsItsOwnEnvironment() {
        val endpoint = AppEnvironment.endpoint
        when (BuildConfig.BUILD_TYPE) {
            "release" -> {
                assertEquals(ApiEnvironment.PROD, endpoint.environment)
                assertEquals("https://tany.ma", endpoint.baseUrl)
                assertEquals("ma.tany.collect", BuildConfig.APPLICATION_ID)
            }
            "staging" -> {
                assertEquals(ApiEnvironment.STAGING, endpoint.environment)
                assertEquals("https://staging.tany.ma", endpoint.baseUrl)
                assertEquals("ma.tany.collect.staging", BuildConfig.APPLICATION_ID)
            }
            "debug" -> {
                assertEquals(ApiEnvironment.DEV, endpoint.environment)
                assertTrue(!endpoint.baseUrl.contains("tany.ma"))
                assertEquals("ma.tany.collect.dev", BuildConfig.APPLICATION_ID)
            }
            else -> error("Unexpected build type ${BuildConfig.BUILD_TYPE}")
        }
    }

    @Test
    fun devCodeIsNeverShownInProduction() {
        assertEquals(BuildConfig.BUILD_TYPE != "release", AppEnvironment.mayShowDevCode)
    }

    @Test
    fun internalToolsNeverShipInProduction() {
        assertEquals(BuildConfig.BUILD_TYPE != "release", ma.tany.collect.internal.InternalTools.enabled)
    }
}
