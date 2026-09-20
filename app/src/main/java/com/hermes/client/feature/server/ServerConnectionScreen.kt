package com.hermes.client.feature.server

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hermes.client.data.security.SecureStorage
import com.hermes.client.domain.model.ConnectionState
import com.hermes.client.domain.model.ServerInfo
import com.hermes.client.domain.repository.HermesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ServerUiState(
    val serverUrl: String = "",
    val apiKey: String = "",
    val isTestingConnection: Boolean = false,
    val connectionState: ConnectionState = ConnectionState.UNKNOWN,
    val serverInfo: ServerInfo? = null,
    val error: String? = null,
    val isSaved: Boolean = false,
    val showApiKey: Boolean = false
)

@HiltViewModel
class ServerViewModel @Inject constructor(
    private val secureStorage: SecureStorage,
    private val hermesRepository: HermesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ServerUiState())
    val uiState: StateFlow<ServerUiState> = _uiState.asStateFlow()

    init {
        loadSavedConfig()
    }

    private fun loadSavedConfig() {
        _uiState.update {
            it.copy(
                serverUrl = secureStorage.getServerUrl() ?: "",
                apiKey = secureStorage.getApiKey() ?: ""
            )
        }
    }

    fun updateServerUrl(url: String) {
        _uiState.update { it.copy(serverUrl = url, isSaved = false) }
    }

    fun updateApiKey(key: String) {
        _uiState.update { it.copy(apiKey = key, isSaved = false) }
    }

    fun toggleApiKeyVisibility() {
        _uiState.update { it.copy(showApiKey = !it.showApiKey) }
    }

    fun saveAndConnect() {
        val state = _uiState.value
        if (state.serverUrl.isBlank()) {
            _uiState.update { it.copy(error = "Server URL is required") }
            return
        }

        // Save to secure storage
        secureStorage.setServerUrl(state.serverUrl)
        if (state.apiKey.isNotBlank()) {
            secureStorage.setApiKey(state.apiKey)
        }

        _uiState.update { it.copy(isSaved = true) }
        testConnection()
    }

    fun testConnection() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isTestingConnection = true,
                    error = null,
                    connectionState = ConnectionState.UNKNOWN
                )
            }

            // Ensure URL is saved before testing
            val url = _uiState.value.serverUrl
            if (url.isNotBlank()) {
                secureStorage.setServerUrl(url)
            }
            val apiKey = _uiState.value.apiKey
            if (apiKey.isNotBlank()) {
                secureStorage.setApiKey(apiKey)
            }

            hermesRepository.testConnection()
                .onSuccess { info ->
                    _uiState.update {
                        it.copy(
                            isTestingConnection = false,
                            connectionState = ConnectionState.CONNECTED,
                            serverInfo = info,
                            error = null
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isTestingConnection = false,
                            connectionState = ConnectionState.ERROR,
                            error = error.message ?: "Connection failed"
                        )
                    }
                }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerConnectionScreen(
    onNavigateBack: () -> Unit,
    viewModel: ServerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Server Connection") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Server URL
            OutlinedTextField(
                value = uiState.serverUrl,
                onValueChange = { viewModel.updateServerUrl(it) },
                label = { Text("Hermes Server URL") },
                placeholder = { Text("http://192.168.0.197:8642") },
                leadingIcon = { Icon(Icons.Outlined.Dns, contentDescription = null) },
                supportingText = {
                    Text("Enter the URL of your Hermes server (e.g., http://192.168.0.197:8642)")
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // API Key
            OutlinedTextField(
                value = uiState.apiKey,
                onValueChange = { viewModel.updateApiKey(it) },
                label = { Text("API Key") },
                placeholder = { Text("Your Hermes API key") },
                leadingIcon = { Icon(Icons.Outlined.Key, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { viewModel.toggleApiKeyVisibility() }) {
                        Icon(
                            if (uiState.showApiKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = "Toggle visibility"
                        )
                    }
                },
                visualTransformation = if (uiState.showApiKey)
                    VisualTransformation.None else PasswordVisualTransformation(),
                supportingText = { Text("Bearer token for authentication") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.testConnection() },
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isTestingConnection && uiState.serverUrl.isNotBlank()
                ) {
                    if (uiState.isTestingConnection) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Test Connection")
                }
                Button(
                    onClick = { viewModel.saveAndConnect() },
                    modifier = Modifier.weight(1f),
                    enabled = uiState.serverUrl.isNotBlank()
                ) {
                    Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Save & Connect")
                }
            }

            // Connection status
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when (uiState.connectionState) {
                        ConnectionState.CONNECTED -> MaterialTheme.colorScheme.primaryContainer
                        ConnectionState.ERROR -> MaterialTheme.colorScheme.errorContainer
                        ConnectionState.DISCONNECTED -> MaterialTheme.colorScheme.surfaceVariant
                        ConnectionState.UNKNOWN -> MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            when (uiState.connectionState) {
                                ConnectionState.CONNECTED -> Icons.Filled.CheckCircle
                                ConnectionState.ERROR -> Icons.Filled.Error
                                else -> Icons.Outlined.CloudOff
                            },
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when (uiState.connectionState) {
                                ConnectionState.CONNECTED -> "Connected to Hermes"
                                ConnectionState.ERROR -> "Connection Failed"
                                ConnectionState.DISCONNECTED -> "Disconnected"
                                ConnectionState.UNKNOWN -> "Not Connected"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (uiState.serverInfo != null) {
                        Spacer(Modifier.height(12.dp))
                        val info = uiState.serverInfo!!
                        InfoRow("Version", info.version)
                        InfoRow("API", info.apiVersion)
                        InfoRow("Models", "${info.modelCount}")
                        InfoRow("Streaming", if (info.capabilities.streaming) "✓" else "✕")
                        InfoRow("Tasks/Runs", if (info.capabilities.runs) "✓" else "✕")
                    }

                    if (uiState.error != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            uiState.error!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Help section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Setup Guide",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "1. Start Hermes on your PC: hermes gateway",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "2. Set API_SERVER_HOST=0.0.0.0 for LAN access",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "3. Find your PC's LAN IP (e.g., ipconfig)",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "4. Enter http://<PC-IP>:8642 above",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "5. Allow port 8642 through Windows Firewall",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}
