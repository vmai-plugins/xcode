package digital.vmstudio.code.feature.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import digital.vmstudio.code.core.ui.component.*
import digital.vmstudio.code.core.ui.theme.VmTheme
import digital.vmstudio.code.core.git.codespacesUrl
import android.widget.Toast

@Composable
fun GitHubSettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: GitHubSettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var tokenDraft by remember { mutableStateOf("") }
    
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        VmCard {
            Text("GitHub token", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "Choose a repository to open it in GitHub Codespaces. Editing and builds happen in GitHub, not on this phone or your VPS.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text("Use a token that can read your repositories.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value = tokenDraft,
                onValueChange = { tokenDraft = it },
                label = { Text("Token") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                VmButton(
                    text = if (state.hasToken) "Update Token" else "Save Token",
                    onClick = { viewModel.setToken(tokenDraft) { tokenDraft = "" } },
                    enabled = tokenDraft.isNotBlank() && !state.isLoading,
                    style = VmButtonStyle.Primary,
                    modifier = Modifier.weight(1f),
                )
                if (state.hasToken) {
                    VmButton(
                        text = "Disconnect",
                        onClick = { viewModel.clearToken { tokenDraft = "" } },
                        enabled = !state.isLoading,
                        style = VmButtonStyle.Secondary,
                    )
                }
            }
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
                            text = "Open Codespaces",
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(repo.codespacesUrl()))
                                try {
                                    context.startActivity(intent)
                                } catch (_: ActivityNotFoundException) {
                                    Toast.makeText(context, "No browser is available to open GitHub.", Toast.LENGTH_LONG).show()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
