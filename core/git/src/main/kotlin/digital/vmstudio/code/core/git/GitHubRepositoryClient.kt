package digital.vmstudio.code.core.git

import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.preferences.UserPreferencesRepository
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.network.http.RetryPolicy
import digital.vmstudio.code.core.network.http.VmHttpClient
import digital.vmstudio.code.core.security.model.Secret
import digital.vmstudio.code.core.security.model.SecretType
import digital.vmstudio.code.core.security.store.SecureCredentialStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

data class GitHubRepository(
    val id: Long,
    val fullName: String,
    val description: String?,
    val cloneUrl: String,
    val defaultBranch: String,
    val isPrivate: Boolean,
)

data class GitHubTreeItem(
    val path: String,
    val type: String,
    val size: Long = 0,
)


fun GitHubRepository.codespacesUrl(): String {
    require(fullName.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))) {
        "GitHub returned an invalid repository name."
    }
    val encodedName = URLEncoder.encode(fullName, StandardCharsets.UTF_8.name())
    return "https://github.com/codespaces/new?repo=$encodedName"
}

@Singleton
class GitHubRepositoryClient @Inject constructor(
    private val httpClient: VmHttpClient,
    private val preferences: UserPreferencesRepository,
    private val credentialStore: SecureCredentialStore,
) {
    private val repositoryCacheMutex = Mutex()
    private var cachedCredentialId: String? = null
    private var cachedRepositories: List<GitHubRepository>? = null

    suspend fun saveToken(token: String): VmResult<Unit> {
        val cleaned = token.trim()
        if (cleaned.isBlank()) {
            return VmResult.Failure(VmError.Validation(summary = "GitHub token cannot be empty"))
        }

        val previousId = preferences.preferences.first().githubTokenCredentialId
        val secret = Secret.of(cleaned)
        val stored = try {
            credentialStore.put(
                type = SecretType.GIT_HTTP_TOKEN,
                label = "GitHub Token",
                secret = secret,
            )
        } finally {
            secret.wipe()
        }
        val credentialId = when (stored) {
            is VmResult.Failure -> return stored
            is VmResult.Success -> stored.value.id
        }
        preferences.setGithubConnection(credentialId, null)
        repositoryCacheMutex.lock()
        try {
            cachedCredentialId = null
            cachedRepositories = null
        } finally {
            repositoryCacheMutex.unlock()
        }
        previousId?.takeIf { it != credentialId }?.let { oldId ->
            if (credentialStore.reference(oldId) is VmResult.Success) {
                runCatching { credentialStore.delete(oldId) }
            }
        }
        return VmResult.Success(Unit)
    }

    suspend fun hasToken(): Boolean =
        preferences.preferences.first().githubTokenCredentialId != null

    suspend fun clearToken(): VmResult<Unit> {
        val currentId = preferences.preferences.first().githubTokenCredentialId
        preferences.setGithubConnection(null, null)
        repositoryCacheMutex.lock()
        try {
            cachedCredentialId = null
            cachedRepositories = null
        } finally {
            repositoryCacheMutex.unlock()
        }
        currentId?.let { id ->
            if (credentialStore.reference(id) is VmResult.Success) {
                credentialStore.delete(id)
            }
        }
        return VmResult.Success(Unit)
    }

    suspend fun repositories(forceRefresh: Boolean = false): VmResult<List<GitHubRepository>> {
        repositoryCacheMutex.lock()
        try {
            val credentialId = preferences.preferences.first().githubTokenCredentialId
            if (!forceRefresh && credentialId != null && credentialId == cachedCredentialId) {
                cachedRepositories?.let { return VmResult.Success(it) }
            }

            return when (val result = fetchRepositories()) {
                is VmResult.Failure -> result
                is VmResult.Success -> {
                    cachedCredentialId = credentialId
                    cachedRepositories = result.value
                    result
                }
            }
        } finally {
            repositoryCacheMutex.unlock()
        }
    }

    private suspend fun fetchRepositories(): VmResult<List<GitHubRepository>> {
        val secret = when (val result = readToken()) {
            is VmResult.Success -> result.value
            is VmResult.Failure -> return result
        }

        return try {
            val token = secret.useAsString { it }
            val repositories = mutableListOf<GitHubRepository>()
            var page = 1
            var hasMore = true
            while (hasMore && page <= MAX_PAGES) {
                    val request = Request.Builder()
                        .url("$REPOSITORIES_URL&page=$page")
                        .header("Authorization", "Bearer $token")
                        .header("Accept", "application/vnd.github+json")
                        .header("X-GitHub-Api-Version", "2022-11-28")
                        .build()
                    when (val response = httpClient.execute(request, RetryPolicy.NONE)) {
                        is VmResult.Failure -> {
                            // If we already loaded at least one page, return what we have rather than total failure
                            if (repositories.isNotEmpty()) return VmResult.Success(repositories)
                            return response
                        }
                        is VmResult.Success -> {
                            when (val parsed = parseRepositories(response.value)) {
                                is VmResult.Failure -> {
                                    if (repositories.isNotEmpty()) return VmResult.Success(repositories)
                                    return parsed
                                }
                                is VmResult.Success -> {
                                    repositories += parsed.value
                                    hasMore = parsed.value.size == PAGE_SIZE
                                }
                            }
                        }
                    }
                page++
            }
            VmResult.Success(repositories)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            VmResult.Failure(
                VmError.Network(
                    summary = "Could not load GitHub repositories",
                    reason = exception.message,
                ),
            )
        } finally {
            secret.wipe()
        }
    }

    suspend fun readToken(): VmResult<Secret> {
        val credentialId = preferences.preferences.first().githubTokenCredentialId
            ?: return VmResult.Failure(
                VmError.Validation(
                    summary = "Connect a GitHub token first",
                    suggestedAction = "Add a personal access token in Settings → GitHub Integration.",
                ),
            )
        return credentialStore.read(credentialId)
    }

    private fun parseRepositories(body: String): VmResult<List<GitHubRepository>> = try {
        val json = JSONArray(body)
        VmResult.Success(
            List(json.length()) { index ->
                val repo = json.getJSONObject(index)
                GitHubRepository(
                    id = repo.getLong("id"),
                    fullName = repo.getString("full_name"),
                    description = repo.optString("description").takeIf(String::isNotBlank),
                    cloneUrl = repo.getString("clone_url"),
                    defaultBranch = repo.optString("default_branch").ifBlank { "main" },
                    isPrivate = repo.optBoolean("private"),
                )
            },
        )
    } catch (exception: Exception) {
        VmResult.Failure(
            VmError.Network(
                summary = "GitHub returned an invalid repository list",
                reason = exception.message,
            ),
        )
    }

    private fun encodePath(path: String): String =
        path.trimStart('/').split('/').joinToString("/") { segment ->
            URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
        }

    private fun encodeParam(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    suspend fun getFileContent(repoFullName: String, path: String, ref: String? = null): VmResult<String> {
        val cleanPath = path.trim().removePrefix("/")
        if (cleanPath.isEmpty()) {
            return VmResult.Failure(
                VmError.FileSystem(
                    summary = "Cannot read repository root as a file",
                    reason = "Target path resolves to the repository root directory.",
                    path = path,
                ),
            )
        }
        val secret = when (val result = readToken()) {
            is VmResult.Success -> result.value
            is VmResult.Failure -> return result
        }
        return try {
            val token = secret.useAsString { it }
            val encodedPath = encodePath(cleanPath)
            val refParam = if (!ref.isNullOrBlank()) "?ref=${encodeParam(ref)}" else ""
            val request = Request.Builder()
                .url("https://api.github.com/repos/$repoFullName/contents/$encodedPath$refParam")
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github.raw+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .build()
            when (val response = httpClient.execute(request, RetryPolicy.NONE)) {
                is VmResult.Failure -> response
                is VmResult.Success -> {
                    val body = response.value
                    if (body.trimStart().startsWith("[")) {
                        VmResult.Failure(
                            VmError.FileSystem(
                                summary = "Cannot read directory as a file",
                                reason = "Target path '$path' resolves to a directory.",
                                path = path,
                            ),
                        )
                    } else {
                        VmResult.Success(body)
                    }
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            VmResult.Failure(
                VmError.Network(
                    summary = "Could not read file from GitHub",
                    reason = exception.message,
                ),
            )
        } finally {
            secret.wipe()
        }
    }

    suspend fun getRepositoryTree(
        repoFullName: String,
        ref: String = "HEAD",
        recursive: Boolean = true,
    ): VmResult<List<GitHubTreeItem>> {
        val secret = when (val result = readToken()) {
            is VmResult.Success -> result.value
            is VmResult.Failure -> return result
        }
        return try {
            val token = secret.useAsString { it }
            val encodedRef = encodeParam(ref)
            val recursiveParam = if (recursive) "?recursive=1" else ""
            val request = Request.Builder()
                .url("https://api.github.com/repos/$repoFullName/git/trees/$encodedRef$recursiveParam")
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .build()
            when (val response = httpClient.execute(request, RetryPolicy.NONE)) {
                is VmResult.Failure -> response
                is VmResult.Success -> {
                    val json = org.json.JSONObject(response.value)
                    val treeArray = json.optJSONArray("tree") ?: JSONArray()
                    val items = mutableListOf<GitHubTreeItem>()
                    for (i in 0 until treeArray.length()) {
                        val obj = treeArray.getJSONObject(i)
                        items.add(
                            GitHubTreeItem(
                                path = obj.optString("path"),
                                type = obj.optString("type"),
                                size = obj.optLong("size", 0),
                            ),
                        )
                    }
                    VmResult.Success(items)
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            VmResult.Failure(
                VmError.Network(
                    summary = "Could not fetch repository tree from GitHub",
                    reason = exception.message,
                ),
            )
        } finally {
            secret.wipe()
        }
    }

    suspend fun putFileContent(
        repoFullName: String,
        path: String,
        content: String,
        commitMessage: String,
        branch: String? = null,
    ): VmResult<Unit> {
        val secret = when (val result = readToken()) {
            is VmResult.Success -> result.value
            is VmResult.Failure -> return result
        }
        return try {
            val token = secret.useAsString { it }
            val encodedPath = encodePath(path)
            val refQuery = if (!branch.isNullOrBlank()) "?ref=${encodeParam(branch)}" else ""
            val metaRequest = Request.Builder()
                .url("https://api.github.com/repos/$repoFullName/contents/$encodedPath$refQuery")
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .build()
            val existingSha = when (val metaResponse = httpClient.execute(metaRequest, RetryPolicy.NONE)) {
                is VmResult.Success -> {
                    val body = metaResponse.value.trimStart()
                    if (body.startsWith("[")) {
                        return VmResult.Failure(
                            VmError.FileSystem(
                                summary = "Cannot overwrite directory with file content",
                                reason = "Target path '$path' in $repoFullName resolves to a directory.",
                                path = path,
                            ),
                        )
                    }
                    val sha = runCatching {
                        org.json.JSONObject(body).optString("sha").takeIf { it.isNotBlank() }
                    }.getOrNull()
                    if (sha == null) {
                        return VmResult.Failure(
                            VmError.Network(
                                summary = "GitHub did not return a valid file blob SHA for '$path'",
                                reason = body.take(200),
                            ),
                        )
                    }
                    sha
                }
                is VmResult.Failure -> {
                    val err = metaResponse.error
                    if (err is VmError.Network && err.statusCode == 404) {
                        null
                    } else {
                        return metaResponse
                    }
                }
            }

            val encodedContent = android.util.Base64.encodeToString(
                content.toByteArray(StandardCharsets.UTF_8),
                android.util.Base64.NO_WRAP,
            )
            val bodyJson = org.json.JSONObject().apply {
                put("message", commitMessage)
                put("content", encodedContent)
                if (existingSha != null) put("sha", existingSha)
                if (!branch.isNullOrBlank()) put("branch", branch)
            }.toString()

            val putRequest = Request.Builder()
                .url("https://api.github.com/repos/$repoFullName/contents/$encodedPath")
                .put(bodyJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .build()

            when (val putResponse = httpClient.execute(putRequest, RetryPolicy.NONE)) {
                is VmResult.Failure -> putResponse
                is VmResult.Success -> VmResult.Success(Unit)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            VmResult.Failure(
                VmError.Network(
                    summary = "Could not write file to GitHub",
                    reason = exception.message,
                ),
            )
        } finally {
            secret.wipe()
        }
    }

    private companion object {
        const val PAGE_SIZE = 100
        const val MAX_PAGES = 10
        const val REPOSITORIES_URL =
            "https://api.github.com/user/repos?sort=updated&per_page=$PAGE_SIZE&affiliation=owner,collaborator,organization_member"
    }
}
