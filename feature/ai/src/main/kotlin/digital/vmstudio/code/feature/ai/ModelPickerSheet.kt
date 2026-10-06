package digital.vmstudio.code.feature.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.text.font.FontWeight
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
    ComposerChip(
        label = modelChipLabel(selectedModel),
        enabled = enabled,
        onClick = { open = true },
        // Green with a bolt when the model is free, so it reads at a glance.
        icon = if (selectedModel in omniModels.freeIds) Icons.Default.Bolt else null,
        accent = if (selectedModel in omniModels.freeIds) VmTheme.colors.success else null,
    )
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

/**
 * The gateway model to fall back on when the chosen one is gone: a free one first
 * (it costs nothing and is what most people pick from), else the first listed.
 */
internal fun preferredGatewayModel(ids: List<String>, freeIds: Set<String>): String? =
    ids.firstOrNull { it in freeIds } ?: ids.firstOrNull()

/**
 * The model to move to when the selected gateway model is gone from a fresh list,
 * or null when the selection is fine (a Claude Code alias, a still-listed model,
 * or no list to judge by).
 */
internal fun replacementForMissingModel(selected: String, models: OmniRouteModels): String? {
    if (ClaudeCodeModels.isClaudeCode(selected) || models.ids.isEmpty() || selected in models.ids) return null
    return preferredGatewayModel(models.ids, models.freeIds)
}

/** What the picker lists: everything, only free gateway models, or only Claude Code. */
internal enum class ModelFilter { ALL, FREE, CLAUDE }

/** The gateway ids a filter keeps, after the search. */
internal fun visibleGatewayModels(
    ids: List<String>,
    freeIds: Set<String>,
    query: String,
    filter: ModelFilter,
): List<String> = when (filter) {
    ModelFilter.CLAUDE -> emptyList()
    ModelFilter.FREE -> filterModels(ids.filter { it in freeIds }, query)
    ModelFilter.ALL -> filterModels(ids, query)
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
    // Opens on Free when the gateway marks any model free: that is what most people
    // pick from, and it answers "which ones cost nothing" at a glance.
    var filter by remember {
        mutableStateOf(if (omniModels.freeIds.isEmpty()) ModelFilter.ALL else ModelFilter.FREE)
    }
    val claude = if (filter == ModelFilter.FREE) {
        emptyList()
    } else {
        ClaudeCodeModels.CHOICES.filter { choice ->
            filterModels(listOf("${choice.label} ${choice.id} ${choice.description}"), query).isNotEmpty()
        }
    }
    val groups = groupModels(visibleGatewayModels(omniModels.ids, omniModels.freeIds, query, filter))
    val nothing = claude.isEmpty() && groups.all { it.second.isEmpty() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Choose a model", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = omniSectionTitle(omniModels, isSyncing),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                SyncButton(omniModels.isConfigured, isSyncing, onSync)
            }
            SearchField(query = query, onQueryChange = { query = it })
            FilterRow(filter, freeCount = omniModels.freeIds.size, onFilter = { filter = it })
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp, max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (claude.isNotEmpty()) {
                    item(key = "header-claude") {
                        SectionLabel(if (isClaudeCodeMissing) CLAUDE_MISSING_LABEL else CLAUDE_LABEL)
                    }
                    items(claude, key = { "claude-${it.id}" }) { choice ->
                        ModelRow(
                            title = choice.label,
                            subtitle = choice.description,
                            vendor = "claude",
                            selected = choice.id == selectedModel,
                            free = false,
                        ) { onPick(choice.id) }
                    }
                }
                groups.forEach { (vendor, ids) ->
                    if (ids.isEmpty()) return@forEach
                    item(key = "header-$vendor") { SectionLabel(vendor ?: "OmniRoute") }
                    items(ids, key = { "omni-$it" }) { id ->
                        ModelRow(
                            title = id.substringAfterLast('/'),
                            subtitle = id.takeIf { '/' in it },
                            vendor = vendor ?: id,
                            selected = id == selectedModel,
                            free = id in omniModels.freeIds,
                        ) { onPick(id) }
                    }
                }
                if (nothing) item(key = "empty") { EmptyHint(query, omniModels, filter) }
            }
        }
    }
}

@Composable
private fun FilterRow(filter: ModelFilter, freeCount: Int, onFilter: (ModelFilter) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 4.dp),
    ) {
        FilterChip(
            selected = filter == ModelFilter.ALL,
            onClick = { onFilter(ModelFilter.ALL) },
            label = { Text("All") },
        )
        FilterChip(
            selected = filter == ModelFilter.FREE,
            onClick = { onFilter(ModelFilter.FREE) },
            label = { Text(if (freeCount > 0) "Free · $freeCount" else "Free") },
            leadingIcon = {
                Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = VmTheme.colors.successContainer,
                selectedLabelColor = VmTheme.colors.onSuccessContainer,
                selectedLeadingIconColor = VmTheme.colors.onSuccessContainer,
            ),
        )
        FilterChip(
            selected = filter == ModelFilter.CLAUDE,
            onClick = { onFilter(ModelFilter.CLAUDE) },
            label = { Text("Claude Code") },
        )
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
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp, start = 8.dp),
    )
}

/** A round badge with the provider's initial, tinted per provider so a long list scans by colour. */
@Composable
private fun ProviderAvatar(vendor: String, free: Boolean = false) {
    val palette = listOf(
        MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer,
        MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer,
        VmTheme.colors.infoContainer to VmTheme.colors.onInfoContainer,
        VmTheme.colors.agentContainer to VmTheme.colors.onAgentContainer,
    )
    val (background, foreground) = palette[(vendor.lowercase().hashCode() and Int.MAX_VALUE) % palette.size]
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(background)
            // A green ring marks a free model from the avatar alone.
            .then(if (free) Modifier.border(2.dp, VmTheme.colors.success, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = vendor.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?",
            style = MaterialTheme.typography.labelLarge,
            color = foreground,
        )
    }
}

@Composable
private fun FreeBadge() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(VmTheme.colors.success)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Bolt,
            contentDescription = null,
            tint = VmTheme.colors.onSuccess,
            modifier = Modifier.size(11.dp),
        )
        Text(
            text = "FREE",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = VmTheme.colors.onSuccess,
        )
    }
}

@Composable
private fun ModelRow(
    title: String,
    subtitle: String?,
    vendor: String,
    selected: Boolean,
    free: Boolean,
    onClick: () -> Unit,
) {
    val background = when {
        selected -> MaterialTheme.colorScheme.secondaryContainer
        // A faint green wash on free rows, so a long list shows them as a group.
        free -> VmTheme.colors.success.copy(alpha = FREE_ROW_TINT)
        else -> Color.Transparent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ProviderAvatar(vendor, free)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
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
        if (free) FreeBadge()
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun EmptyHint(query: String, omniModels: OmniRouteModels, filter: ModelFilter) {
    val text = when {
        query.isNotBlank() -> "No model matches “${query.trim()}”."
        filter == ModelFilter.FREE && omniModels.ids.isNotEmpty() ->
            "The gateway marks none of its models as free. Tap All to see every model."
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
    else -> buildString {
        append("OmniRoute · ${models.ids.size} models")
        if (models.freeIds.isNotEmpty()) append(", ${models.freeIds.size} free")
        append(" · synced ${relativeAge(models.syncedAtMillis)}")
    }
}

private fun relativeAge(millis: Long): String {
    val minutes = (System.currentTimeMillis() - millis) / MILLIS_PER_MINUTE
    return when {
        minutes < 1 -> "just now"
        minutes < MINUTES_PER_HOUR -> "$minutes min ago"
        else -> "${minutes / MINUTES_PER_HOUR} h ago"
    }
}

private const val FREE_ROW_TINT = 0.08f
private const val MILLIS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60L
private const val CLAUDE_LABEL = "Claude Code · runs on your server"
private const val CLAUDE_MISSING_LABEL = "Claude Code · not installed on this server"
