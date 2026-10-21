package digital.vmstudio.code.feature.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import digital.vmstudio.code.core.git.codespacesUrl
import digital.vmstudio.code.core.ui.component.VmButton
import digital.vmstudio.code.core.ui.component.VmButtonStyle
import digital.vmstudio.code.core.ui.component.VmCard

@Composable
fun GitHubSettingsScreen(
    modifier: Modifier = Modifier,
    onImportRepo: (url: String, branch: String) -> Unit = { _, _ -> },
    viewModel: GitHubSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var tokenDraft by remember { mutableStateOf("") }
    var externalRepoUrl by remember { mutableStateOf("") }
    var externalBranch by remember { mutableStateOf("main") }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        VmCard {
            Text("GitHub Personal Access Token", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "Connect your GitHub PAT to list your repositories, clone them directly into X-Codes projects (Local or VPS), or launch GitHub Codespaces.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = tokenDraft,
                onValueChange = { tokenDraft = it },
                label = { Text("Personal Access Token (repo scope)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
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

        // Quick Import Any External GitHub Repo
        VmCard {
            Text("Clone Any GitHub Repository", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "Import any public or accessible repository into a new X-Codes workspace by pasting its URL or owner/repository.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = externalRepoUrl,
                onValueChange = { externalRepoUrl = it },
                label = { Text("Repository URL or owner/name") },
                placeholder = { Text("e.g. torvalds/linux or https://github.com/...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = externalBranch,
                    onValueChange = { externalBranch = it },
                    label = { Text("Branch") },
                    singleLine = true,
                    modifier = Modifier.weight(0.4f),
                )
                VmButton(
                    text = "Import as Project",
                    onClick = {
                        val trimmed = externalRepoUrl.trim()
                        val url = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                            trimmed
                        } else {
                            "https://github.com/$trimmed.git"
                        }
                        onImportRepo(url, externalBranch.trim().ifBlank { "main" })
                    },
                    enabled = externalRepoUrl.isNotBlank(),
                    style = VmButtonStyle.Primary,
                    modifier = Modifier.weight(0.6f),
                )
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
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column {
                            Text(repo.fullName, fontWeight = FontWeight.Bold)
                            Text(repo.description ?: "No description", style = MaterialTheme.typography.bodySmall)
                            Text(
                                "Default branch: ${repo.defaultBranch} • ${if (repo.isPrivate) "Private" else "Public"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            VmButton(
                                text = "Import to Projects",
                                onClick = { onImportRepo(repo.cloneUrl, repo.defaultBranch) },
                                style = VmButtonStyle.Primary,
                                modifier = Modifier.weight(1f),
                            )
                            VmButton(
                                text = "Codespaces",
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(repo.codespacesUrl()))
                                    try {
                                        context.startActivity(intent)
                                    } catch (_: ActivityNotFoundException) {
                                        Toast.makeText(context, "No browser is available to open GitHub.", Toast.LENGTH_LONG).show()
                                    }
                                },
                                style = VmButtonStyle.Secondary,
                            )
                        }
                    }
                }
            }
        }
    }
}
