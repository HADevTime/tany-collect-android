package ma.tany.core.network

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import ma.tany.core.model.collect.KitCheck
import ma.tany.core.model.collect.KitCheckState
import ma.tany.core.model.collect.KitChecks
import ma.tany.core.model.common.AssetCondition
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/** Rental kit V1: `POST collect/bookings/{id}/handover` wire body (historical, all handed over, one difference). */
class RentalKitOperationsTest {
    private lateinit var server: MockWebServer

    private fun fixture(name: String) = File("../model/src/test/resources/fixtures/real/$name.json").readText()

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() = server.shutdown()

    private fun ok() = MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(fixture("booking_detail_kit_pickup"))

    @Test
    fun handoverSendsTheKitDifferencesOnly() = runTest(UnconfinedTestDispatcher()) {
        val stack = TestStack(server, TestScope(testScheduler))
        stack.session.signIn(StoredSession("tok_m", "usr_m"))
        val repo = DefaultCollectOperationsRepository(stack.api)
        repeat(3) { server.enqueue(ok()) }

        assertTrue(repo.handover("bk1", "cp1", AssetCondition.GOOD) is ApiResult.Success)
        assertTrue(repo.handover("bk1", "cp1", AssetCondition.GOOD, KitChecks()) is ApiResult.Success)
        assertTrue(repo.handover("bk1", "cp1", null, KitChecks(listOf(KitCheck("pouch", KitCheckState.MISSING)))) is ApiResult.Success)

        val legacy = server.takeRequest()
        assertEquals("/api/mobile/v1/collect/bookings/bk1/handover", legacy.path)
        assertEquals("""{"collectPointId":"cp1","condition":"good"}""", legacy.body.readUtf8())
        assertEquals("""{"collectPointId":"cp1","condition":"good","kit":{"checks":[]}}""", server.takeRequest().body.readUtf8())
        assertEquals("""{"collectPointId":"cp1","kit":{"checks":[{"itemId":"pouch","state":"MISSING"}]}}""", server.takeRequest().body.readUtf8())
    }
}
