package com.hermes.client.feature.models

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hermes.client.domain.model.AIProviderType
import com.hermes.client.domain.model.ResponseEffortLevel
import com.hermes.client.domain.model.ThinkingLevel
import kotlin.math.roundToInt

/**
 * Bottom-sheet for adjusting model generation parameters:
 * thinking level, response effort, temperature, top-p.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSettingsSheet(
    provider: AIProviderType?,
    temperature: Float,
    topP: Float,
    thinkingEnabled: Boolean,
    thinkingLevel: ThinkingLevel,
    responseEffort: ResponseEffortLevel?,
    onTemperatureChange: (Float) -> Unit,
    onTopPChange: (Float) -> Unit,
    onThinkingEnabledChange: (Boolean) -> Unit,
    onThinkingLevelChange: (ThinkingLevel) -> Unit,
    onResponseEffortChange: (ResponseEffortLevel?) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Handle / title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Response Settings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = "Close")
                }
            }

            HorizontalDivider()

            // ── Thinking Level (providers that support it) ────────────────────
            if (provider?.supportsThinking == true) {
                SettingsSection(title = "Thinking", icon = Icons.Outlined.Psychology) {
                    // Enable/disable toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Enable thinking", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = thinkingEnabled,
                            onCheckedChange = onThinkingEnabledChange
                        )
                    }

                    // Level selector — only meaningful when thinking is on
                    if (thinkingEnabled) {
                        Text(
                            "Thinking depth",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            ThinkingLevel.entries.filter { it != ThinkingLevel.OFF }.forEachIndexed { index, level ->
                                SegmentedButton(
                                    selected = thinkingLevel == level,
                                    onClick = { onThinkingLevelChange(level) },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = ThinkingLevel.entries.size - 1
                                    ),
                                    label = { Text(level.displayName) }
                                )
                            }
                        }
                    }
                }
                HorizontalDivider()
            }

            // ── Response Effort (OpenAI) ──────────────────────────────────────
            if (provider?.supportsResponseEffort == true) {
                SettingsSection(title = "Response Effort", icon = Icons.Outlined.Speed) {
                    Text(
                        "Controls reasoning depth for o-series models",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        val levels = listOf(null) + ResponseEffortLevel.entries
                        levels.forEachIndexed { index, effort ->
                            SegmentedButton(
                                selected = responseEffort == effort,
                                onClick = { onResponseEffortChange(effort) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = levels.size),
                                label = { Text(effort?.displayName ?: "Auto") }
                            )
                        }
                    }
                }
                HorizontalDivider()
            }

            // ── Temperature ───────────────────────────────────────────────────
            SettingsSection(title = "Temperature", icon = Icons.Outlined.Thermostat) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Controls randomness / creativity",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            "%.2f".format(temperature),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Slider(
                    value = temperature,
                    onValueChange = onTemperatureChange,
                    valueRange = 0f..2f,
                    steps = 39, // 0.05 increments
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("0 (deterministic)", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline)
                    Text("2 (creative)", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline)
                }
            }

            HorizontalDivider()

            // ── Top-P ─────────────────────────────────────────────────────────
            SettingsSection(title = "Top-P (nucleus sampling)", icon = Icons.Outlined.FilterList) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Cumulative probability cutoff",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            "%.2f".format(topP),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Slider(
                    value = topP,
                    onValueChange = onTopPChange,
                    valueRange = 0f..1f,
                    steps = 19, // 0.05 increments
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("0", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline)
                    Text("1", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(6.dp))
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        content()
    }
}
