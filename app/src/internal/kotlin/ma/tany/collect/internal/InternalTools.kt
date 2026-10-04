package ma.tany.collect.internal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ma.tany.core.designsystem.R
import ma.tany.core.designsystem.component.BusinessDateTimeText
import ma.tany.core.designsystem.component.ConfirmationKind
import ma.tany.core.designsystem.component.ConfirmationRequest
import ma.tany.core.designsystem.component.ConfirmationSheetHost
import ma.tany.core.designsystem.component.MoneyText
import ma.tany.core.designsystem.component.ProductImageSurface
import ma.tany.core.designsystem.component.TanyBadge
import ma.tany.core.designsystem.component.TanyButton
import ma.tany.core.designsystem.component.TanyButtonStyle
import ma.tany.core.designsystem.component.TanyCard
import ma.tany.core.designsystem.component.TanyEmptyState
import ma.tany.core.designsystem.component.TanyIconContainer
import ma.tany.core.designsystem.component.TanyInfoRow
import ma.tany.core.designsystem.component.TanyRow
import ma.tany.core.designsystem.component.TanyStatusChip
import ma.tany.core.designsystem.component.TanyTone
import ma.tany.core.designsystem.component.TanyTopBar
import ma.tany.core.designsystem.component.rememberConfirmationState
import ma.tany.core.designsystem.theme.TanyTheme
import ma.tany.core.designsystem.theme.ThemePreference
import ma.tany.core.model.common.MoneyAmount
import java.time.Instant

/**
 * INTERNAL design system showcase (debug + staging only — never compiled into release).
 * Validates typography, colors, cards, buttons, sheets, Light/Dark, RTL and ProductImageSurface.
 * Sample texts below are internal fixtures, not product copy.
 */
object InternalTools {
    const val enabled: Boolean = true

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    fun Showcase(onBack: () -> Unit) {
        var preference by remember { mutableStateOf(ThemePreference.SYSTEM) }
        var rtl by remember { mutableStateOf(false) }
        val confirmation = rememberConfirmationState()
        val direction = if (rtl) LayoutDirection.Rtl else LocalLayoutDirection.current
        TanyTheme(preference) {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                Column(Modifier.fillMaxSize()) {
                    TanyTopBar(title = "Design system · Collect", onBack = onBack, chrome = true)
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ThemePreference.entries.forEach { p ->
                                TanyButton(p.name, { preference = p }, style = TanyButtonStyle.SECONDARY, fillWidth = false)
                            }
                            TanyButton(if (rtl) "LTR" else "RTL", { rtl = !rtl }, style = TanyButtonStyle.SECONDARY, fillWidth = false)
                        }
                        Text("Display", style = TanyTheme.typography.display)
                        Text("Title · Headline", style = TanyTheme.typography.title)
                        Text("Body — العربية · Français · English", style = TanyTheme.typography.body)
                        Text("Caption", style = TanyTheme.typography.caption, color = TanyTheme.colors.textMuted)

                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            TanyTone.entries.forEach { TanyStatusChip(it.name, it) }
                            TanyBadge("3")
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ProductImageSurface(url = null, contentDescription = null, modifier = Modifier.width(120.dp))
                            ProductImageSurface(
                                url = "https://staging.tany.ma/images/products/perceuse.png",
                                contentDescription = null,
                                modifier = Modifier.width(120.dp),
                            )
                        }

                        TanyCard {
                            TanyInfoRow("Location (3 jours)") { MoneyText(MoneyAmount.parse("148.5")) }
                            TanyInfoRow("Caution") { MoneyText(MoneyAmount.ofMajor(300)) }
                            TanyInfoRow("À payer", emphasized = true) { MoneyText(MoneyAmount.parse("448.5")) }
                            TanyInfoRow("Collecte") {
                                BusinessDateTimeText(Instant.parse("2026-10-05T08:00:00Z"), end = Instant.parse("2026-10-05T10:00:00Z"))
                            }
                        }
                        TanyCard(contentPadding = 0.dp) {
                            TanyRow(title = "Row", subtitle = "Subtitle", leadingIcon = R.drawable.ic_tany_calendar, onClick = {})
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TanyIconContainer(R.drawable.ic_tany_box, null)
                            TanyIconContainer(R.drawable.ic_tany_scan, null, container = TanyTheme.colors.accent, tint = TanyTheme.colors.onAccent)
                        }
                        TanyButton("Primary", {})
                        TanyButton("Loading", {}, loading = true)
                        TanyButton("Secondary", {}, style = TanyButtonStyle.SECONDARY)
                        TanyButton(
                            "Financial sheet",
                            {
                                confirmation.show(
                                    ConfirmationRequest("cash", "Encaissement", "Montant exact reçu en espèces.", "J’ai reçu ce montant", ConfirmationKind.FINANCIAL, MoneyAmount.parse("448.5")),
                                )
                            },
                            style = TanyButtonStyle.SECONDARY,
                        )
                        TanyButton(
                            "Destructive sheet",
                            { confirmation.show(ConfirmationRequest("cancel", "Annuler ?", "Action irréversible.", "Annuler la réservation", ConfirmationKind.DESTRUCTIVE)) },
                            style = TanyButtonStyle.DESTRUCTIVE,
                        )
                        TanyEmptyState(title = "Empty state", message = "Message", modifier = Modifier.fillMaxWidth())
                    }
                }
                ConfirmationSheetHost(confirmation) { confirmation.finish() }
            }
        }
    }
}
