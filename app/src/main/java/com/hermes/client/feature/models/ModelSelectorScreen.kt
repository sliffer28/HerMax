package com.hermes.client.feature.models

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hermes.client.data.provider.AIProviderRegistry
import com.hermes.client.data.security.SecureStorage
import com.hermes.client.domain.model.AIProviderType
import com.hermes.client.domain.model.Model
import com.hermes.client.domain.model.ResponseEffortLevel
import com.hermes.client.domain.model.ThinkingLevel
import com.hermes.client.domain.repository.ServerProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AISource(val displayName: String) {
    HERMES("Hermes Agent"),
    CLOUD("Cloud Models")
}

data class ModelSelectorUiState(
    val selectedSource: AISource = AISource.HERMES,
    val selectedProvider: AIProviderType = AIProviderType.HERMES_AGENT,
    val models: List<Model> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedModelId: String? = null,
    val isKeyConfigured: Boolean = true
)

@HiltViewModel
class ModelSelectorViewModel @Inject constructor(
    private val aiProviderRegistry: AIProviderRegistry,
    private val serverProfileRepository: ServerProfileRepository,
    private val secureStorage: SecureStorage
) : ViewModel() {

    private val _uiState = MutableStateFlow(ModelSelectorUiState())
    val uiState: StateFlow<ModelSelectorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val currentProviderStr = serverProfileRepository.currentProviderId.firstOrNull()
                val currentModelStr = serverProfileRepository.currentModelId.firstOrNull()

                val initialProvider = currentProviderStr?.let { str ->
                    AIProviderType.entries.firstOrNull { it.name.equals(str, ignoreCase = true) }
                } ?: AIProviderType.HERMES_AGENT

                val initialSource = if (initialProvider == AIProviderType.HERMES_AGENT) AISource.HERMES else AISource.CLOUD

                _uiState.update {
                    it.copy(
                        selectedSource = initialSource,
                        selectedProvider = initialProvider,
                        selectedModelId = currentModelStr
                    )
                }
                loadModels(initialProvider)
            } catch (_: Exception) {
                loadModels(AIProviderType.HERMES_AGENT)
            }
        }
    }

    fun selectSource(source: AISource) {
        val targetProvider = when (source) {
            AISource.HERMES -> AIProviderType.HERMES_AGENT
            AISource.CLOUD -> {
                // If current provider is Hermes, default to Google AI Studio or OpenRouter
                if (_uiState.value.selectedProvider == AIProviderType.HERMES_AGENT) {
                    AIProviderType.GOOGLE_AI
                } else {
                    _uiState.value.selectedProvider
                }
            }
        }
        _uiState.update { it.copy(selectedSource = source, selectedProvider = targetProvider) }
        loadModels(targetProvider)
    }

    fun selectProvider(provider: AIProviderType) {
        val source = if (provider == AIProviderType.HERMES_AGENT) AISource.HERMES else AISource.CLOUD
        _uiState.update { it.copy(selectedSource = source, selectedProvider = provider) }
        loadModels(provider)
    }

    fun loadModels(provider: AIProviderType = _uiState.value.selectedProvider) {
        viewModelScope.launch {
            val isConfigured = aiProviderRegistry.isConfigured(provider)
            if (!isConfigured) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = null,
                        models = emptyList(),
                        isKeyConfigured = false
                    )
                }
                return@launch
            }

            _uiState.update { it.copy(isLoading = true, error = null, isKeyConfigured = true) }

            val aiProvider = aiProviderRegistry.getProvider(provider)
            aiProvider.getModels()
                .onSuccess { models ->
                    _uiState.update {
                        it.copy(models = models, isLoading = false, error = null)
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "Failed to load models for ${provider.displayName}"
                        )
                    }
                }
        }
    }

    fun selectModel(model: Model) {
        _uiState.update { it.copy(selectedModelId = model.id) }
        serverProfileRepository.updateModelId(model.id)
        serverProfileRepository.updateProviderId(_uiState.value.selectedProvider.name)
    }

    fun setManualModel(modelId: String) {
        if (modelId.isBlank()) return
        val currentProvider = _uiState.value.selectedProvider
        val config = secureStorage.getProviderConfig(currentProvider)
        secureStorage.saveProviderConfig(config.copy(manualModelId = modelId.trim()))
        val providerTitle = if (currentProvider == AIProviderType.CUSTOM && !config.customName.isNullOrBlank()) {
            config.customName
        } else {
            currentProvider.displayName
        }
        val newModel = Model(
            id = modelId.trim(),
            name = "${modelId.trim()} (Manual)",
            provider = providerTitle,
            isAvailable = true
        )
        _uiState.update { state ->
            val existing = state.models.filter { it.id != modelId.trim() }
            state.copy(
                models = listOf(newModel) + existing,
                selectedModelId = newModel.id,
                error = null
            )
        }
        selectModel(newModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelectorScreen(
    currentModelId: String?,
    onModelSelected: (Model) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToSettings: (() -> Unit)? = null,
    provider: AIProviderType? = null,
    temperature: Float = 0.7f,
    topP: Float = 1.0f,
    thinkingEnabled: Boolean = false,
    thinkingLevel: ThinkingLevel = ThinkingLevel.OFF,
    responseEffort: ResponseEffortLevel? = null,
    onTemperatureChange: (Float) -> Unit = {},
    onTopPChange: (Float) -> Unit = {},
    onThinkingEnabledChange: (Boolean) -> Unit = {},
    onThinkingLevelChange: (ThinkingLevel) -> Unit = {},
    onResponseEffortChange: (ResponseEffortLevel?) -> Unit = {},
    viewModel: ModelSelectorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select Model") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        @Suppress("DEPRECATION")
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Outlined.Tune, contentDescription = "Response settings")
                    }
                    IconButton(onClick = { viewModel.loadModels() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { paddingValues ->
        ModelListContent(
            uiState = uiState,
            currentModelId = currentModelId,
            onModelSelected = {
                viewModel.selectModel(it)
                onModelSelected(it)
            },
            onSourceSelected = { viewModel.selectSource(it) },
            onProviderSelected = { viewModel.selectProvider(it) },
            onRetry = { viewModel.loadModels() },
            onNavigateToSettings = onNavigateToSettings,
            onManualModelSubmit = { viewModel.setManualModel(it) },
            modifier = Modifier.padding(paddingValues)
        )
    }

    if (showSettings) {
        ModelSettingsSheet(
            provider = uiState.selectedProvider,
            temperature = temperature,
            topP = topP,
            thinkingEnabled = thinkingEnabled,
            thinkingLevel = thinkingLevel,
            responseEffort = responseEffort,
            onTemperatureChange = onTemperatureChange,
            onTopPChange = onTopPChange,
            onThinkingEnabledChange = onThinkingEnabledChange,
            onThinkingLevelChange = onThinkingLevelChange,
            onResponseEffortChange = onResponseEffortChange,
            onDismiss = { showSettings = false }
        )
    }
}

/**
 * Pure model list content with source & provider selectors.
 * Suitable for both screen and BottomSheet usage.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelListContent(
    uiState: ModelSelectorUiState,
    currentModelId: String?,
    onModelSelected: (Model) -> Unit,
    onSourceSelected: (AISource) -> Unit = {},
    onProviderSelected: (AIProviderType) -> Unit = {},
    onRetry: () -> Unit,
    onNavigateToSettings: (() -> Unit)? = null,
    onManualModelSubmit: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Source tabs: Hermes Agent vs Cloud Models
        TabRow(
            selectedTabIndex = if (uiState.selectedSource == AISource.HERMES) 0 else 1,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = uiState.selectedSource == AISource.HERMES,
                onClick = { onSourceSelected(AISource.HERMES) },
                text = { Text("🖥 Hermes Agent") }
            )
            Tab(
                selected = uiState.selectedSource == AISource.CLOUD,
                onClick = { onSourceSelected(AISource.CLOUD) },
                text = { Text("☁ Cloud Models") }
            )
        }

        // Provider chips when Cloud Models is selected
        if (uiState.selectedSource == AISource.CLOUD) {
            val cloudProviders = remember {
                listOf(
                    AIProviderType.GOOGLE_AI,
                    AIProviderType.OPENROUTER,
                    AIProviderType.ANTHROPIC,
                    AIProviderType.OPENAI,
                    AIProviderType.OLLAMA,
                    AIProviderType.OLLAMA_CLOUD,
                    AIProviderType.NVIDIA,
                    AIProviderType.CUSTOM
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                cloudProviders.forEach { provider ->
                    FilterChip(
                        selected = uiState.selectedProvider == provider,
                        onClick = { onProviderSelected(provider) },
                        label = { Text(provider.displayName) },
                        leadingIcon = if (uiState.selectedProvider == provider) {
                            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // Content body based on state
        when {
            // Missing API Key / Base URL
            !uiState.isKeyConfigured -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = if (uiState.selectedProvider == AIProviderType.CUSTOM) Icons.Outlined.Dns else Icons.Outlined.Key,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = if (uiState.selectedProvider == AIProviderType.CUSTOM) "Server URL Required" else "API Key Required",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = when (uiState.selectedProvider) {
                                AIProviderType.CUSTOM -> "Please enter the Server URL for Custom Provider in Settings to use its models."
                                AIProviderType.OLLAMA_CLOUD -> "Please enter your Ollama Cloud API key in Settings to browse and use models."
                                else -> "Please enter an API key for ${uiState.selectedProvider.displayName} to browse and use its models."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        if (onNavigateToSettings != null) {
                            Spacer(Modifier.height(20.dp))
                            Button(onClick = onNavigateToSettings) {
                                Icon(Icons.Filled.Settings, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Configure in Settings")
                            }
                        }
                    }
                }
            }

            // Loading state
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Discovering ${uiState.selectedProvider.displayName} models...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Error state
            uiState.error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.ErrorOutline,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            uiState.error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(16.dp))

                        // If Custom Provider, also offer manual model entry here
                        if (uiState.selectedProvider == AIProviderType.CUSTOM) {
                            ManualModelEntryCard(onSubmit = { onManualModelSubmit?.invoke(it) })
                            Spacer(Modifier.height(12.dp))
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = onRetry) {
                                Icon(Icons.Filled.Refresh, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Retry")
                            }
                            if (uiState.selectedSource == AISource.HERMES) {
                                OutlinedButton(onClick = { onSourceSelected(AISource.CLOUD) }) {
                                    Icon(Icons.Filled.Cloud, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Switch to Cloud")
                                }
                            }
                        }
                    }
                }
            }

            // Empty state
            uiState.models.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "No models found for ${uiState.selectedProvider.displayName}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (uiState.selectedProvider == AIProviderType.CUSTOM) {
                        Spacer(Modifier.height(16.dp))
                        ManualModelEntryCard(onSubmit = { onManualModelSubmit?.invoke(it) })
                    }
                }
            }

            // Model list
            else -> {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (uiState.selectedProvider == AIProviderType.CUSTOM) {
                        ManualModelEntryCard(onSubmit = { onManualModelSubmit?.invoke(it) })
                    }
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(uiState.models, key = { it.id }) { model ->
                            ModelItem(
                                model = model,
                                isSelected = model.id == (currentModelId ?: uiState.selectedModelId),
                                onClick = { onModelSelected(model) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ManualModelEntryCard(onSubmit: (String) -> Unit) {
    var manualInput by remember { mutableStateOf("") }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "Manual Model Entry",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = manualInput,
                    onValueChange = { manualInput = it },
                    placeholder = { Text("e.g., qwen2.5-72b, mistral, llama3") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )
                Button(
                    onClick = {
                        if (manualInput.isNotBlank()) {
                            onSubmit(manualInput.trim())
                            manualInput = ""
                        }
                    },
                    enabled = manualInput.isNotBlank(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Select")
                }
            }
        }
    }
}

@Composable
private fun ModelItem(
    model: Model,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                model.name,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        },
        supportingContent = {
            Column {
                if (model.provider.isNotBlank()) {
                    Text(
                        model.provider,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    if (model.contextWindow != null) {
                        AssistChip(
                            onClick = {},
                            label = { Text("${model.contextWindow / 1000}K ctx") },
                            modifier = Modifier.height(24.dp)
                        )
                    }
                    model.capabilities.take(3).forEach { cap ->
                        AssistChip(
                            onClick = {},
                            label = { Text(cap) },
                            modifier = Modifier.height(24.dp)
                        )
                    }
                }
            }
        },
        leadingContent = {
            RadioButton(
                selected = isSelected,
                onClick = onClick
            )
        },
        trailingContent = {
            if (!model.isAvailable) {
                Icon(
                    Icons.Filled.CloudOff,
                    contentDescription = "Unavailable",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
        colors = ListItemDefaults.colors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surface
        )
    )
}
