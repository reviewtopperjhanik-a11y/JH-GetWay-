@file:OptIn(ExperimentalLayoutApi::class)

package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.core.i18n.LocalStrings
import com.example.data.local.ModelEntity
import com.example.ui.GatewayViewModel
import com.example.ui.components.*
import com.example.ui.navigation.Routes
import com.example.ui.theme.MonoTextStyle

@Composable
fun PlaygroundScreen(
    viewModel: GatewayViewModel,
    onNavigate: (String) -> Unit
) {
    val s = LocalStrings.current
    val settings by viewModel.settings.collectAsState()
    val providers by viewModel.providers.collectAsState()
    val models by viewModel.models.collectAsState()
    val sessions by viewModel.playgroundSessions.collectAsState()
    val savedMessages by viewModel.activeSessionMessages.collectAsState()
    val ephemeralMessages by viewModel.ephemeralMessages.collectAsState()
    val streamingBuffer by viewModel.streamingBuffer.collectAsState()
    val isStreaming by viewModel.isStreaming.collectAsState()
    val compareResults by viewModel.compareResults.collectAsState()
    val isComparing by viewModel.isComparing.collectAsState()
    val clipboard = LocalClipboardManager.current

    val messages = if (settings.saveChatHistoryAllowed) savedMessages else ephemeralMessages

    var compareMode by remember { mutableStateOf(false) }
    var selectedProviderId by remember { mutableStateOf<String?>(null) }
    var selectedModelId by remember(models, settings.defaultModelId) {
        mutableStateOf(
            settings.defaultModelId.ifBlank {
                models.firstOrNull { it.isDefault }?.modelId ?: models.firstOrNull()?.modelId.orEmpty()
            }
        )
    }
    var systemPrompt by remember { mutableStateOf("") }
    var promptInput by remember { mutableStateOf("") }
    var showParams by remember { mutableStateOf(false) }

    var temperature by remember { mutableFloatStateOf(0.7f) }
    var topP by remember { mutableFloatStateOf(0.95f) }
    var maxTokensText by remember { mutableStateOf("1024") }
    var stopSeqText by remember { mutableStateOf("") }
    var streamingEnabled by remember { mutableStateOf(true) }
    var jsonOutput by remember { mutableStateOf(false) }

    val selectedModelEntity = models.firstOrNull { it.modelId == selectedModelId }
    var compareSelection by remember { mutableStateOf<Set<String>>(emptySet()) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Mode & Actions Header
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !compareMode,
                    onClick = { compareMode = false },
                    label = { Text(s.singleChatMode) },
                    modifier = Modifier.testTag("playground_chat_mode")
                )
                FilterChip(
                    selected = compareMode,
                    onClick = { compareMode = true },
                    label = { Text(s.multiModelCompareMode) },
                    modifier = Modifier.testTag("playground_compare_mode")
                )
            }
            Row {
                IconButton(onClick = { showParams = !showParams }) {
                    Icon(Icons.Default.Tune, contentDescription = s.parameters)
                }
                IconButton(onClick = { viewModel.selectOrCreateSession(null) }) {
                    Icon(Icons.Default.AddComment, contentDescription = s.newChat)
                }
                IconButton(onClick = { viewModel.clearActiveChat() }) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = s.clearChat)
                }
                IconButton(onClick = {
                    val exported = messages.joinToString("\n\n") { "[${it.role.uppercase()}] ${it.content}" }
                    clipboard.setText(AnnotatedString(exported))
                    viewModel.postBanner(s.copied)
                }) {
                    Icon(Icons.Default.IosShare, contentDescription = s.exportChat)
                }
            }
        }

        // Provider & Model Selector Bar
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = selectedProviderId == null,
                    onClick = { selectedProviderId = null },
                    label = { Text(s.autoRouterLabel) }
                )
            }
            items(providers.filter { it.enabled }) { p ->
                FilterChip(
                    selected = selectedProviderId == p.id,
                    onClick = {
                        selectedProviderId = p.id
                        if (p.defaultModel.isNotBlank()) selectedModelId = p.defaultModel
                    },
                    label = { Text(p.name) }
                )
            }
        }

        if (models.isNotEmpty() && !compareMode) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(models.filter { it.enabled }) { m ->
                    val label = if (m.alias.isNotBlank()) "${m.alias} (${m.modelId})" else m.modelId
                    FilterChip(
                        selected = selectedModelId == m.modelId,
                        onClick = { selectedModelId = m.modelId },
                        label = { Text(label, style = MonoTextStyle) }
                    )
                }
            }
        }

        if (showParams) {
            GatewayCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    label = { Text(s.systemPromptLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text("${s.temperature}: ${String.format("%.2f", temperature)}", style = MaterialTheme.typography.labelMedium)
                Slider(value = temperature, onValueChange = { temperature = it }, valueRange = 0f..2f)
                Text("${s.topP}: ${String.format("%.2f", topP)}", style = MaterialTheme.typography.labelMedium)
                Slider(value = topP, onValueChange = { topP = it }, valueRange = 0.05f..1f)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = maxTokensText, onValueChange = { maxTokensText = it.filter { c -> c.isDigit() } }, label = { Text(s.maxOutputTokens) }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = stopSeqText, onValueChange = { stopSeqText = it }, label = { Text(s.stopSequences) }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val streamSupported = selectedModelEntity?.supportsStreaming ?: true
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = streamingEnabled && streamSupported, enabled = streamSupported, onCheckedChange = { streamingEnabled = it })
                        Spacer(Modifier.width(4.dp))
                        Text(if (streamSupported) s.streamingLabel else "${s.streamingLabel} (${s.unsupportedParam})", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val jsonSupported = !selectedModelId.contains("cohere", true)
                    Switch(checked = jsonOutput && jsonSupported, enabled = jsonSupported, onCheckedChange = { jsonOutput = it })
                    Spacer(Modifier.width(4.dp))
                    Text(if (jsonSupported) s.jsonOutput else "${s.jsonOutput} (${s.unsupportedParam})", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (providers.isEmpty() && models.isEmpty()) {
            Box(modifier = Modifier.weight(1f).padding(16.dp), contentAlignment = Alignment.Center) {
                EmptyStatePanel(
                    icon = Icons.AutoMirrored.Filled.Chat,
                    title = s.noProvidersTitle,
                    message = s.noProvidersMessage,
                    actionLabel = s.addProvider,
                    onAction = { onNavigate(Routes.PROVIDERS) }
                )
            }
        } else if (!compareMode) {
            // Single Chat Conversation Area
            if (sessions.isNotEmpty() && settings.saveChatHistoryAllowed) {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(sessions.take(5), key = { it.id }) { sess ->
                        AssistChip(
                            onClick = { viewModel.selectOrCreateSession(sess.id) },
                            label = { Text(sess.title, maxLines = 1) }
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    val isUser = msg.role == "user"
                    GatewayCard(
                        containerColor = when {
                            msg.isError -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                            isUser -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            else -> MaterialTheme.colorScheme.surface
                        }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isUser) s.youLabel else "${msg.providerName} • ${msg.modelId}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            IconButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(msg.content))
                                    viewModel.postBanner(s.copied)
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("playground_copy_msg_${msg.id}")
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = s.copyResponseLabel, modifier = Modifier.size(18.dp))
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(msg.content, style = MaterialTheme.typography.bodyMedium)
                        if (!isUser && msg.latencyMs != null) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "${msg.latencyMs}ms • ${msg.inputTokens ?: 0} in / ${msg.outputTokens ?: 0} out • ${formatCurrency(msg.estimatedCostUsd, settings.analyticsCurrency, s.unknown)}",
                                style = MonoTextStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (isStreaming) {
                    item {
                        GatewayCard {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Text(s.streamingIndicator, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                            if (streamingBuffer.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text(streamingBuffer, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        } else {
            // Multi-Model Compare Mode
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text("${s.selectModelsToCompareLabel} (${compareSelection.size})", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        models.filter { it.enabled }.forEach { m ->
                            val picked = m.id in compareSelection
                            FilterChip(
                                selected = picked,
                                onClick = {
                                    compareSelection = if (picked) compareSelection - m.id else compareSelection + m.id
                                },
                                label = { Text("${m.providerName}: ${m.modelId}") }
                            )
                        }
                    }
                }

                if (isComparing) {
                    item {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }

                items(compareResults) { itemRes ->
                    GatewayCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${itemRes.providerName} • ${itemRes.modelId}", style = MaterialTheme.typography.titleSmall)
                            StatusPill(
                                text = if (itemRes.error == null) "${itemRes.latencyMs}ms" else s.error,
                                tone = if (itemRes.error == null) StatusTone.SUCCESS else StatusTone.ERROR
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = itemRes.error ?: itemRes.responseText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (itemRes.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "${itemRes.inputTokens} in / ${itemRes.outputTokens} out • ${formatCurrency(itemRes.estimatedCostUsd, settings.analyticsCurrency, s.unknown)}",
                            style = MonoTextStyle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Input Composer Bar
        Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = promptInput,
                    onValueChange = { promptInput = it },
                    placeholder = { Text(s.messageInputHint) },
                    maxLines = 4,
                    modifier = Modifier.weight(1f).testTag("playground_prompt_input")
                )
                if (!compareMode && messages.lastOrNull { it.role == "user" } != null && !isStreaming) {
                    IconButton(onClick = {
                        val lastUser = messages.lastOrNull { it.role == "user" }?.content.orEmpty()
                        if (lastUser.isNotBlank()) {
                            viewModel.sendPlaygroundPrompt(
                                prompt = lastUser,
                                systemPrompt = systemPrompt,
                                modelIdOrAlias = selectedModelId,
                                explicitProviderId = selectedProviderId,
                                temperature = temperature,
                                topP = topP,
                                maxTokens = maxTokensText.toIntOrNull() ?: 1024,
                                stopSequences = stopSeqText.split(",").map { it.trim() }.filter { it.isNotBlank() },
                                jsonOutput = jsonOutput,
                                streaming = streamingEnabled
                            )
                        }
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = s.regenerate)
                    }
                }
                FilledIconButton(
                    enabled = promptInput.isNotBlank() && !isStreaming && !isComparing,
                    onClick = {
                        val currentPrompt = promptInput
                        promptInput = ""
                        if (compareMode) {
                            val chosen = models.filter { it.id in compareSelection }
                            viewModel.runModelComparison(currentPrompt, systemPrompt, chosen)
                        } else {
                            viewModel.sendPlaygroundPrompt(
                                prompt = currentPrompt,
                                systemPrompt = systemPrompt,
                                modelIdOrAlias = selectedModelId,
                                explicitProviderId = selectedProviderId,
                                temperature = temperature,
                                topP = topP,
                                maxTokens = maxTokensText.toIntOrNull() ?: 1024,
                                stopSequences = stopSeqText.split(",").map { it.trim() }.filter { it.isNotBlank() },
                                jsonOutput = jsonOutput,
                                streaming = streamingEnabled
                            )
                        }
                    },
                    modifier = Modifier.size(48.dp).testTag("playground_send_btn")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = s.send)
                }
            }
        }
    }
}
