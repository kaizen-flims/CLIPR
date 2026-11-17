package com.clipr.app.data.security

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.clipr.app.core.model.ApiKeyStatus
import com.clipr.app.core.model.GeminiModelOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.cliprByokDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "clipr_byok_credentials.preferences_pb"
)

data class StoredByokState(
    val hasStoredKey: Boolean = false,
    val maskedKeyPreview: String = "Not Configured",
    val selectedModel: GeminiModelOption = GeminiModelOption.GEMINI_2_5_FLASH,
    val isOnboardingDismissed: Boolean = false,
    val lastValidatedEpochMs: Long = 0L,
    val status: ApiKeyStatus = ApiKeyStatus.UNCONFIGURED
)

/**
 * Manages encrypted storage, retrieval, replacement, and deletion of the user's Gemini API key.
 * Plaintext keys never touch disk; only the AES-256-GCM ciphertext and a 4+4 masked preview are stored.
 */
class ByokCredentialRepository(
    private val appContext: Context,
    private val cryptoEngine: KeystoreCryptoEngine = KeystoreCryptoEngine()
) {

    val byokStateFlow: Flow<StoredByokState> = appContext.cliprByokDataStore.data.map { prefs ->
        val encryptedEnvelope = prefs[KEY_ENCRYPTED_API_KEY].orEmpty()
        val hasKey = encryptedEnvelope.isNotBlank()
        val masked = prefs[KEY_MASKED_PREVIEW] ?: "Not Configured"
        val model = GeminiModelOption.fromModelId(prefs[KEY_SELECTED_MODEL_ID])
        val onboardingDone = prefs[KEY_ONBOARDING_COMPLETED] ?: false
        val validatedAt = prefs[KEY_LAST_VALIDATED_MS] ?: 0L

        StoredByokState(
            hasStoredKey = hasKey,
            maskedKeyPreview = if (hasKey) masked else "Not Configured",
            selectedModel = model,
            isOnboardingDismissed = onboardingDone || hasKey,
            lastValidatedEpochMs = validatedAt,
            status = if (hasKey) ApiKeyStatus.VALID else ApiKeyStatus.UNCONFIGURED
        )
    }

    /**
     * Encrypts the validated API key via AndroidKeyStore and persists the ciphertext in DataStore.
     */
    suspend fun saveValidatedApiKey(
        rawApiKey: String,
        selectedModel: GeminiModelOption
    ) {
        val trimmed = rawApiKey.trim()
        val encryptedEnvelope = cryptoEngine.encrypt(trimmed)
        val maskedPreview = LogRedactor.maskApiKey(trimmed)

        appContext.cliprByokDataStore.edit { prefs ->
            prefs[KEY_ENCRYPTED_API_KEY] = encryptedEnvelope
            prefs[KEY_MASKED_PREVIEW] = maskedPreview
            prefs[KEY_SELECTED_MODEL_ID] = selectedModel.modelId
            prefs[KEY_ONBOARDING_COMPLETED] = true
            prefs[KEY_LAST_VALIDATED_MS] = System.currentTimeMillis()
        }
    }

    /**
     * Decrypts and returns the user's Gemini API key strictly in memory for an outbound Gemini API request.
     */
    suspend fun getDecryptedApiKey(): String? {
        val prefs = appContext.cliprByokDataStore.data.first()
        val envelope = prefs[KEY_ENCRYPTED_API_KEY].orEmpty()
        if (envelope.isBlank()) return null
        return cryptoEngine.decrypt(envelope)
    }

    suspend fun updateSelectedModel(model: GeminiModelOption) {
        appContext.cliprByokDataStore.edit { prefs ->
            prefs[KEY_SELECTED_MODEL_ID] = model.modelId
        }
    }

    suspend fun markOnboardingSkipped() {
        appContext.cliprByokDataStore.edit { prefs ->
            prefs[KEY_ONBOARDING_COMPLETED] = true
        }
    }

    /**
     * Removes the encrypted ciphertext from DataStore AND destroys the AndroidKeyStore master key.
     */
    suspend fun removeAndWipeApiKey() {
        cryptoEngine.deleteMasterKey()
        appContext.cliprByokDataStore.edit { prefs ->
            prefs.remove(KEY_ENCRYPTED_API_KEY)
            prefs.remove(KEY_MASKED_PREVIEW)
            prefs.remove(KEY_LAST_VALIDATED_MS)
        }
    }

    companion object {
        private val KEY_ENCRYPTED_API_KEY = stringPreferencesKey("encrypted_gemini_api_key")
        private val KEY_MASKED_PREVIEW = stringPreferencesKey("masked_key_preview")
        private val KEY_SELECTED_MODEL_ID = stringPreferencesKey("selected_gemini_model_id")
        private val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("byok_onboarding_completed")
        private val KEY_LAST_VALIDATED_MS = longPreferencesKey("last_validated_epoch_ms")
    }
}
