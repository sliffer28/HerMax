package com.hermes.client.feature.tasks

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.hermes.client.domain.model.Task
import com.hermes.client.domain.model.TaskStatus
import com.hermes.client.domain.repository.HermesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class TasksViewModel @Inject constructor(
    private val hermesRepository: HermesRepository
) : ViewModel() {

    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    val tasks: StateFlow<List<Task>> = _tasks.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun cancelTask(taskId: String) {
        viewModelScope.launch {
            hermesRepository.cancelRun(taskId)
            _tasks.update { tasks ->
                tasks.map {
                    if (it.id == taskId) it.copy(status = TaskStatus.CANCELLED) else it
                }
            }
        }
    }

    fun removeTask(taskId: String) {
        _tasks.update { it.filter { task -> task.id != taskId } }
    }

    fun addTask(task: Task) {
        _tasks.update { it + task }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    onNavigateBack: () -> Unit,
    viewModel: TasksViewModel = hiltViewModel()
) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tasks") },
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
        ) {
            if (tasks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.TaskAlt,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "No active tasks",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Long-running agent operations will appear here",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(tasks, key = { it.id }) { task ->
                        TaskCard(
                            task = task,
                            onCancel = { viewModel.cancelTask(task.id) },
                            onRemove = { viewModel.removeTask(task.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskCard(
    task: Task,
    onCancel: () -> Unit,
    onRemove: () -> Unit
) {
    val dateFormatter = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }

    val statusColor = when (task.status) {
        TaskStatus.RUNNING -> MaterialTheme.colorScheme.primary
        TaskStatus.COMPLETED -> MaterialTheme.colorScheme.tertiary
        TaskStatus.FAILED -> MaterialTheme.colorScheme.error
        TaskStatus.CANCELLED -> MaterialTheme.colorScheme.outline
        TaskStatus.QUEUED -> MaterialTheme.colorScheme.secondary
        TaskStatus.PAUSED -> MaterialTheme.colorScheme.secondary
    }

    val statusIcon = when (task.status) {
        TaskStatus.RUNNING -> Icons.Filled.PlayCircle
        TaskStatus.COMPLETED -> Icons.Filled.CheckCircle
        TaskStatus.FAILED -> Icons.Filled.Error
        TaskStatus.CANCELLED -> Icons.Filled.Cancel
        TaskStatus.QUEUED -> Icons.Filled.HourglassEmpty
        TaskStatus.PAUSED -> Icons.Filled.PauseCircle
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    statusIcon,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        task.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        task.status.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor
                    )
                }
                if (task.status == TaskStatus.RUNNING) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = statusColor
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Task details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Started: ${dateFormatter.format(Date(task.startedAt))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                if (task.model != null) {
                    Text(
                        task.model,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            if (task.progress != null) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { task.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = statusColor
                )
            }

            if (task.lastActivity != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    task.lastActivity,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (task.error != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    task.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // Action buttons
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (task.status == TaskStatus.RUNNING || task.status == TaskStatus.QUEUED) {
                    OutlinedButton(
                        onClick = onCancel,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Cancel")
                    }
                }
                if (task.status == TaskStatus.COMPLETED || task.status == TaskStatus.FAILED || task.status == TaskStatus.CANCELLED) {
                    TextButton(onClick = onRemove) {
                        Text("Remove")
                    }
                }
            }
        }
    }
}
