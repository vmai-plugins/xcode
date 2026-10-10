package digital.vmstudio.code.feature.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import digital.vmstudio.code.core.ui.component.*
import digital.vmstudio.code.core.ui.theme.VmTheme

@Composable
fun GitHubSettingsScreen(
    onCloneRepo: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GitHubSettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var tokenDraft by remember { mutableStateOf("") }
    
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        VmCard {
            Text("GitHub Access Token", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text("Create a fine-grained or classic personal access token with repo scope.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value = tokenDraft,
                onValueChange = { tokenDraft = it },
                label = { Text("Token") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            VmButton(
                text = "Save Token",
                onClick = { viewModel.setToken(tokenDraft); tokenDraft = "" },
                enabled = tokenDraft.isNotBlank(),
                style = VmButtonStyle.Primary
            )
        }
        
        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        }
        
        state.error?.let {
            Text("Error loading repos: $it", color = MaterialTheme.colorScheme.error)
        }
        
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.repositories, key = { it.fullName }) { repo ->
                VmCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                            Text(repo.fullName, fontWeight = FontWeight.Bold)
                            Text(repo.description ?: "No description", style = MaterialTheme.typography.bodySmall)
                        }
                        VmButton(
                            text = "Code & Build",
                            onClick = { onCloneRepo(repo.cloneUrl) }
                        )
                    }
                }
            }
        }
    }
}

