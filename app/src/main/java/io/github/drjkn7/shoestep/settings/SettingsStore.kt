package io.github.drjkn7.shoestep.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import io.github.drjkn7.shoestep.core.Money
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

    /** Kilometres or miles; null = not chosen (the phone's region decides, [DistanceUnit.defaultFor]). */
    val distanceUnit: Flow<DistanceUnit?>

    suspend fun setDistanceUnit(unit: DistanceUnit)

    /** True once "Not now" was tapped on Home's "Show distance" card; it then stays hidden. */
    val distanceCardDismissed: Flow<Boolean>

    suspend fun dismissDistanceCard()
}

/**
 * The real store: one Preferences DataStore file ("settings") per app, created once by AppContainer.
 * Tests pass their own [fileName] and [scope] (cancelling the scope closes the file, like the app
 * stopping), so they never touch the app's settings.
 */
class DataStoreSettings(
    context: Context,
    fileName: String = FILE_NAME,
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
) : SettingsStore {
    private val store: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = scope,
        produceFile = { context.applicationContext.preferencesDataStoreFile(fileName) },
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

    override val distanceUnit: Flow<DistanceUnit?> = store.data.map { prefs -> DistanceUnit.fromStored(prefs[DISTANCE_UNIT]) }

    override suspend fun setDistanceUnit(unit: DistanceUnit) {
        store.edit { it[DISTANCE_UNIT] = unit.name }
    }

    override val distanceCardDismissed: Flow<Boolean> = store.data.map { prefs -> prefs[DISTANCE_CARD_DISMISSED] == true }

    override suspend fun dismissDistanceCard() {
        store.edit { it[DISTANCE_CARD_DISMISSED] = true }
    }

    private companion object {
        const val FILE_NAME = "settings"
        val CURRENCY = stringPreferencesKey("default_currency")
        val THEME = stringPreferencesKey("theme_mode")
        val DISTANCE_UNIT = stringPreferencesKey("distance_unit")
        val DISTANCE_CARD_DISMISSED = booleanPreferencesKey("distance_card_dismissed")
    }
}
