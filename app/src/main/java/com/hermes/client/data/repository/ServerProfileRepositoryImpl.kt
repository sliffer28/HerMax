package com.hermes.client.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.hermes.client.data.local.ServerProfileDao
import com.hermes.client.data.local.ServerProfileEntity
import com.hermes.client.domain.model.*
import com.hermes.client.domain.repository.ServerProfileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServerProfileRepositoryImpl @Inject constructor(
    private val dao: ServerProfileDao,
    private val dataStore: DataStore<androidx.datastore.preferences.core.Preferences>
) : ServerProfileRepository {

    // ── Keys ─────────────────────────────────────────────────────────────
    companion object {
        val KEY_PROVIDER_ID      = stringPreferencesKey("chat_provider_id")
        val KEY_MODEL_ID         = stringPreferencesKey("chat_model_id")
        val KEY_TEMPERATURE      = floatPreferencesKey("chat_temperature")
        val KEY_TOP_P            = floatPreferencesKey("chat_top_p")
        val KEY_THINKING_ENABLED = booleanPreferencesKey("chat_thinking_enabled")
        val KEY_THINKING_LEVEL   = stringPreferencesKey("chat_thinking_level")
        val KEY_RESPONSE_EFFORT  = stringPreferencesKey("chat_response_effort")
    }

    private val scope = CoroutineScope(Dispatchers.IO)

    // ── ServerProfile CRUD ───────────────────────────────────────────────

    override fun getAllProfiles(): Flow<List<ServerProfile>> =
        dao.getAllProfiles().map { list -> list.map { it.toServerProfile() } }

    override suspend fun getActiveProfile(): ServerProfile? =
        dao.getActiveProfile()?.toServerProfile()

    override suspend fun createProfile(profile: ServerProfile): ServerProfile {
        dao.insertProfile(profile.toProfileEntity()); return profile
    }

    override suspend fun updateProfile(profile: ServerProfile) =
        dao.updateProfile(profile.toProfileEntity())

    override suspend fun deleteProfile(id: String) = dao.deleteProfile(id)

    override suspend fun setActiveProfile(id: String) = dao.setActiveProfile(id)

    // ── DataStore-backed chat header preferences ─────────────────────────

    private val safeData: Flow<Preferences> = dataStore.data.catch { exception ->
        if (exception is java.io.IOException) {
            emit(emptyPreferences())
        } else {
            throw exception
        }
    }

    override val currentProviderId: Flow<String?> =
        safeData.map { it[KEY_PROVIDER_ID] }

    override val currentModelId: Flow<String?> =
        safeData.map { it[KEY_MODEL_ID] }

    override val currentTemperature: Flow<Float?> =
        safeData.map { it[KEY_TEMPERATURE] }

    override val currentTopP: Flow<Float?> =
        safeData.map { it[KEY_TOP_P] }

    override val currentThinkingEnabled: Flow<Boolean?> =
        safeData.map { it[KEY_THINKING_ENABLED] }

    override val currentThinkingLevel: Flow<ThinkingLevel?> =
        safeData.map { prefs ->
            prefs[KEY_THINKING_LEVEL]?.let { runCatching { ThinkingLevel.valueOf(it) }.getOrNull() }
        }

    override val currentResponseEffort: Flow<ResponseEffortLevel?> =
        safeData.map { prefs ->
            prefs[KEY_RESPONSE_EFFORT]?.let { runCatching { ResponseEffortLevel.valueOf(it) }.getOrNull() }
        }

    override fun updateProviderId(id: String) = edit { it[KEY_PROVIDER_ID] = id }
    override fun updateModelId(id: String) = edit { it[KEY_MODEL_ID] = id }
    override fun updateTemperature(value: Float) = edit { it[KEY_TEMPERATURE] = value }
    override fun updateTopP(value: Float) = edit { it[KEY_TOP_P] = value }
    override fun updateThinkingEnabled(enabled: Boolean) = edit { it[KEY_THINKING_ENABLED] = enabled }
    override fun updateThinkingLevel(level: ThinkingLevel) = edit { it[KEY_THINKING_LEVEL] = level.name }
    override fun updateResponseEffort(effort: ResponseEffortLevel?) = edit {
        if (effort != null) it[KEY_RESPONSE_EFFORT] = effort.name
        else it.remove(KEY_RESPONSE_EFFORT)
    }

    private fun edit(block: (MutablePreferences) -> Unit) {
        scope.launch {
            try {
                dataStore.edit { block(it) }
            } catch (_: Exception) {}
        }
    }

    // ── Mappers ──────────────────────────────────────────────────────────

    private fun ServerProfileEntity.toServerProfile() = ServerProfile(
        id = id,
        name = name,
        url = url,
        authType = AuthType.valueOf(authType),
        isActive = isActive,
        lastConnectionState = ConnectionState.valueOf(lastConnectionState)
    )

    private fun ServerProfile.toProfileEntity() = ServerProfileEntity(
        id = id,
        name = name,
        url = url,
        authType = authType.name,
        isActive = isActive,
        lastConnectionState = lastConnectionState.name
    )
}
