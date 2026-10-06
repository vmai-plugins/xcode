package digital.vmstudio.code.feature.ai

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import digital.vmstudio.code.core.ai.model.ClaudeCodeModels
import digital.vmstudio.code.core.ai.omniroute.OmniRouteModels
import digital.vmstudio.code.core.ui.theme.VmTheme

/** The chip's text: a gateway id's last part, so `auto/best-coding` reads `best-coding`. */
internal fun modelChipLabel(selectedModel: String): String =
    ClaudeCodeModels.CHOICES.firstOrNull { it.id == selectedModel }?.label
        ?: selectedModel.substringAfterLast('/')

/**
 * Models whose id contains every word of [query], in any order and any case.
 * An id that starts with the first word comes first, so "gem" finds `gemini-…` before `…-gemma`.
 */
internal fun filterModels(ids: List<String>, query: String): List<String> {
    val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return ids
    val matches = ids.filter { id -> words.all { id.lowercase().contains(it) } }
    return matches.sortedBy { id ->
        if (id.substringAfterLast('/').lowercase().startsWith(words.first())) 0 else 1
    }
}

/** Groups `vendor/model` ids under their vendor; ids without a vendor share one unnamed group. */
internal fun groupModels(ids: List<String>): List<Pair<String?, List<String>>> {
    if (ids.none { '/' in it }) return listOf(null to ids)
    return ids.groupBy { id -> id.substringBefore('/', "").ifEmpty { "other" } }
        .toList()
        .sortedBy { (vendor, _) -> vendor }
}

/** The chip in the composer; opens a searchable sheet of every model the user can pick. */
@Composable
internal fun ModelPicker(
    selectedModel: String,
    omniModels: OmniRouteModels,
    isSyncing: Boolean,
    enabled: Boolean,
    onModelChange: (String) -> Unit,
    onSync: () -> Unit,
    isClaudeCodeMissing: Boolean = false,
) {
    var open by remember { mutableStateOf(false) }
    ComposerChip(label = modelChipLabel(selectedModel), enabled = enabled, onClick = { open = true })
    if (open) {
        ModelPickerSheet(
            selectedModel = selectedModel,
            omniModels = omniModels,
            isSyncing = isSyncing,
            isClaudeCodeMissing = isClaudeCodeMissing,
            onPick = {
                onModelChange(it)
                open = false
            },
            onSync = onSync,
            onDismiss = { open = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelPickerSheet(
    selectedModel: String,
    omniModels: OmniRouteModels,
    isSyncing: Boolean,
    isClaudeCodeMissing: Boolean,
    onPick: (String) -> Unit,
    onSync: () -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val claude = ClaudeCodeModels.CHOICES.filter { choice ->
        filterModels(listOf("${choice.label} ${choice.id} ${choice.description}"), query).isNotEmpty()
    }
    val groups = groupModels(filterModels(omniModels.ids, query))
    val nothing = claude.isEmpty() && groups.all { it.second.isEmpty() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Choose a model", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = omniSectionTitle(omniModels, isSyncing),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SyncButton(omniModels.isConfigured, isSyncing, onSync)
            }
            SearchField(query = query, onQueryChange = { query = it })
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp, max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (claude.isNotEmpty()) {
                    item {
                        val label = if (isClaudeCodeMissing) CLAUDE_MISSING_LABEL else "Claude Code"
                        SectionLabel(label)
                    }
                    items(claude, key = { "claude-${it.id}" }) { choice ->
                        val isSelected = choice.id == selectedModel
                        ModelRow(choice.label, choice.description, isSelected) { onPick(choice.id) }
                    }
                }
                groups.forEach { (vendor, ids) ->
                    if (ids.isEmpty()) return@forEach
                    item(key = "header-$vendor") { SectionLabel(vendor ?: "OmniRoute") }
                    items(ids, key = { "omni-$it" }) { id ->
                        val subtitle = id.takeIf { '/' in id }
                        ModelRow(id.substringAfterLast('/'), subtitle, id == selectedModel) { onPick(id) }
                    }
                }
                if (nothing) item { EmptyHint(query, omniModels) }
            }
        }
    }
}

@Composable
private fun SyncButton(configured: Boolean, isSyncing: Boolean, onSync: () -> Unit) {
    if (isSyncing) {
        CircularProgressIndicator(modifier = Modifier.padding(12.dp).size(20.dp), strokeWidth = 2.dp)
    } else {
        IconButton(onClick = onSync, enabled = configured) {
            Icon(Icons.Default.Refresh, contentDescription = "Sync models now")
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = { Text("Search models") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Clear search")
                }
            }
        },
        shape = RoundedCornerShape(24.dp),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp, start = 4.dp),
    )
}

@Composable
private fun ModelRow(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(modifier = Modifier.size(20.dp)) {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = VmTheme.code.mono.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 320.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyHint(query: String, omniModels: OmniRouteModels) {
    val text = when {
        query.isNotBlank() -> "No model matches “${query.trim()}”."
        !omniModels.isConfigured -> "Set the gateway URL and key in Settings to see its models."
        else -> "No models yet. Tap the refresh button to sync."
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(16.dp),
    )
}

internal fun omniSectionTitle(models: OmniRouteModels, isSyncing: Boolean): String = when {
    isSyncing -> "OmniRoute · syncing…"
    !models.isConfigured -> "OmniRoute · not set up"
    models.syncedAtMillis == 0L -> "OmniRoute · not synced yet"
    else -> "OmniRoute · ${models.ids.size} models, synced ${relativeAge(models.syncedAtMillis)}"
}

private fun relativeAge(millis: Long): String {
    val minutes = (System.currentTimeMillis() - millis) / MILLIS_PER_MINUTE
    return when {
        minutes < 1 -> "just now"
        minutes < MINUTES_PER_HOUR -> "$minutes min ago"
        else -> "${minutes / MINUTES_PER_HOUR} h ago"
    }
}

private const val MILLIS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60L
private const val CLAUDE_MISSING_LABEL = "Claude Code · not installed on this server"
