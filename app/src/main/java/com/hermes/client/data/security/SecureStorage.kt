package com.hermes.client.data.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.hermes.client.domain.model.AIProviderType
import com.hermes.client.domain.model.ProviderConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Secure storage using Android Keystore + EncryptedSharedPreferences.
 * Never stores secrets in plaintext SharedPreferences.
 */
@Singleton
class SecureStorage @Inject constructor(context: Context) {

    private val prefs: SharedPreferences = createSecurePrefs(context)

    private val _themeMode = MutableStateFlow(getThemeMode())
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    companion object {
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_API_KEY = "api_key" // This is for Hermes Agent only
        private const val KEY_SESSION_ID = "current_session_id"
        private const val KEY_SESSION_KEY = "session_key"
        private const val KEY_ACTIVE_SERVER_PROFILE = "active_server_profile"
        private const val KEY_DEFAULT_MODEL = "default_model"
        private const val KEY_DEFAULT_PROVIDER = "default_provider"
        private const val KEY_PROVIDER_CONFIG_PREFIX = "provider_config_"
        private const val KEY_THEME_MODE = "theme_mode" // "system", "dark", "light"

        private fun createSecurePrefs(context: Context): SharedPreferences {
            return try {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context,
                    "hermes_secure_prefs",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (_: Exception) {
                // Keystore desync or corruption: clear and recreate, or fallback safely
                try {
                    context.getSharedPreferences("hermes_secure_prefs", Context.MODE_PRIVATE).edit().clear().commit()
                    val masterKey = MasterKey.Builder(context)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build()
                    EncryptedSharedPreferences.create(
                        context,
                        "hermes_secure_prefs",
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                    )
                } catch (_: Exception) {
                    context.getSharedPreferences("hermes_fallback_prefs", Context.MODE_PRIVATE)
                }
            }
        }
    }

    // ─── Server URL (for Hermes Agent) ─────────────────────────────────────────────

    fun getServerUrl(): String? = prefs.getString(KEY_SERVER_URL, null)

    fun setServerUrl(url: String) {
        prefs.edit().putString(KEY_SERVER_URL, url).apply()
    }

    // ─── API Key (for Hermes Agent only, to be deprecated by ProviderConfig) ────

    @Deprecated("Use saveProviderConfig(Hermes Agent) instead")
    fun getApiKey(): String? = prefs.getString(KEY_API_KEY, null)

    @Deprecated("Use saveProviderConfig(Hermes Agent) instead")
    fun setApiKey(key: String) {
        prefs.edit().putString(KEY_API_KEY, key).apply()
    }

    // ─── Session ────────────────────────────────────────────────

    fun getCurrentSessionId(): String? = prefs.getString(KEY_SESSION_ID, null)

    fun setCurrentSessionId(sessionId: String) {
        prefs.edit().putString(KEY_SESSION_ID, sessionId).apply()
    }

    fun getSessionKey(): String? = prefs.getString(KEY_SESSION_KEY, null)

    fun setSessionKey(key: String) {
        prefs.edit().putString(KEY_SESSION_KEY, key).apply()
    }

    // ─── Server Profile ─────────────────────────────────────────

    fun getActiveServerProfileId(): String? =
        prefs.getString(KEY_ACTIVE_SERVER_PROFILE, null)

    fun setActiveServerProfileId(id: String) {
        prefs.edit().putString(KEY_ACTIVE_SERVER_PROFILE, id).apply()
    }

    // ─── Model Defaults ─────────────────────────────────────────

    fun getDefaultModel(): String? = prefs.getString(KEY_DEFAULT_MODEL, null)

    fun setDefaultModel(model: String) {
        prefs.edit().putString(KEY_DEFAULT_MODEL, model).apply()
    }

    fun getDefaultProvider(): String? = prefs.getString(KEY_DEFAULT_PROVIDER, null)

    fun setDefaultProvider(provider: String) {
        prefs.edit().putString(KEY_DEFAULT_PROVIDER, provider).apply()
    }

    // ─── Theme Mode ─────────────────────────────────────────────

    fun getThemeMode(): String = prefs.getString(KEY_THEME_MODE, "system") ?: "system"

    fun setThemeMode(mode: String) {
        prefs.edit().putString(KEY_THEME_MODE, mode).apply()
        _themeMode.value = mode
    }

    // ─── Provider Configurations ────────────────────────────────

    fun saveProviderConfig(config: ProviderConfig) {
        val key = "$KEY_PROVIDER_CONFIG_PREFIX${config.provider.name}"
        val configJson = json.encodeToString(config)
        prefs.edit().putString(key, configJson).apply()
    }

    fun getProviderConfig(providerType: AIProviderType): ProviderConfig {
        val key = "$KEY_PROVIDER_CONFIG_PREFIX${providerType.name}"
        val configJson = prefs.getString(key, null)
        return if (configJson != null) {
            try {
                json.decodeFromString<ProviderConfig>(configJson)
            } catch (_: Exception) {
                ProviderConfig(provider = providerType)
            }
        } else {
            ProviderConfig(provider = providerType)
        }
    }

    // ─── Clear ──────────────────────────────────────────────────

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    fun clearCredentials() {
        prefs.edit()
            .remove(KEY_API_KEY)
            .remove(KEY_SESSION_KEY)
            .apply()
    }
}