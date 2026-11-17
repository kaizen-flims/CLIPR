package com.clipr.app.ui.screens.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clipr.app.core.model.ApiKeyStatus
import com.clipr.app.core.model.GeminiModelOption
import com.clipr.app.data.remote.gemini.GeminiRestClient
import com.clipr.app.data.security.ByokCredentialRepository
import com.clipr.app.data.security.LogRedactor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val apiKeyInput: String = "",
    val isApiKeyVisible: Boolean = false,
    val hasStoredKey: Boolean = false,
    val maskedKeyPreview: String = "Not Configured",
    val apiKeyStatus: ApiKeyStatus = ApiKeyStatus.UNCONFIGURED,
    val statusMessage: String? = null,
    val isErrorMessage: Boolean = false,
    val isOnboardingRequired: Boolean = false,
    val selectedModel: GeminiModelOption = GeminiModelOption.GEMINI_2_5_FLASH,
    val availableRemoteModels: List<String> = emptyList(),
    val preferHardwareEncoding: Boolean = true,
    val allowCpuFallback: Boolean = true
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val credentialRepository = ByokCredentialRepository(application.applicationContext)
    private val geminiRestClient = GeminiRestClient()

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            credentialRepository.byokStateFlow.collectLatest { stored ->
                _uiState.update { current ->
                    val resolvedStatus = when {
                        current.apiKeyStatus == ApiKeyStatus.VALIDATING -> ApiKeyStatus.VALIDATING
                        stored.hasStoredKey -> ApiKeyStatus.VALID
                        current.apiKeyStatus == ApiKeyStatus.INVALID ||
                            current.apiKeyStatus == ApiKeyStatus.QUOTA_EXCEEDED ||
                            current.apiKeyStatus == ApiKeyStatus.NETWORK_ERROR -> current.apiKeyStatus
                        else -> ApiKeyStatus.UNCONFIGURED
                    }
                    current.copy(
                        hasStoredKey = stored.hasStoredKey,
                        maskedKeyPreview = stored.maskedKeyPreview,
                        selectedModel = stored.selectedModel,
                        apiKeyStatus = resolvedStatus,
                        isOnboardingRequired = !stored.isOnboardingDismissed && !stored.hasStoredKey
                    )
                }
            }
        }
    }

    fun onApiKeyInputChanged(newInput: String) {
        _uiState.update {
            it.copy(
                apiKeyInput = newInput,
                statusMessage = null,
                isErrorMessage = false
            )
        }
    }

    fun toggleApiKeyVisibility() {
        _uiState.update { it.copy(isApiKeyVisible = !it.isApiKeyVisible) }
    }

    /**
     * Validates the entered Gemini API key against Google's `/v1beta/models` endpoint,
     * and upon verification encrypts it in Android Keystore.
     */
    fun validateAndSaveApiKey() {
        val candidateKey = _uiState.value.apiKeyInput.trim()
        val chosenModel = _uiState.value.selectedModel

        if (!LogRedactor.isPlausibleGeminiApiKeyFormat(candidateKey)) {
            _uiState.update {
                it.copy(
                    apiKeyStatus = ApiKeyStatus.INVALID,
                    statusMessage = "Invalid key format. Enter a valid Google AI Studio key starting with 'AIza'.",
                    isErrorMessage = true
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    apiKeyStatus = ApiKeyStatus.VALIDATING,
                    statusMessage = "Validating key with Google Gemini API...",
                    isErrorMessage = false
                )
            }

            val result = geminiRestClient.validateApiKey(
                rawApiKey = candidateKey,
                preferredModel = chosenModel
            )

            if (result.isSuccess) {
                credentialRepository.saveValidatedApiKey(
                    rawApiKey = candidateKey,
                    selectedModel = chosenModel
                )
                _uiState.update {
                    it.copy(
                        apiKeyInput = "",
                        isApiKeyVisible = false,
                        apiKeyStatus = ApiKeyStatus.VALID,
                        availableRemoteModels = result.availableModelIds,
                        statusMessage = "Gemini API key verified and encrypted in Android Keystore (${chosenModel.displayName} ready).",
                        isErrorMessage = false,
                        isOnboardingRequired = false
                    )
                }
            } else {
                val err = result.error
                _uiState.update {
                    it.copy(
                        apiKeyStatus = err?.mappedKeyStatus ?: ApiKeyStatus.INVALID,
                        statusMessage = err?.userMessage ?: "API key validation failed.",
                        isErrorMessage = true
                    )
                }
            }
        }
    }

    /**
     * Removes the stored key from DataStore and destroys the AndroidKeyStore AES master key.
     */
    fun removeAndWipeApiKey() {
        viewModelScope.launch {
            credentialRepository.removeAndWipeApiKey()
            _uiState.update {
                it.copy(
                    apiKeyInput = "",
                    hasStoredKey = false,
                    maskedKeyPreview = "Not Configured",
                    apiKeyStatus = ApiKeyStatus.UNCONFIGURED,
                    statusMessage = "API key removed and cryptographic master key wiped from Android Keystore.",
                    isErrorMessage = false
                )
            }
        }
    }

    fun skipOnboardingForNow() {
        viewModelScope.launch {
            credentialRepository.markOnboardingSkipped()
            _uiState.update { it.copy(isOnboardingRequired = false) }
        }
    }

    fun reopenOnboarding() {
        _uiState.update { it.copy(isOnboardingRequired = true) }
    }

    fun selectGeminiModel(model: GeminiModelOption) {
        viewModelScope.launch {
            credentialRepository.updateSelectedModel(model)
            _uiState.update {
                it.copy(
                    selectedModel = model,
                    statusMessage = "Active Gemini model set to ${model.displayName} (${model.modelId}).",
                    isErrorMessage = false
                )
            }
        }
    }

    fun setPreferHardwareEncoding(enabled: Boolean) {
        _uiState.update { it.copy(preferHardwareEncoding = enabled) }
    }

    fun setAllowCpuFallback(enabled: Boolean) {
        _uiState.update { it.copy(allowCpuFallback = enabled) }
    }
}
