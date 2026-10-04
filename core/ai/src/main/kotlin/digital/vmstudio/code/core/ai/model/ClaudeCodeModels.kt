package digital.vmstudio.code.core.ai.model

/**
 * Models the Claude Code CLI is offered from the chat picker.
 *
 * These are the CLI's own `--model` aliases, which it resolves to the current
 * release of each family, so the app never has to ship a dated model id.
 */
object ClaudeCodeModels {

    data class Choice(val id: String, val label: String, val description: String)

    val CHOICES: List<Choice> = listOf(
        Choice("sonnet", "Sonnet", "Everyday coding"),
        Choice("opus", "Opus", "Hardest problems"),
        Choice("haiku", "Haiku", "Fast and light"),
    )

    val ALIASES: Set<String> = CHOICES.map { it.id }.toSet()

    const val DEFAULT: String = "sonnet"

    /** True when [model] is run by Claude Code rather than sent to the HTTP gateway. */
    fun isClaudeCode(model: String): Boolean = model in ALIASES
}
