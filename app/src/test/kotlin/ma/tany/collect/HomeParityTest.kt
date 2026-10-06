package ma.tany.collect

import ma.tany.collect.feature.today.HomeSection
import ma.tany.collect.feature.today.contains
import ma.tany.collect.feature.today.heroAction
import ma.tany.collect.feature.today.homeItemIndex
import ma.tany.collect.feature.today.homeSectionsWithAnchors
import ma.tany.collect.feature.today.homeStats
import ma.tany.collect.feature.today.pickHero
import ma.tany.core.designsystem.component.TanyTone
import ma.tany.core.model.collect.MerchantPhase
import ma.tany.core.model.collect.TodayCounts
import ma.tany.core.model.collect.TodayResponse
import ma.tany.core.model.common.TanyJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Home parity with iOS: presentation over the server counters and phases only. */
class HomeParityTest {
    private val today: TodayResponse =
        TanyJson.decodeFromString(File("../core/model/src/test/resources/fixtures/real/today.json").readText())

    @Test
    fun statsFollowTheIosOrderAndServerCounters() {
        val stats = homeStats(TodayCounts(toCollect = 3, toReturn = 2, awaitingCustomer = 1, late = 4))
        assertEquals(listOf(3, 2, 1, 4), stats.map { it.value })
        assertEquals(
            listOf(HomeSection.TO_COLLECT, HomeSection.TO_RETURN, HomeSection.AWAITING_CUSTOMER, HomeSection.NOW),
            stats.map { it.target },
        )
        // Pickup = TANY accent, late = danger (only when > 0, the card decides).
        assertEquals(TanyTone.ACTION, stats[0].tone)
        assertEquals(TanyTone.DANGER, stats[3].tone)
    }

    @Test
    fun heroCtaFollowsTheServerPhase() {
        val op = today.operations.first()
        fun action(phase: MerchantPhase) = op.copy(phase = phase).heroAction()
        // Real starts only where the server lets the operation be worked on now.
        listOf(
            MerchantPhase.PICKUP_READY, MerchantPhase.PICKUP_IN_PROGRESS, MerchantPhase.RETURN_DUE,
            MerchantPhase.RETURN_LATE, MerchantPhase.RETURN_IN_PROGRESS, MerchantPhase.DEPOSIT_TO_REFUND,
        ).forEach { assertTrue(it.name, action(it).startFlow) }
        listOf(
            MerchantPhase.PICKUP_UPCOMING, MerchantPhase.WITH_CUSTOMER, MerchantPhase.PICKUP_AWAITING_CUSTOMER,
            MerchantPhase.RETURN_AWAITING_CUSTOMER, MerchantPhase.DEPOSIT_AWAITING_CUSTOMER,
        ).forEach { assertFalse(it.name, action(it).startFlow) }
        // Never the generic label when a meaningful one exists.
        assertEquals(R.string.stage_start_pickup, action(MerchantPhase.PICKUP_READY).label)
        assertEquals(R.string.hero_cta_prepare, action(MerchantPhase.PICKUP_UPCOMING).label)
        assertEquals(R.string.stage_start_return, action(MerchantPhase.RETURN_DUE).label)
        assertEquals(R.string.hero_cta_continue, action(MerchantPhase.PICKUP_IN_PROGRESS).label)
    }

    @Test
    fun collectAndReturnSectionsComeFirstAndAreAlwaysListed() {
        val pickupOnly = today.operations.filter { it.phase == MerchantPhase.PICKUP_READY }
        val hero = pickHero(pickupOnly)
        val sections = homeSectionsWithAnchors(pickupOnly, hero)
        // The only pickup is the hero ⇒ « À collecter » is listed but empty (no duplicate), « À retourner » too.
        assertEquals(listOf(HomeSection.TO_COLLECT, HomeSection.TO_RETURN), sections.map { it.first })
        assertTrue(sections.all { it.second.isEmpty() })

        val all = homeSectionsWithAnchors(today.operations, pickHero(today.operations))
        assertEquals(all.map { it.first }, all.map { it.first }.sortedBy { it.ordinal })
        assertEquals(HomeSection.TO_COLLECT, all.first().first)
    }

    @Test
    fun statTapsLeadToTheHeroOrTheSectionHeader() {
        val ops = today.operations
        val hero = pickHero(ops)!!
        val sections = homeSectionsWithAnchors(ops, hero)
        val heroTarget = HomeSection.entries.first { it.contains(hero) }
        // Items: stats(0) · hero(1) · sections…
        assertEquals(1, homeItemIndex(sections, heroTarget, hero, hasStale = false))
        assertEquals(2, homeItemIndex(sections, heroTarget, hero, hasStale = true))
        val first = sections.first { it.first != heroTarget }.first
        val index = homeItemIndex(sections, first, hero, hasStale = false)!!
        assertTrue(index >= 2)
        assertNull(homeItemIndex(sections, HomeSection.NO_SHOW, hero, hasStale = false))
    }
}
