package digital.vmstudio.code.core.ssh.recipes

/**
 * A server recipe is a scripted, repeatable setup that provisions a development
 * stack on a remote server. Each step is a guarded command; the whole recipe runs
 * through [RecipeExecutor], which routes every command through the same approval
 * gate as the terminal and agent.
 *
 * Recipes are declarative — they describe *what* to install, not *how* to prompt.
 * Progress callbacks carry a normalized 0..1 fraction plus a human status line,
 * so the UI can render progress without knowing the recipe's internals.
 */
data class Recipe(
    val id: String,
    val name: String,
    val description: String,
    /** Estimated wall-clock seconds, for UI display only. */
    val estimatedSeconds: Int,
    val steps: List<RecipeStep>,
    /**
     * Read-only command that detects whether this recipe is already installed.
     * Exit code 0 with non-empty stdout means installed; non-zero means not.
     * Null means the recipe has no reliable probe and status is always UNKNOWN.
     */
    val statusProbe: String? = null,
)

data class RecipeStep(
    /** Fraction of total progress this step completes (0..1). */
    val progressWeight: Double,
    val label: String,
    val command: String,
)

/**
 * Result of checking whether a recipe is already installed on a server.
 */
enum class RecipeInstallStatus {
    INSTALLED,
    NOT_INSTALLED,
    UNKNOWN,
}

data class RecipeState(
    val recipeId: String,
    val status: RecipeInstallStatus,
    /** Installed path, when detectable. */
    val path: String? = null,
)
