package io.github.drjkn7.shoestep.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import io.github.drjkn7.shoestep.core.Money
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** App settings. Stored on the phone only, excluded from backups like the database. */
interface SettingsStore {
    /** Currency new pairs start with; null = not chosen (the phone's region decides). */
    val defaultCurrency: Flow<String?>

    suspend fun setDefaultCurrency(code: String)

    /** Light, dark or follow the phone (the default). */
    val themeMode: Flow<ThemeMode>

    suspend fun setThemeMode(mode: ThemeMode)
}

/** The real store: one Preferences DataStore file ("settings") per app, created once by AppContainer. */
class DataStoreSettings(context: Context) : SettingsStore {
    private val store: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        produceFile = { context.applicationContext.preferencesDataStoreFile(FILE_NAME) },
    )

    // Anything unexpected in the file (e.g. a code this phone doesn't know) counts as "not chosen".
    override val defaultCurrency: Flow<String?> =
        store.data.map { prefs -> prefs[CURRENCY]?.takeIf(Money::isSupportedCurrency) }

    override suspend fun setDefaultCurrency(code: String) {
        require(Money.isSupportedCurrency(code)) { "Unsupported currency" }
        store.edit { it[CURRENCY] = code }
    }

    override val themeMode: Flow<ThemeMode> = store.data.map { prefs -> ThemeMode.fromStored(prefs[THEME]) }

    override suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[THEME] = mode.name }
    }

    private companion object {
        const val FILE_NAME = "settings"
        val CURRENCY = stringPreferencesKey("default_currency")
        val THEME = stringPreferencesKey("theme_mode")
    }
}
