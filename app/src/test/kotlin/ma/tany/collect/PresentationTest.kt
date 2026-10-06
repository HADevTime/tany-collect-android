package ma.tany.collect

import kotlinx.coroutines.test.runTest
import ma.tany.collect.core.ui.LoadState
import ma.tany.collect.core.ui.groupConsecutiveByDay
import ma.tany.collect.feature.equipment.filterAssets
import ma.tany.collect.feature.today.OperationSection
import ma.tany.collect.feature.today.TodayViewModel
import ma.tany.collect.feature.today.groupBySection
import ma.tany.collect.feature.today.ui
import ma.tany.core.model.collect.ActivityResponse
import ma.tany.core.model.collect.AssetDetail
import ma.tany.core.model.collect.AssetGroup
import ma.tany.core.model.collect.AssetsResponse
import ma.tany.core.model.collect.CollectMe
import ma.tany.core.model.collect.MerchantBookingDetail
import ma.tany.core.model.collect.MerchantPhase
import ma.tany.core.model.collect.TodayResponse
import ma.tany.core.model.common.TanyJson
import ma.tany.core.network.ApiEndpoint
import ma.tany.core.network.ApiEnvironment
import ma.tany.core.network.ApiError
import ma.tany.core.network.ApiResult
import ma.tany.core.network.CollectRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate

/** Presentation rules of the premium redesign: grouping never reorders or drops server data. */
class PresentationTest {
    @get:Rule
    val main = MainDispatcherRule()

    private fun fixture(name: String) = File("../core/model/src/test/resources/fixtures/real/$name.json").readText()

    private val today: TodayResponse = TanyJson.decodeFromString(fixture("today"))
    private val assets: AssetsResponse = TanyJson.decodeFromString(fixture("assets"))

    private class FakeToday(var result: ApiResult<TodayResponse>) : CollectRepository {
        var reads = 0

        override suspend fun me(): ApiResult<CollectMe> = ApiResult.Failure(ApiError.Unauthorized)

        override suspend fun today(pointId: String): ApiResult<TodayResponse> {
            reads++
            return result
        }

        override suspend fun activity(pointId: String, query: String?): ApiResult<ActivityResponse> = ApiResult.Success(ActivityResponse())

        override suspend fun booking(bookingId: String, pointId: String): ApiResult<MerchantBookingDetail> = ApiResult.Failure(ApiError.Unauthorized)

        override suspend fun assets(pointId: String): ApiResult<AssetsResponse> = ApiResult.Success(AssetsResponse(enabled = false))

        override suspend fun asset(pointId: String, assetId: String): ApiResult<AssetDetail> = ApiResult.Failure(ApiError.Unauthorized)
    }

    @Test
    fun everyServerPhaseHasASectionAndADescription() {
        MerchantPhase.entries.forEach { phase ->
            val ui = phase.ui()
            assertTrue(ui.description != 0)
            assertTrue(ui.section in OperationSection.entries)
        }
        // Customer / TANY confirmations are never listed as « to handle » by the merchant.
        listOf(
            MerchantPhase.PICKUP_AWAITING_CUSTOMER,
            MerchantPhase.RETURN_AWAITING_CUSTOMER,
            MerchantPhase.DEPOSIT_AWAITING_CUSTOMER,
            MerchantPhase.DEPOSIT_DISPUTED,
            MerchantPhase.BLOCKED_PENDING_REVIEW,
        ).forEach { assertEquals(OperationSection.WAITING, it.ui().section) }
    }

    @Test
    fun todayGroupingKeepsEveryOperationInServerOrder() {
        val operations = today.operations
        assertTrue("real fixture has operations", operations.isNotEmpty())
        val groups = groupBySection(operations)
        // Nothing lost or duplicated.
        assertEquals(operations.map { it.id }.toSet(), groups.flatMap { it.second }.map { it.id }.toSet())
        assertEquals(operations.size, groups.sumOf { it.second.size })
        // Sections follow the fixed order and each keeps the server's relative order.
        assertEquals(groups.map { it.first }, groups.map { it.first }.sortedBy { it.ordinal })
        groups.forEach { (_, ops) ->
            val serverIndexes = ops.map { op -> operations.indexOfFirst { it.id == op.id } }
            assertEquals(serverIndexes.sorted(), serverIndexes)
        }
        assertTrue(groups.none { it.second.isEmpty() })
    }

    @Test
    fun dayGroupingOnlySplitsConsecutiveRuns() {
        val d1 = LocalDate.of(2026, 10, 5)
        val d2 = LocalDate.of(2026, 10, 4)
        val items = listOf("a" to d1, "b" to d1, "c" to d2, "d" to d1)
        val groups = groupConsecutiveByDay(items) { it.second }
        assertEquals(listOf(d1, d2, d1), groups.map { it.first })
        assertEquals(listOf("a", "b", "c", "d"), groups.flatMap { g -> g.second.map { it.first } })
        assertTrue(groupConsecutiveByDay(emptyList<Pair<String, LocalDate>>()) { it.second }.isEmpty())
    }

    @Test
    fun equipmentFilterUsesTheServerGroupOnly() {
        assertEquals(assets.assets, filterAssets(assets.assets, null))
        AssetGroup.entries.forEach { group ->
            val filtered = filterAssets(assets.assets, group)
            assertTrue(filtered.all { it.group == group })
            assertEquals(assets.assets.count { it.group == group }, filtered.size)
        }
    }

    @Test
    fun pullToRefreshKeepsTheListWhenTheServerFails() = runTest {
        val repo = FakeToday(ApiResult.Success(today))
        val vm = TodayViewModel(repo, ApiEndpoint.of(ApiEnvironment.STAGING, "https://staging.tany.ma"))
        vm.load("cp-maarif")
        repo.result = ApiResult.Failure(ApiError.Network(java.io.IOException("offline")))
        vm.refresh("cp-maarif")
        assertEquals(LoadState.Loaded(today), vm.state.value)
        assertFalse(vm.refreshing.value)
        assertEquals(2, repo.reads)
    }
}
