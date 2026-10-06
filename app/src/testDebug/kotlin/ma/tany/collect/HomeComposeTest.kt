package ma.tany.collect

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import ma.tany.collect.feature.today.HomeSection
import ma.tany.collect.feature.today.HomeStatsGrid
import ma.tany.collect.feature.today.HomeTags
import ma.tany.collect.feature.today.NextOperationHero
import ma.tany.collect.feature.today.NoNextOperationCard
import ma.tany.core.designsystem.format.TanyFormatters
import ma.tany.core.designsystem.format.TanyLanguage
import ma.tany.core.designsystem.theme.TanyTheme
import ma.tany.core.designsystem.theme.ThemePreference
import ma.tany.core.model.collect.MerchantPhase
import ma.tany.core.model.collect.Operation
import ma.tany.core.model.collect.OperationKind
import ma.tany.core.model.collect.TodayCounts
import ma.tany.core.model.collect.TodayResponse
import ma.tany.core.model.common.TanyJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/** Home parity with TANY Collect iOS: 2 × 2 stat cards and the « Prochaine opération » hero (Compose, Robolectric). */
@RunWith(AndroidJUnit4::class)
class HomeComposeTest {
    @get:Rule
    val compose = createComposeRule()

    private val today: TodayResponse =
        TanyJson.decodeFromString(File("../core/model/src/test/resources/fixtures/real/today.json").readText())

    private val pickup: Operation = today.operations.first { it.phase == MerchantPhase.PICKUP_READY }
    private val ret: Operation = today.operations.first { it.phase == MerchantPhase.RETURN_DUE }

    private val longName = "Nettoyeur Pro des canapés et matelas — kit complet avec accessoires"

    private fun time(op: Operation) = TanyFormatters(TanyLanguage.FR).businessTime(op.pickupWindowStart)

    // ——— Stats ———

    @Test
    fun statsAreFourCardsAndOnlyNonZeroCardsLeadSomewhere() {
        var jumped: HomeSection? = null
        compose.setContent {
            TanyTheme {
                HomeStatsGrid(TodayCounts(toCollect = 1, toReturn = 0, awaitingCustomer = 0, late = 2), onJump = { jumped = it })
            }
        }
        compose.onNodeWithTag(HomeTags.STATS).assertExists()
        listOf("À collecter", "À retourner", "En attente client", "En retard").forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNodeWithContentDescription("À collecter : 1").assertHasClickAction()
        compose.onNodeWithContentDescription("À retourner : 0").assertHasNoClickAction()
        compose.onNodeWithContentDescription("En attente client : 0").assertHasNoClickAction()
        compose.onNodeWithContentDescription("En retard : 2").assertHasClickAction().performClick()
        assertEquals(HomeSection.NOW, jumped)
        compose.onNodeWithContentDescription("À collecter : 1").performClick()
        assertEquals(HomeSection.TO_COLLECT, jumped)
    }

    @Test
    fun allZeroStatsStayQuietAndInert() {
        compose.setContent { TanyTheme { HomeStatsGrid(TodayCounts(), onJump = {}) } }
        listOf("À collecter : 0", "À retourner : 0", "En attente client : 0", "En retard : 0").forEach {
            compose.onNodeWithContentDescription(it).assertExists().assertHasNoClickAction()
        }
    }

    @Test
    fun threeDigitCountsAreShownInFull() {
        compose.setContent { TanyTheme { HomeStatsGrid(TodayCounts(toCollect = 128, late = 12), onJump = {}) } }
        compose.onNodeWithContentDescription("À collecter : 128").assertExists()
        compose.onNodeWithText("128", substring = true).assertExists()
    }

    // ——— Hero ———

    @Test
    fun pickupHeroPutsTimeTypeFullProductAndAStartAction() {
        var opened = 0
        var started = 0
        val op = pickup.copy(product = pickup.product.copy(name = longName))
        compose.setContent { TanyTheme { NextOperationHero(op, onOpen = { opened++ }, onStart = { started++ }) } }
        compose.onNodeWithTag(HomeTags.HERO).assertExists()
        compose.onNodeWithText("PROCHAINE OPÉRATION").assertExists()
        compose.onNodeWithText("COLLECTE").assertExists()
        compose.onAllNodesWithText(time(op), substring = true).onFirst().assertExists()
        // Full product name, never truncated in the data shown.
        compose.onNodeWithText(longName).assertExists()
        compose.onNodeWithText(op.customer.shortName, substring = true).assertExists()
        compose.onNodeWithText("Commencer la collecte").assertHasClickAction().performClick()
        assertEquals(1, started)
        assertEquals(0, opened)
    }

    @Test
    fun upcomingPickupHeroOnlyOffersToPrepare() {
        var opened = 0
        var started = 0
        val op = pickup.copy(phase = MerchantPhase.PICKUP_UPCOMING)
        compose.setContent { TanyTheme { NextOperationHero(op, onOpen = { opened++ }, onStart = { started++ }) } }
        compose.onNodeWithText("Préparer").performClick()
        assertEquals(1, opened)
        assertEquals(0, started)
    }

    @Test
    fun returnHeroShowsTheReturnBadgeAndAction() {
        var started = 0
        compose.setContent { TanyTheme { NextOperationHero(ret, onOpen = {}, onStart = { started++ }) } }
        compose.onNodeWithText("RETOUR").assertExists()
        compose.onNodeWithText("Commencer le retour").performClick()
        assertEquals(1, started)
        assertEquals(OperationKind.RETURN, ret.kind)
    }

    @Test
    fun lateReturnHeroSaysHowLate() {
        val late = ret.copy(phase = MerchantPhase.RETURN_LATE, lateMinutes = 95)
        compose.setContent { TanyTheme { NextOperationHero(late, onOpen = {}, onStart = {}) } }
        compose.onNodeWithText("RETOUR").assertExists()
        compose.onNodeWithText("1 h 35", substring = true).assertExists()
    }

    @Test
    fun heroRendersInDarkTheme() {
        compose.setContent { TanyTheme(ThemePreference.DARK) { NextOperationHero(pickup, onOpen = {}, onStart = {}) } }
        compose.onNodeWithTag(HomeTags.HERO).assertExists()
        compose.onNodeWithText("Commencer la collecte").assertExists()
    }

    @Test
    fun noNextOperationIsACalmState() {
        compose.setContent { TanyTheme { NoNextOperationCard(completedToday = 0) } }
        compose.onNodeWithTag(HomeTags.NO_NEXT).assertExists()
        compose.onNodeWithText("Aucune opération à venir").assertExists()
        compose.onNodeWithText("Les prochaines collectes et retours apparaîtront ici.").assertExists()
    }

    // ——— Languages ———

    @Test
    @Config(qualifiers = "en")
    fun englishHero() {
        compose.setContent { TanyTheme(ThemePreference.LIGHT) { NextOperationHero(pickup, onOpen = {}, onStart = {}) } }
        compose.onNodeWithText("NEXT OPERATION").assertExists()
        compose.onNodeWithText("Start the pickup").assertExists()
    }

    @Test
    @Config(qualifiers = "ar-ldrtl")
    fun arabicHeroIsMirrored() {
        compose.setContent { TanyTheme { NextOperationHero(pickup, onOpen = {}, onStart = {}) } }
        val heading = compose.onNodeWithText("العملية القادمة").fetchSemanticsNode().boundsInRoot
        val badge = compose.onNodeWithText("الاستلام").fetchSemanticsNode().boundsInRoot
        // RTL: the heading starts on the right, the type badge sits on its left.
        assertTrue(badge.right <= heading.left + 1f)
        // The time stays readable (LTR island).
        compose.onAllNodesWithText(time(pickup), substring = true).onFirst().assertExists()
        compose.onNodeWithText("بدء الاستلام").assertExists()
    }
}
