package ma.tany.collect.core.locale

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import ma.tany.core.designsystem.format.TanyLanguage
import ma.tany.core.network.LanguageProvider
import java.util.Locale

/**
 * Per-app language (FR / EN / AR) through the platform per-app locale API (system settings on Android 13+,
 * AppCompat storage below). Changing it recreates the activity, so layout direction (RTL) follows structurally.
 */
object AppLanguage : LanguageProvider {
    fun current(): TanyLanguage {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        val tag = if (!appLocales.isEmpty) appLocales[0]?.language else Locale.getDefault().language
        return TanyLanguage.fromTag(tag)
    }

    fun set(language: TanyLanguage) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
    }

    /** `Accept-Language` for the API: localized catalog + notifications. */
    override fun languageTag(): String = current().tag
}
