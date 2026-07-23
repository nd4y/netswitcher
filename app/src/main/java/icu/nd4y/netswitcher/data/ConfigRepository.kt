package icu.nd4y.netswitcher.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.configStore: DataStore<Preferences> by preferencesDataStore(name = "netswitcher")

class ConfigRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val key = stringPreferencesKey("config_json")

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val flow: Flow<Config> = appContext.configStore.data.map { prefs -> decode(prefs[key]) }

    suspend fun current(): Config = flow.first()

    suspend fun update(block: (Config) -> Config): Config {
        var result = Config.default()
        appContext.configStore.edit { prefs ->
            result = block(decode(prefs[key]))
            prefs[key] = json.encodeToString(Config.serializer(), result)
        }
        return result
    }

    fun encode(config: Config): String = json.encodeToString(Config.serializer(), config)

    fun decodeOrNull(raw: String): Config? =
        runCatching { json.decodeFromString(Config.serializer(), raw) }.getOrNull()

    private fun decode(raw: String?): Config {
        if (raw == null) return Config.default()
        return decodeOrNull(raw) ?: Config.default()
    }

    companion object {
        @Volatile
        private var instance: ConfigRepository? = null

        fun get(context: Context): ConfigRepository =
            instance ?: synchronized(this) {
                instance ?: ConfigRepository(context).also { instance = it }
            }
    }
}
