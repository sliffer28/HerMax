package com.hermes.client.feature.settings

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hermes.client.data.provider.AIProviderRegistry
import com.hermes.client.data.security.SecureStorage
import com.hermes.client.domain.model.AIProviderType
import com.hermes.client.domain.model.ConnectionTestResult
import com.hermes.client.domain.model.ProviderConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── ViewModel ─────────────────────────────────────────────────────────────────

data class ProvidersUiState(
    val configs: Map<AIProviderType, ProviderConfig> = emptyMap(),
    val editingProvider: AIProviderType? = null,
    val savedMessage: String? = null,
    val testingProviders: Set<AIProviderType> = emptySet(),
    val testResults: Map<AIProviderType, ConnectionTestResult> = emptyMap()
)

@HiltViewModel
class ProvidersViewModel @Inject constructor(
    private val secureStorage: SecureStorage,
    private val aiProviderRegistry: AIProviderRegistry
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProvidersUiState())
    val uiState: StateFlow<ProvidersUiState> = _uiState

    init {
        val configs = AIProviderType.entries.associateWith { secureStorage.getProviderConfig(it) }
        _uiState.update { it.copy(configs = configs) }
    }

    fun saveConfig(config: ProviderConfig) {
        // Auto-enable if API key or base URL is provided
        val hasKeyOrUrl = !config.apiKey.isNullOrBlank() || (!config.baseUrl.isNullOrBlank() && config.provider == AIProviderType.CUSTOM)
        val finalConfig = if (hasKeyOrUrl && !config.isEnabled) {
            config.copy(isEnabled = true)
        } else {
            config
        }

        secureStorage.saveProviderConfig(finalConfig)
        _uiState.update { state ->
            state.copy(
                configs = state.configs + (finalConfig.provider to finalConfig),
                savedMessage = "${finalConfig.provider.displayName} saved"
            )
        }
    }

    fun clearCredentials(provider: AIProviderType) {
        val current = _uiState.value.configs[provider] ?: ProviderConfig(provider = provider)
        val cleared = current.copy(
            isEnabled = false,
            apiKey = null,
            organizationId = null,
            lastTestSuccess = null,
            lastTested = null,
            discoveredModels = emptyList()
        )
        secureStorage.saveProviderConfig(cleared)
        _uiState.update { state ->
            state.copy(
                configs = state.configs + (provider to cleared),
                testResults = state.testResults - provider,
                savedMessage = "Credentials cleared for ${provider.displayName}"
            )
        }
    }

    fun testConnection(provider: AIProviderType) {
        viewModelScope.launch {
            _uiState.update { it.copy(testingProviders = it.testingProviders + provider) }

            val aiProvider = aiProviderRegistry.getProvider(provider)
            val result = aiProvider.testConnection().getOrElse {
                ConnectionTestResult.Failure("Connection failed: ${it.message}")
            }

            // Update storage with test outcome
            val current = _uiState.value.configs[provider] ?: ProviderConfig(provider = provider)
            val isSuccess = result is ConnectionTestResult.Success
            val updated = current.copy(
                lastTested = System.currentTimeMillis(),
                lastTestSuccess = isSuccess
            )
            secureStorage.saveProviderConfig(updated)

            _uiState.update { state ->
                state.copy(
                    configs = state.configs + (provider to updated),
                    testingProviders = state.testingProviders - provider,
                    testResults = state.testResults + (provider to result)
                )
            }
        }
    }

    fun clearSavedMessage() {
        _uiState.update { it.copy(savedMessage = null) }
    }

    fun setEditing(provider: AIProviderType?) {
        _uiState.update { it.copy(editingProvider = provider) }
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvidersScreen(
    onNavigateBack: () -> Unit,
    viewModel: ProvidersViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.savedMessage) {
        uiState.savedMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSavedMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("AI Providers") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        @Suppress("DEPRECATION")
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp)
        ) {
            Text(
                "Configure cloud AI providers and Hermes. API keys are securely encrypted on-device and never sent to your PC Hermes server.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            AIProviderType.entries.forEach { provider ->
                val config = uiState.configs[provider] ?: ProviderConfig(provider = provider)
                val isEditing = uiState.editingProvider == provider
                val isTesting = uiState.testingProviders.contains(provider)
                val testResult = uiState.testResults[provider]

                ProviderCard(
                    provider = provider,
                    config = config,
                    isEditing = isEditing,
                    isTesting = isTesting,
                    testResult = testResult,
                    onEditToggle = {
                        viewModel.setEditing(if (isEditing) null else provider)
                    },
                    onSave = { updatedConfig ->
                        viewModel.saveConfig(updatedConfig)
                        viewModel.setEditing(null)
                    },
                    onTest = {
                        viewModel.testConnection(provider)
                    },
                    onClearCredentials = {
                        viewModel.clearCredentials(provider)
                        viewModel.setEditing(null)
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ── Provider Card ─────────────────────────────────────────────────────────────

@Composable
private fun ProviderCard(
    provider: AIProviderType,
    config: ProviderConfig,
    isEditing: Boolean,
    isTesting: Boolean,
    testResult: ConnectionTestResult?,
    onEditToggle: () -> Unit,
    onSave: (ProviderConfig) -> Unit,
    onTest: () -> Unit,
    onClearCredentials: () -> Unit
) {
    var enabled by remember(config) { mutableStateOf(config.isEnabled) }
    var apiKey by remember(config) { mutableStateOf(config.apiKey ?: "") }
    var baseUrl by remember(config) { mutableStateOf(config.baseUrl ?: "") }
    var orgId by remember(config) { mutableStateOf(config.organizationId ?: "") }
    var customName by remember(config) { mutableStateOf(config.customName ?: "") }
    var manualModelId by remember(config) { mutableStateOf(config.manualModelId ?: "") }
    var showKey by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Header row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (enabled || (!provider.requiresApiKey && provider != AIProviderType.CUSTOM))
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = providerIcon(provider),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = if (enabled || (!provider.requiresApiKey && provider != AIProviderType.CUSTOM))
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                val title = if (provider == AIProviderType.CUSTOM && !config.customName.isNullOrBlank()) {
                    "${provider.displayName} (${config.customName})"
                } else {
                    provider.displayName
                }
                Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = when (provider) {
                        AIProviderType.HERMES_AGENT, AIProviderType.OLLAMA -> "Local PC server"
                        AIProviderType.CUSTOM -> if (!config.baseUrl.isNullOrBlank()) "Configured (${config.baseUrl})" else "Not configured"
                        else -> if (!config.apiKey.isNullOrBlank()) "API Key configured" else "Not configured"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (provider == AIProviderType.HERMES_AGENT || provider == AIProviderType.OLLAMA || !config.apiKey.isNullOrBlank() || !config.baseUrl.isNullOrBlank())
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (provider == AIProviderType.HERMES_AGENT || provider == AIProviderType.OLLAMA) {
                AssistChip(
                    onClick = {},
                    label = { Text("Local") },
                    leadingIcon = {
                        Icon(Icons.Filled.Computer, contentDescription = null, modifier = Modifier.size(14.dp))
                    }
                )
            } else {
                Switch(
                    checked = enabled,
                    onCheckedChange = { isChecked ->
                        enabled = isChecked
                        onSave(config.copy(isEnabled = isChecked))
                    }
                )
            }
        }

        // Status indicator row
        Spacer(Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isTesting) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(6.dp))
                Text("Testing connection...", style = MaterialTheme.typography.bodySmall)
            } else if (testResult != null) {
                when (testResult) {
                    is ConnectionTestResult.Success -> {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Connected successfully (${testResult.modelsFound} models found)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                    is ConnectionTestResult.Failure -> {
                        Icon(Icons.Filled.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(testResult.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            } else if (config.lastTestSuccess == true) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Connection verified", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }

        // Expand/collapse config section
        AnimatedVisibility(
            visible = isEditing,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Provider Name (Custom Provider)
                if (provider == AIProviderType.CUSTOM) {
                    OutlinedTextField(
                        value = customName,
                        onValueChange = { customName = it },
                        label = { Text("Provider Name") },
                        placeholder = { Text("e.g. LM Studio, vLLM, Private Cloud") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                // Base URL (Ollama Local, Ollama Cloud, Custom Provider, NVIDIA)
                if (provider == AIProviderType.OLLAMA || provider == AIProviderType.OLLAMA_CLOUD || provider == AIProviderType.CUSTOM || provider == AIProviderType.NVIDIA) {
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = {
                            Text(
                                when (provider) {
                                    AIProviderType.OLLAMA -> "Server URL"
                                    AIProviderType.OLLAMA_CLOUD -> "Server URL (optional)"
                                    AIProviderType.CUSTOM -> "Base URL (OpenAI-compatible)"
                                    else -> "Server URL"
                                }
                            )
                        },
                        placeholder = {
                            Text(
                                when (provider) {
                                    AIProviderType.OLLAMA -> "http://192.168.1.22:11434"
                                    AIProviderType.OLLAMA_CLOUD -> "https://ollama.com/v1"
                                    AIProviderType.CUSTOM -> "https://example.com/v1"
                                    else -> "https://integrate.api.nvidia.com/v1"
                                }
                            )
                        },
                        supportingText = if (provider == AIProviderType.CUSTOM) {
                            { Text("Normalized automatically; works with or without /v1") }
                        } else null,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                    )
                }

                // API Key field
                if (provider.requiresApiKey || provider == AIProviderType.CUSTOM) {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = {
                            Text(
                                when (provider) {
                                    AIProviderType.OLLAMA_CLOUD -> "Ollama Cloud API Key"
                                    AIProviderType.CUSTOM -> "API Key (optional)"
                                    else -> "API Key"
                                }
                            )
                        },
                        placeholder = if (provider == AIProviderType.CUSTOM) {
                            { Text("Optional Bearer token") }
                        } else null,
                        supportingText = if (provider == AIProviderType.OLLAMA_CLOUD) {
                            { Text("Generated at ollama.com/settings/keys") }
                        } else null,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { showKey = !showKey }) {
                                Icon(
                                    if (showKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = if (showKey) "Hide key" else "Show key"
                                )
                            }
                        },
                        singleLine = true
                    )
                }

                // Manual Model ID (Custom Provider)
                if (provider == AIProviderType.CUSTOM) {
                    OutlinedTextField(
                        value = manualModelId,
                        onValueChange = { manualModelId = it },
                        label = { Text("Manual Model ID (optional)") },
                        placeholder = { Text("e.g., qwen2.5-72b, mistral-large, default") },
                        supportingText = { Text("Used if /models discovery is not supported by endpoint") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                // Organization ID (OpenAI, Custom Provider)
                if (provider == AIProviderType.OPENAI || provider == AIProviderType.CUSTOM) {
                    OutlinedTextField(
                        value = orgId,
                        onValueChange = { orgId = it },
                        label = { Text("Organization / Project ID (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                // Action buttons: Test, Clear, Cancel, Save
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if ((provider.requiresApiKey && !config.apiKey.isNullOrBlank()) || (!config.apiKey.isNullOrBlank() || !config.baseUrl.isNullOrBlank())) {
                        TextButton(
                            onClick = onClearCredentials,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Clear")
                        }
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                onSave(
                                    config.copy(
                                        isEnabled = enabled,
                                        apiKey = apiKey.ifBlank { null },
                                        baseUrl = baseUrl.ifBlank { null },
                                        organizationId = orgId.ifBlank { null },
                                        customName = customName.ifBlank { null },
                                        manualModelId = manualModelId.ifBlank { null }
                                    )
                                )
                                onTest()
                            },
                            enabled = !isTesting
                        ) {
                            Text("Test")
                        }
                        Button(onClick = {
                            onSave(
                                config.copy(
                                    isEnabled = enabled,
                                    apiKey = apiKey.ifBlank { null },
                                    baseUrl = baseUrl.ifBlank { null },
                                    organizationId = orgId.ifBlank { null },
                                    customName = customName.ifBlank { null },
                                    manualModelId = manualModelId.ifBlank { null }
                                )
                            )
                        }) {
                            Text("Save")
                        }
                    }
                }
            }
        }

        // Action row when not editing
        if (!isEditing) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onEditToggle,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Configure", style = MaterialTheme.typography.labelMedium)
                }

                if (!provider.requiresApiKey || !config.apiKey.isNullOrBlank() || (provider == AIProviderType.CUSTOM && !config.baseUrl.isNullOrBlank())) {
                    TextButton(
                        onClick = onTest,
                        enabled = !isTesting,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Icon(Icons.Outlined.NetworkCheck, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Test Connection", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

private fun providerIcon(provider: AIProviderType) = when (provider) {
    AIProviderType.HERMES_AGENT  -> Icons.Outlined.Dns
    AIProviderType.OPENROUTER    -> Icons.Outlined.Cloud
    AIProviderType.GOOGLE_AI     -> Icons.Outlined.SmartToy
    AIProviderType.ANTHROPIC     -> Icons.Outlined.Psychology
    AIProviderType.OPENAI        -> Icons.Outlined.SmartToy
    AIProviderType.OLLAMA        -> Icons.Outlined.Memory
    AIProviderType.OLLAMA_CLOUD  -> Icons.Outlined.Cloud
    AIProviderType.NVIDIA        -> Icons.Outlined.Memory
    AIProviderType.CUSTOM        -> Icons.Outlined.Tune
}
