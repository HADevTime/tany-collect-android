package ma.tany.collect.internal

import androidx.compose.runtime.Composable

/** PROD build: no internal tools (the showcase source set is not compiled into release). */
object InternalTools {
    const val enabled: Boolean = false

    @Composable
    fun Showcase(onBack: () -> Unit) = Unit
}
