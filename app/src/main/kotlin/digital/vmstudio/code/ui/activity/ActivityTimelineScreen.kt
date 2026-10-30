package digital.vmstudio.code.ui.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import digital.vmstudio.code.core.database.entity.ActivityCategory
import digital.vmstudio.code.core.database.entity.ActivityEntity
import digital.vmstudio.code.core.database.entity.ActivitySeverity
import digital.vmstudio.code.core.database.repository.ActivityRepository
import digital.vmstudio.code.core.ui.component.VmCard
import digital.vmstudio.code.core.ui.component.VmEmptyState
import digital.vmstudio.code.core.ui.component.VmStatus
import digital.vmstudio.code.core.ui.theme.VmTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class ActivityViewModel @Inject constructor(
    private val activityRepository: ActivityRepository,
) : ViewModel() {
    val activities: StateFlow<List<ActivityEntity>> = activityRepository.observeRecent(200)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    fun clearAll() {
        viewModelScope.launch {
            activityRepository.clearAll()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityTimelineScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ActivityViewModel = hiltViewModel(),
) {
    val activities by viewModel.activities.collectAsStateWithLifecycle()
    val spacing = VmTheme.spacing
    var selectedCategory by remember { mutableStateOf<ActivityCategory?>(null) }

    val filteredActivities = remember(activities, selectedCategory) {
        if (selectedCategory == null) activities
        else activities.filter { it.category == selectedCategory }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Activity Audit Timeline") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (activities.isNotEmpty()) {
                        IconButton(onClick = viewModel::clearAll) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear all activity")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Category filter chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = spacing.screenHorizontal, vertical = spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                item {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { selectedCategory = null },
                        label = { Text("All (${activities.size})") },
                    )
                }
                items(
                    listOf(
                        ActivityCategory.AI to "AI Agent",
                        ActivityCategory.GIT to "Git",
                        ActivityCategory.FILE to "Files",
                        ActivityCategory.SERVER to "Servers",
                        ActivityCategory.TERMINAL to "Terminal",
                    ),
                ) { (cat, label) ->
                    val count = activities.count { it.category == cat }
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = if (selectedCategory == cat) null else cat },
                        label = { Text("$label ($count)") },
                    )
                }
            }

            if (filteredActivities.isEmpty()) {
                VmEmptyState(
                    icon = Icons.Default.History,
                    title = "No activity yet",
                    description = "Key events like agent actions, Git operations, and file saves appear here.",
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        horizontal = spacing.screenHorizontal,
                        vertical = spacing.sm,
                    ),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    items(filteredActivities, key = { it.id }) { activity ->
                        ActivityItemCard(activity = activity)
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityItemCard(activity: ActivityEntity) {
    val spacing = VmTheme.spacing
    val timeFormat = remember { SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault()) }
    val timeStr = remember(activity.timestampMillis) { timeFormat.format(Date(activity.timestampMillis)) }

    val icon = when (activity.category) {
        ActivityCategory.AI, ActivityCategory.AGENT -> Icons.Default.AutoAwesome
        ActivityCategory.GIT -> Icons.Default.AccountTree
        ActivityCategory.FILE -> Icons.AutoMirrored.Filled.InsertDriveFile
        ActivityCategory.SERVER -> Icons.Default.Dns
        ActivityCategory.TERMINAL -> Icons.Default.Terminal
        else -> Icons.Default.History
    }

    val status = when (activity.severity) {
        ActivitySeverity.SUCCESS -> VmStatus.CONNECTED
        ActivitySeverity.WARNING -> VmStatus.DEGRADED
        ActivitySeverity.ERROR -> VmStatus.ERROR
        ActivitySeverity.INFO -> VmStatus.CONNECTED
    }

    VmCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = activity.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val detail = activity.detail
            if (!detail.isNullOrBlank()) {
                Text(
                    text = detail,
                    style = VmTheme.code.mono.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
