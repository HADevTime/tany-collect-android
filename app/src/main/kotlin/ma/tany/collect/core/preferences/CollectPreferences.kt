package ma.tany.collect.core.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ma.tany.core.designsystem.theme.ThemePreference

private val Context.collectPreferences: DataStore<Preferences> by preferencesDataStore(name = "tany_collect_preferences")

/**
 * Non-sensitive preferences. The active TANY Collect point is a convenience only: every request carries it and the
 * server re-checks the merchant's scope. It is cleared on sign-out.
 */
class CollectPreferences(private val context: Context) {
    val theme: Flow<ThemePreference> = context.collectPreferences.data.map { prefs ->
        prefs[KEY_THEME]?.let { stored -> ThemePreference.entries.firstOrNull { it.name == stored } } ?: ThemePreference.SYSTEM
    }

    val activePointId: Flow<String?> = context.collectPreferences.data.map { it[KEY_ACTIVE_POINT] }

    suspend fun setTheme(preference: ThemePreference) {
        context.collectPreferences.edit { it[KEY_THEME] = preference.name }
    }

    suspend fun setActivePoint(pointId: String) {
        context.collectPreferences.edit { it[KEY_ACTIVE_POINT] = pointId }
    }

    suspend fun clearActivePoint() {
        context.collectPreferences.edit { it.remove(KEY_ACTIVE_POINT) }
    }

    private companion object {
        val KEY_THEME = stringPreferencesKey("theme")
        val KEY_ACTIVE_POINT = stringPreferencesKey("active_point_id")
    }
}
