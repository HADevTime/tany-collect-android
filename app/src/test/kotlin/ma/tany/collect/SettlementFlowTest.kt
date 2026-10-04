package ma.tany.collect

import kotlinx.coroutines.test.runTest
import ma.tany.collect.core.ui.LoadState
import ma.tany.collect.feature.revenue.SettlementViewModel
import ma.tany.core.model.collect.RevenueOverview
import ma.tany.core.model.collect.SettlementCollection
import ma.tany.core.model.collect.SettlementOverview
import ma.tany.core.model.collect.SettlementQr
import ma.tany.core.model.common.ApiErrorCode
import ma.tany.core.model.common.TanyJson
import ma.tany.core.network.ApiError
import ma.tany.core.network.ApiResult
import ma.tany.core.network.CollectBusinessRepository
import ma.tany.core.network.SettlementActionError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.io.File

class SettlementFlowTest {
    @get:Rule
    val main = MainDispatcherRule()

    private fun fixture(name: String) = File("../core/model/src/test/resources/fixtures/real/$name.json").readText()

    private val awaiting = TanyJson.decodeFromString<SettlementOverview>(fixture("settlement_awaiting"))
    private val confirmed = TanyJson.decodeFromString<SettlementOverview>(fixture("settlement_confirmed"))

    private class FakeBusiness(var overview: ApiResult<SettlementOverview>) : CollectBusinessRepository {
        val calls = mutableListOf<String>()
        var confirmResult: ApiResult<SettlementOverview> = ApiResult.Failure(ApiError.Unauthorized)
        var confirmed: SettlementCollection? = null

        override suspend fun revenue(pointId: String, period: String?): ApiResult<RevenueOverview> = ApiResult.Failure(ApiError.Unauthorized)

        override suspend fun settlement(pointId: String): ApiResult<SettlementOverview> {
            calls += "read"
            return overview
        }

        override suspend fun settlementQr(pointId: String, collectionId: String): ApiResult<SettlementQr> {
            calls += "qr"
            return ApiResult.Failure(ApiError.Unauthorized)
        }

        override suspend fun confirmHandoff(pointId: String, collection: SettlementCollection): ApiResult<SettlementOverview> {
            calls += "confirm"; confirmed = collection
            return confirmResult
        }

        override suspend fun disputeHandoff(pointId: String, collectionId: String, reason: String?): ApiResult<SettlementOverview> {
            calls += "dispute"
            return confirmResult
        }
    }

    @Test
    fun confirmSendsTheAgentDeclarationOnceAndAppliesTheServerOverview() = runTest {
        val repo = FakeBusiness(ApiResult.Success(awaiting)).apply { confirmResult = ApiResult.Success(confirmed) }
        val vm = SettlementViewModel(repo)
        vm.load("cp-maarif")
        var done = 0
        vm.confirm("cp-maarif", awaiting.activeCollection!!) { done++ }
        assertEquals(listOf("read", "confirm"), repo.calls)
        assertEquals(awaiting.activeCollection, repo.confirmed)
        assertEquals(confirmed, (vm.state.value as LoadState.Loaded).value)
        assertEquals(1, done)
        assertNull(vm.ui.value.error)
    }

    @Test
    fun staleDeclarationIsTypedAndRereadNeverResent() = runTest {
        val repo = FakeBusiness(ApiResult.Success(awaiting)).apply {
            confirmResult = ApiResult.Failure(ApiError.Http(409, ApiErrorCode.CONFIRMATION_STALE, "confirmation_stale", "…"))
        }
        val vm = SettlementViewModel(repo)
        vm.load("cp-maarif")
        vm.confirm("cp-maarif", awaiting.activeCollection!!) {}
        assertEquals(listOf("read", "confirm", "read"), repo.calls)
        assertEquals(SettlementActionError.Stale, vm.ui.value.error)
    }
}
