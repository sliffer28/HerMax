package com.hermes.client.feature.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hermes.client.data.security.SecureStorage
import com.hermes.client.domain.repository.ConversationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val isDarkMode: Boolean? = null, // null = system
    val defaultModel: String? = null,
    val defaultProvider: String? = null,
    val enterToSend: Boolean = true,
    val showToolActivity: Boolean = true,
    val showTimestamps: Boolean = false,
    val autoScroll: Boolean = true,
    val markdownRendering: Boolean = true,
    val streamingEnabled: Boolean = true,
    val fontSize: Float = 14f,
    val serverUrl: String = "",
    val isConnected: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val secureStorage: SecureStorage,
    private val conversationRepository: ConversationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        _uiState.update {
            it.copy(
                serverUrl = secureStorage.getServerUrl() ?: "",
                defaultModel = secureStorage.getDefaultModel(),
                defaultProvider = secureStorage.getDefaultProvider()
            )
        }
    }

    val themeMode: StateFlow<String> = secureStorage.themeMode

    fun setThemeMode(mode: String) {
        secureStorage.setThemeMode(mode)
    }

    fun clearConversations() {
        viewModelScope.launch {
            conversationRepository.clearAllConversations()
        }
    }

    fun updateDarkMode(isDark: Boolean?) {
        val mode = when (isDark) {
            true -> "dark"
            false -> "light"
            null -> "system"
        }
        setThemeMode(mode)
    }

    fun updateEnterToSend(enabled: Boolean) {
        _uiState.update { it.copy(enterToSend = enabled) }
    }

    fun updateShowToolActivity(enabled: Boolean) {
        _uiState.update { it.copy(showToolActivity = enabled) }
    }

    fun updateShowTimestamps(enabled: Boolean) {
        _uiState.update { it.copy(showTimestamps = enabled) }
    }

    fun updateAutoScroll(enabled: Boolean) {
        _uiState.update { it.copy(autoScroll = enabled) }
    }

    fun updateStreaming(enabled: Boolean) {
        _uiState.update { it.copy(streamingEnabled = enabled) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToServerConnection: () -> Unit,
    onNavigateToProviders: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    var showThemeDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
        ) {
            // SERVER section
            SettingsSectionHeader("SERVER")
            ListItem(
                headlineContent = { Text("Server Connection") },
                supportingContent = {
                    Text(uiState.serverUrl.ifBlank { "Not configured" })
                },
                leadingContent = {
                    Icon(Icons.Outlined.Dns, contentDescription = null)
                },
                trailingContent = {
                    Icon(Icons.Filled.ChevronRight, contentDescription = null)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToServerConnection() }
            )

            HorizontalDivider()

            // AI section
            SettingsSectionHeader("AI")
            ListItem(
                headlineContent = { Text("Default Model") },
                supportingContent = {
                    Text(uiState.defaultModel ?: "hermes-agent")
                },
                leadingContent = {
                    Icon(Icons.Outlined.SmartToy, contentDescription = null)
                },
                trailingContent = {
                    Icon(Icons.Filled.ChevronRight, contentDescription = null)
                }
            )
            ListItem(
                headlineContent = { Text("AI Providers") },
                supportingContent = { Text("Configure cloud LLM providers & API keys") },
                leadingContent = {
                    Icon(Icons.Outlined.Cloud, contentDescription = null)
                },
                trailingContent = {
                    Icon(Icons.Filled.ChevronRight, contentDescription = null)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToProviders() }
            )
            SwitchListItem(
                title = "Streaming Responses",
                subtitle = "Show responses as they are generated",
                icon = Icons.Outlined.Stream,
                checked = uiState.streamingEnabled,
                onCheckedChange = { viewModel.updateStreaming(it) }
            )

            HorizontalDivider()

            // CHAT section
            SettingsSectionHeader("CHAT")
            ListItem(
                headlineContent = { Text("Theme") },
                supportingContent = {
                    Text(
                        when (themeMode) {
                            "dark" -> "Dark"
                            "light" -> "Light"
                            else -> "System"
                        }
                    )
                },
                leadingContent = {
                    Icon(Icons.Outlined.DarkMode, contentDescription = null)
                },
                trailingContent = {
                    Icon(Icons.Filled.ChevronRight, contentDescription = null)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showThemeDialog = true }
            )
            SwitchListItem(
                title = "Show Tool Activity",
                subtitle = "Display agent tool executions",
                icon = Icons.Outlined.Build,
                checked = uiState.showToolActivity,
                onCheckedChange = { viewModel.updateShowToolActivity(it) }
            )
            SwitchListItem(
                title = "Show Timestamps",
                subtitle = "Display message timestamps",
                icon = Icons.Outlined.Schedule,
                checked = uiState.showTimestamps,
                onCheckedChange = { viewModel.updateShowTimestamps(it) }
            )
            SwitchListItem(
                title = "Auto-scroll",
                subtitle = "Scroll to new messages automatically",
                icon = Icons.Outlined.VerticalAlignBottom,
                checked = uiState.autoScroll,
                onCheckedChange = { viewModel.updateAutoScroll(it) }
            )

            HorizontalDivider()

            // DATA section
            SettingsSectionHeader("DATA")
            ListItem(
                headlineContent = {
                    Text("Clear All Conversations", color = MaterialTheme.colorScheme.error)
                },
                leadingContent = {
                    Icon(
                        Icons.Outlined.DeleteSweep,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showClearDialog = true }
            )

            HorizontalDivider()

            // ABOUT section
            SettingsSectionHeader("ABOUT")
            ListItem(
                headlineContent = { Text("Version") },
                supportingContent = { Text("1.0.0") },
                leadingContent = { Icon(Icons.Outlined.Info, contentDescription = null) },
                trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) }
            )
            ListItem(
                headlineContent = { Text("HerMax") },
                supportingContent = { Text("Mobile interface for Hermes agentic server and Cloud LLMs") },
                leadingContent = {
                    Image(
                        painter = painterResource(com.hermes.client.R.drawable.hermax_logo),
                        contentDescription = "HerMax",
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                    )
                },
                trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) }
            )

            Spacer(Modifier.height(32.dp))
        }
    }

    // Theme selection dialog
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Theme") },
            text = {
                Column {
                    val options = listOf(
                        "system" to "System",
                        "dark" to "Dark",
                        "light" to "Light"
                    )
                    options.forEach { (key, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setThemeMode(key)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (themeMode == key),
                                onClick = {
                                    viewModel.setThemeMode(key)
                                    showThemeDialog = false
                                }
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Clear conversations confirmation dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear All Conversations?") },
            text = { Text("This action cannot be undone. All local conversations and messages will be permanently deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearConversations()
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun SwitchListItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    )
}
