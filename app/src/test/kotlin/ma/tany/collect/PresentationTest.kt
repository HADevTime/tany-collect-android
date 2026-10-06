package ma.tany.collect

import kotlinx.coroutines.test.runTest
import ma.tany.collect.core.ui.LoadState
import ma.tany.collect.core.ui.groupConsecutiveByDay
import ma.tany.collect.feature.activity.ActivityBucket
import ma.tany.collect.feature.activity.bucketActivity
import ma.tany.collect.feature.today.ALWAYS_SHOWN_SECTIONS
import ma.tany.collect.feature.today.OperationSection
import ma.tany.collect.feature.today.referenceTime
import ma.tany.collect.feature.today.TodayViewModel
import ma.tany.collect.feature.today.groupBySection
import ma.tany.collect.feature.today.ui
import ma.tany.core.model.collect.ActivityResponse
import ma.tany.core.model.collect.AssetDetail
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

    private class FakeToday(var result: ApiResult<TodayResponse>) : CollectRepository {
        var reads = 0

        override suspend fun me(): ApiResult<CollectMe> = ApiResult.Failure(ApiError.Unauthorized)

        override suspend fun today(pointId: String): ApiResult<TodayResponse> {
            reads++
            return result
        }

        override suspend fun activity(pointId: String, query: String?): ApiResult<ActivityResponse> = ApiResult.Success(ActivityResponse())

        override suspend fun booking(bookingId: String, pointId: String): ApiResult<MerchantBookingDetail> = ApiResult.Failure(ApiError.Unauthorized)

        override suspend fun assets(pointId: String, query: String?, filter: ma.tany.core.model.collect.AssetFilter?): ApiResult<AssetsResponse> = ApiResult.Success(AssetsResponse(enabled = false))

        override suspend fun asset(pointId: String, assetId: String): ApiResult<AssetDetail> = ApiResult.Failure(ApiError.Unauthorized)

        override suspend fun assetLookup(pointId: String, code: String): ApiResult<ma.tany.core.model.collect.AssetDetail> =
            ApiResult.Failure(ApiError.Unauthorized)

        override suspend fun incidents(pointId: String): ApiResult<ma.tany.core.model.collect.IncidentsResponse> =
            ApiResult.Failure(ApiError.Unauthorized)
    }

    @Test
    fun everyServerPhaseHasASectionAndADescription() {
        MerchantPhase.entries.forEach { phase ->
            val ui = phase.ui()
            assertTrue(ui.description != 0)
            assertTrue(ui.section in OperationSection.entries)
        }
        // Sections mirror the SERVER counters (tany-backend getTodayOperations): awaitingCustomer, blocked, late…
        listOf(MerchantPhase.PICKUP_AWAITING_CUSTOMER, MerchantPhase.RETURN_AWAITING_CUSTOMER, MerchantPhase.DEPOSIT_AWAITING_CUSTOMER)
            .forEach { assertEquals(OperationSection.AWAITING_CUSTOMER, it.ui().section) }
        listOf(MerchantPhase.DEPOSIT_DISPUTED, MerchantPhase.BLOCKED_PENDING_REVIEW).forEach { assertEquals(OperationSection.ATTENTION, it.ui().section) }
        listOf(MerchantPhase.PICKUP_UPCOMING, MerchantPhase.PICKUP_READY, MerchantPhase.PICKUP_IN_PROGRESS)
            .forEach { assertEquals(OperationSection.TO_COLLECT, it.ui().section) }
        listOf(MerchantPhase.RETURN_DUE, MerchantPhase.RETURN_IN_PROGRESS, MerchantPhase.DEPOSIT_TO_REFUND)
            .forEach { assertEquals(OperationSection.TO_RETURN, it.ui().section) }
        assertEquals(OperationSection.LATE, MerchantPhase.RETURN_LATE.ui().section)
        assertEquals(OperationSection.NO_SHOW, MerchantPhase.NO_SHOW.ui().section)
    }

    @Test
    fun todayGroupingKeepsEveryOperationInServerOrder() {
        val operations = today.operations
        assertTrue("real fixture has operations", operations.isNotEmpty())
        val groups = groupBySection(operations)
        // Nothing lost or duplicated.
        assertEquals(operations.map { it.id }.toSet(), groups.flatMap { it.second }.map { it.id }.toSet())
        assertEquals(operations.size, groups.sumOf { it.second.size })
        // Sections follow the fixed iOS order; inside a section, rows are chronological on their server instant.
        assertEquals(groups.map { it.first }, groups.map { it.first }.sortedBy { it.ordinal })
        groups.forEach { (_, ops) -> assertEquals(ops.sortedBy { it.referenceTime() }, ops) }
        // Only « À collecter » / « À retourner » may be empty (they always show a reassuring line).
        assertTrue(groups.filter { it.second.isEmpty() }.all { it.first in ALWAYS_SHOWN_SECTIONS })
        assertTrue(groups.map { it.first }.containsAll(ALWAYS_SHOWN_SECTIONS))
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
    fun activityBucketsFollowTheServerOrderAndNeverSayTomorrow() {
        val activity: ma.tany.core.model.collect.ActivityResponse = TanyJson.decodeFromString(fixture("activity"))
        val items = activity.items
        assertTrue("real fixture has items", items.isNotEmpty())
        val today = java.time.LocalDate.of(2026, 10, 5)
        val groups = bucketActivity(items, today)
        // Same rows, same (server) order.
        assertEquals(items.map { it.id }, groups.flatMap { it.second }.map { it.id })
        // Consecutive runs only; a pickup scheduled tomorrow is « today » (last updated today), never a « Demain » header.
        groups.zipWithNext().forEach { (a, b) -> assertTrue(a.first != b.first) }
        val future = items.first().copy(updatedAt = null, scheduledAt = java.time.Instant.parse("2026-10-06T09:00:00Z"))
        assertEquals(ActivityBucket.TODAY, bucketActivity(listOf(future), today).single().first)
        val old = items.first().copy(updatedAt = java.time.Instant.parse("2026-09-01T09:00:00Z"))
        assertEquals(ActivityBucket.HISTORY, bucketActivity(listOf(old), today).single().first)
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
