package ma.tany.core.designsystem.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import ma.tany.core.designsystem.component.ConfirmationRequest
import ma.tany.core.designsystem.component.ConfirmationState
import ma.tany.core.designsystem.component.TanyButton
import ma.tany.core.designsystem.component.TanyInfoRow
import ma.tany.core.designsystem.component.TanyStatusChip
import ma.tany.core.designsystem.component.TanyTone
import ma.tany.core.designsystem.theme.DarkTanyColors
import ma.tany.core.designsystem.theme.LightTanyColors
import ma.tany.core.designsystem.theme.TanyTheme
import ma.tany.core.designsystem.theme.ThemePreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ComponentsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun buttonIgnoresDoubleTap() {
        var clicks = 0
        compose.setContent { TanyTheme { TanyButton(text = "Réserver", onClick = { clicks++ }) } }
        compose.onNodeWithText("Réserver").performClick()
        compose.onNodeWithText("Réserver").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun loadingButtonIsDisabled() {
        var clicks = 0
        compose.setContent { TanyTheme { TanyButton(text = "Confirmer", onClick = { clicks++ }, loading = true) } }
        compose.onNodeWithText("Confirmer").assertIsNotEnabled()
        assertEquals(0, clicks)
    }

    @Test
    fun statusChipCarriesTextNotOnlyColor() {
        compose.setContent { TanyTheme(ThemePreference.DARK) { TanyStatusChip(label = "En retard", tone = TanyTone.DANGER) } }
        compose.onNodeWithText("En retard").assertExists()
    }

    @Test
    fun rtlMirrorsLayoutStructurally() {
        compose.setContent {
            TanyTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    TanyInfoRow(label = "الضمان") { Text("300 DH") }
                }
            }
        }
        val label = compose.onNodeWithText("الضمان", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val value = compose.onNodeWithText("300 DH", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("label is on the right in RTL", label.left > value.left)
    }

    @Test
    fun productStudioIsWhiteInBothThemes() {
        assertEquals(Color.White, LightTanyColors.media)
        assertEquals(Color.White, DarkTanyColors.media)
    }

    @Test
    fun onlyOneConfirmationAtATime() {
        val state = ConfirmationState()
        assertTrue(state.show(ConfirmationRequest("a", "A", "…", "OK")))
        assertFalse(state.show(ConfirmationRequest("b", "B", "…", "OK")))
        assertEquals("a", state.current?.id)
        state.processing = true
        state.dismiss()
        assertEquals("a", state.current?.id) // cannot dismiss while processing
        state.finish()
        assertEquals(null, state.current)
    }
}
