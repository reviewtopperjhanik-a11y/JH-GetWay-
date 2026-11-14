@file:OptIn(ExperimentalLayoutApi::class)

package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.i18n.LocalStrings
import com.example.data.local.ModelEntity
import com.example.data.local.ModelType
import com.example.ui.GatewayViewModel
import com.example.ui.components.EmptyStatePanel
import com.example.ui.components.GatewayCard
import com.example.ui.components.StatusPill
import com.example.ui.components.StatusTone
import com.example.ui.navigation.Routes
import com.example.ui.theme.MonoTextStyle

@Composable
fun ModelsScreen(
    viewModel: GatewayViewModel,
    onNavigate: (String) -> Unit
) {
    val s = LocalStrings.current
    val models by viewModel.models.collectAsState()
    val providers by viewModel.providers.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf<ModelType?>(null) }
    var aliasTarget by remember { mutableStateOf<ModelEntity?>(null) }
    var detailTarget by remember { mutableStateOf<ModelEntity?>(null) }

    val filtered = remember(models, searchQuery, selectedType) {
        models.filter { m ->
            (selectedType == null || m.modelType == selectedType) &&
                (searchQuery.isBlank() || m.displayName.contains(searchQuery, true) || m.modelId.contains(searchQuery, true) || m.alias.contains(searchQuery, true))
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(s.modelsTitle, style = MaterialTheme.typography.headlineSmall)
                if (providers.isNotEmpty()) {
                    TextButton(onClick = { providers.forEach { viewModel.fetchProviderModels(it) } }) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(s.refresh)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text(s.search) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    FilterChip(selected = selectedType == null, onClick = { selectedType = null }, label = { Text(s.allFilterLabel) })
                }
                items(ModelType.entries) { mt ->
                    FilterChip(selected = selectedType == mt, onClick = { selectedType = mt }, label = { Text(mt.name) })
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                EmptyStatePanel(
                    icon = Icons.Default.Memory,
                    title = s.noModelsTitle,
                    message = s.noModelsMessage,
                    actionLabel = s.providersTitle,
                    onAction = { onNavigate(Routes.PROVIDERS) }
                )
            }
        } else {
            items(filtered, key = { it.id }) { model ->
                GatewayCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(model.displayName, style = MaterialTheme.typography.titleMedium)
                            Text(model.modelId, style = MonoTextStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        StatusPill(
                            text = if (model.isLocal) s.localModels else s.cloudModels,
                            tone = if (model.isLocal) StatusTone.INFO else StatusTone.SUCCESS
                        )
                    }

                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        AssistChip(onClick = {}, label = { Text(model.providerName) })
                        AssistChip(onClick = {}, label = { Text(model.modelType.name) })
                        if (model.alias.isNotBlank()) {
                            AssistChip(onClick = { aliasTarget = model }, label = { Text("${s.aliasPrefix}: ${model.alias}") })
                        }
                        if (model.isDefault) {
                            AssistChip(onClick = {}, label = { Text(s.defaultBadge) })
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { aliasTarget = model }, modifier = Modifier.testTag("set_alias_${model.modelId}")) {
                            Text(s.setAlias)
                        }
                        if (!model.isDefault) {
                            TextButton(onClick = { viewModel.setDefaultModel(model) }) { Text(s.setDefault) }
                        }
                        TextButton(onClick = { detailTarget = model }) { Text(s.details) }
                        TextButton(onClick = { onNavigate(Routes.PLAYGROUND) }) { Text(s.testModel) }
                        IconButton(onClick = { viewModel.toggleModelEnabled(model) }) {
                            Icon(
                                imageVector = if (model.enabled) Icons.Default.ToggleOn else Icons.Default.ToggleOff,
                                contentDescription = if (model.enabled) s.disable else s.enable
                            )
                        }
                    }
                }
            }
        }
    }

    aliasTarget?.let { m ->
        var aliasText by remember { mutableStateOf(m.alias) }
        AlertDialog(
            onDismissRequest = { aliasTarget = null },
            title = { Text("${s.setAlias}: ${m.modelId}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = aliasText,
                        onValueChange = { aliasText = it },
                        label = { Text(s.aliasHint) },
                        textStyle = MonoTextStyle,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(listOf("jh-general", "jh-coder", "jh-fast", "jh-reasoning", "jh-low-cost", "jh-vision")) { preset ->
                            AssistChip(onClick = { aliasText = preset }, label = { Text(preset) })
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.setModelAlias(m, aliasText); aliasTarget = null }) { Text(s.save) }
            },
            dismissButton = { TextButton(onClick = { aliasTarget = null }) { Text(s.cancel) } }
        )
    }

    detailTarget?.let { m ->
        AlertDialog(
            onDismissRequest = { detailTarget = null },
            title = { Text(m.displayName) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${s.modelId}: ${m.modelId}", style = MonoTextStyle)
                    Text("${s.providersTitle}: ${m.providerName}")
                    Text("${s.modelType}: ${m.modelType.name}")
                    Text("${s.contextWindow}: ${m.contextWindow ?: s.unknown}")
                    Text("${s.toolCalling}: ${if (m.supportsTools) s.supportedLabel else s.noLabel}")
                    Text("${s.streamingLabel}: ${if (m.supportsStreaming) s.supportedLabel else s.noLabel}")
                    val priceText = if (m.inputPricePer1M != null && m.outputPricePer1M != null) {
                        "$${m.inputPricePer1M} / $${m.outputPricePer1M} per 1M (${m.pricingUpdatedAt})"
                    } else s.unknown
                    Text("${s.estimatedCost}: $priceText")
                }
            },
            confirmButton = { TextButton(onClick = { detailTarget = null }) { Text(s.close) } }
        )
    }
}
