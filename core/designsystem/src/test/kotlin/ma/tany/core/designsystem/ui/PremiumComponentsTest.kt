package ma.tany.core.designsystem.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ma.tany.core.designsystem.R
import ma.tany.core.designsystem.component.TanyAmountPanel
import ma.tany.core.designsystem.component.TanyEmptyState
import ma.tany.core.designsystem.component.TanyListSkeleton
import ma.tany.core.designsystem.component.TanySegment
import ma.tany.core.designsystem.component.TanySegmentedControl
import ma.tany.core.designsystem.component.TanyStepState
import ma.tany.core.designsystem.component.TanyTimelineStep
import ma.tany.core.designsystem.theme.DarkTanyColors
import ma.tany.core.designsystem.theme.LightTanyColors
import ma.tany.core.designsystem.theme.TanyColors
import ma.tany.core.designsystem.theme.TanyTheme
import ma.tany.core.designsystem.theme.ThemePreference
import ma.tany.core.model.common.MoneyAmount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PremiumComponentsTest {
    @get:Rule
    val compose = createComposeRule()

    private fun contrast(a: Color, b: Color): Double {
        val l1 = a.luminance().toDouble()
        val l2 = b.luminance().toDouble()
        return (maxOf(l1, l2) + 0.05) / (minOf(l1, l2) + 0.05)
    }

    private fun assertReadable(name: String, fg: Color, bg: Color, min: Double = 4.5) {
        val ratio = contrast(fg, bg)
        assertTrue("$name contrast $ratio < $min", ratio >= min)
    }

    private fun checkPalette(c: TanyColors, theme: String) {
        listOf(c.page, c.surface, c.elevated).forEach { bg ->
            assertReadable("$theme textPrimary", c.textPrimary, bg)
            assertReadable("$theme textMuted", c.textMuted, bg)
            assertReadable("$theme textSubtle", c.textSubtle, bg)
        }
        listOf(c.success, c.warning, c.danger, c.info, c.neutralStatus).forEach { assertReadable("$theme status", it.content, it.container) }
        assertReadable("$theme accent container", c.onAccentContainer, c.accentContainer)
        assertReadable("$theme primary action", c.onPrimaryAction, c.primaryAction)
        assertReadable("$theme on accent", c.onAccent, c.accent)
        assertReadable("$theme chrome", c.onChrome, c.chrome)
        assertReadable("$theme chrome muted", c.onChromeMuted, c.chrome)
        assertReadable("$theme destructive", c.destructive, c.surface)
    }

    @Test
    fun paletteIsReadableInBothThemes() {
        checkPalette(LightTanyColors, "light")
        checkPalette(DarkTanyColors, "dark")
    }

    @Test
    fun darkSurfacesAreLayered() {
        val d = DarkTanyColors
        assertTrue(d.page.luminance() < d.surface.luminance())
        assertTrue(d.surface.luminance() < d.elevated.luminance())
        assertEquals(Color.White, d.media) // product studio stays white
    }

    @Test
    fun segmentedControlIsASingleChoiceGroup() {
        compose.setContent {
            TanyTheme {
                var selected by remember { mutableStateOf("fr") }
                TanySegmentedControl(
                    options = listOf(TanySegment("fr", "Français"), TanySegment("ar", "العربية")),
                    selected = selected,
                    onSelect = { selected = it },
                )
            }
        }
        compose.onNodeWithText("Français").assertIsSelected()
        compose.onNodeWithText("العربية").assertIsNotSelected().performClick()
        compose.onNodeWithText("العربية").assertIsSelected()
        compose.onNodeWithText("Français").assertIsNotSelected()
    }

    @Test
    fun timelineStepExposesItsStateAsText() {
        compose.setContent {
            TanyTheme(ThemePreference.DARK) {
                TanyTimelineStep(title = "Caution remise", state = TanyStepState.WAITING, stateLabel = "En attente du client")
            }
        }
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "En attente du client")).assertExists()
    }

    @Test
    fun skeletonIsAnnouncedOnceAsLoading() {
        compose.setContent { TanyTheme { TanyListSkeleton(rows = 2, header = true) } }
        val loading = ApplicationProvider.getApplicationContext<Context>().getString(R.string.tany_state_loading)
        compose.onNodeWithContentDescription(loading).assertExists()
    }

    @Test
    fun amountPanelShowsTheServerAmount() {
        compose.setContent { TanyTheme { TanyAmountPanel(label = "À rendre au client", amount = MoneyAmount.ofMajor(300)) } }
        compose.onNodeWithText("À rendre au client", substring = true, useUnmergedTree = true).assertExists()
        compose.onNodeWithText("300 DH", substring = true, useUnmergedTree = true).assertExists()
    }

    @Test
    fun emptyStateOffersItsActions() {
        var primary = 0
        var secondary = 0
        compose.setContent {
            TanyTheme {
                TanyEmptyState(
                    title = "Aucun résultat",
                    message = "Vérifiez la référence.",
                    actionLabel = "Scanner",
                    onAction = { primary++ },
                    secondaryLabel = "Effacer",
                    onSecondary = { secondary++ },
                )
            }
        }
        compose.onNodeWithText("Scanner").performClick()
        compose.onNodeWithText("Effacer").performClick()
        assertEquals(1, primary)
        assertEquals(1, secondary)
    }
}
