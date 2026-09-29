package com.jhani.measurear.presentation

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * App languages. The choice is applied to the whole activity when it starts
 * ([wrap] from attachBaseContext), so every screen, hint and dialog — and Urdu's
 * right-to-left layout — follow it; changing it recreates the activity.
 */
enum class AppLanguage(val code: String?, val nativeName: String) {
    SYSTEM(null, "System default"),
    ENGLISH("en", "English"),
    HINDI("hi", "हिन्दी"),
    URDU("ur", "اردو"),
    TELUGU("te", "తెలుగు");

    companion object {
        private const val PREFS = "measurear"
        private const val KEY = "language"

        fun saved(context: Context): AppLanguage {
            val name = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            return values().firstOrNull { it.name == name } ?: SYSTEM
        }

        fun save(context: Context, language: AppLanguage) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, language.name).apply()
        }

        /** [base] with the saved language applied (unchanged for System default). */
        fun wrap(base: Context): Context {
            val code = saved(base).code ?: return base
            val locale = Locale(code)
            Locale.setDefault(locale)
            val config = Configuration(base.resources.configuration)
            config.setLocale(locale)
            config.setLayoutDirection(locale)
            return base.createConfigurationContext(config)
        }
    }
}
