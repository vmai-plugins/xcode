package digital.vmstudio.code.feature.servers

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import digital.vmstudio.code.core.common.error.VmError
import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.ssh.recipes.Recipe
import digital.vmstudio.code.core.ssh.recipes.RecipeExecutor
import digital.vmstudio.code.core.ssh.recipes.RecipeInstallStatus
import digital.vmstudio.code.core.ssh.recipes.RecipeRegistry
import digital.vmstudio.code.core.ssh.repository.ServerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Progress of a recipe run in flight. */
data class RecipeProgress(
    val recipeId: String,
    val fraction: Double,
    val label: String,
)

/** Per-recipe UI state: not installed, installing (with progress), or installed. */
data class RecipeUiItem(
    val recipe: Recipe,
    val status: RecipeInstallStatus,
    val progress: RecipeProgress? = null,
    val error: VmError? = null,
)

data class RecipeListUiState(
    val serverName: String = "",
    val isLoading: Boolean = true,
    val recipes: List<RecipeUiItem> = emptyList(),
    val error: VmError? = null,
) {
    val hasRecipes: Boolean get() = recipes.isNotEmpty()
}

/**
 * Lists available recipes and runs them against the current server.
 *
 * Every recipe command goes through [RecipeExecutor], which routes through the
 * same approval gate as the terminal. A denied step stops the run and surfaces
 * the error — the recipe is not a privileged channel.
 */
@HiltViewModel
class RecipeViewModel @Inject constructor(
    private val recipeExecutor: RecipeExecutor,
    serverRepository: ServerRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val serverId: String = requireNotNull(savedStateHandle[ARG_SERVER_ID]) {
        "RecipeList requires a serverId"
    }

    private val serverName = MutableStateFlow("")
    private val isLoading = MutableStateFlow(true)
    private val recipeItems = MutableStateFlow<List<RecipeUiItem>>(emptyList())
    private val error = MutableStateFlow<VmError?>(null)

    val uiState: StateFlow<RecipeListUiState> = combine(
        serverName,
        isLoading,
        recipeItems,
        error,
        serverRepository.observe(serverId),
    ) { name, loading, items, err, server ->
        RecipeListUiState(
            serverName = server?.name ?: name,
            isLoading = loading,
            recipes = items,
            error = err,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RecipeListUiState(),
    )

    init {
        loadRecipes()
    }

    private fun loadRecipes() {
        viewModelScope.launch {
            isLoading.value = true
            val items = RecipeRegistry.all.map { recipe ->
                val result = recipeExecutor.checkStatus(serverId, recipe)
                RecipeUiItem(
                    recipe = recipe,
                    status = when (result) {
                        is VmResult.Success -> result.value
                        is VmResult.Failure -> RecipeInstallStatus.UNKNOWN
                    },
                )
            }
            recipeItems.value = items
            isLoading.value = false
        }
    }

    fun runRecipe(recipeId: String) {
        val recipe = RecipeRegistry.byId(recipeId) ?: return
        viewModelScope.launch {
            setRecipeProgress(recipeId, 0.0, "Starting…")

            val result = recipeExecutor.run(
                serverId = serverId,
                recipe = recipe,
                onProgress = { fraction, label ->
                    setRecipeProgress(recipeId, fraction, label)
                },
            )

            when (result) {
                is VmResult.Success -> {
                    setRecipeStatus(recipeId, RecipeInstallStatus.INSTALLED)
                }
                is VmResult.Failure -> {
                    setRecipeError(recipeId, result.error)
                }
            }
        }
    }

    fun clearError(recipeId: String) {
        recipeItems.value = recipeItems.value.map { item ->
            if (item.recipe.id == recipeId) item.copy(error = null, progress = null)
            else item
        }
    }

    private fun setRecipeProgress(recipeId: String, fraction: Double, label: String) {
        recipeItems.value = recipeItems.value.map { item ->
            if (item.recipe.id == recipeId) {
                item.copy(
                    progress = RecipeProgress(recipeId, fraction, label),
                    error = null,
                )
            } else item
        }
    }

    private fun setRecipeStatus(recipeId: String, status: RecipeInstallStatus) {
        recipeItems.value = recipeItems.value.map { item ->
            if (item.recipe.id == recipeId) {
                item.copy(status = status, progress = null, error = null)
            } else item
        }
    }

    private fun setRecipeError(recipeId: String, err: VmError) {
        recipeItems.value = recipeItems.value.map { item ->
            if (item.recipe.id == recipeId) {
                item.copy(progress = null, error = err)
            } else item
        }
    }

    private companion object {
        const val ARG_SERVER_ID = "serverId"
    }
}
