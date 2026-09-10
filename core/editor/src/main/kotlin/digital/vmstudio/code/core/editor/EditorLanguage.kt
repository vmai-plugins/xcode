package digital.vmstudio.code.core.editor

/**
 * Supported editor languages for syntax highlighting.
 *
 * Each language defines the token rules used by [SyntaxHighlighter] to colorize
 * source code. The list is intentionally small at launch — it covers the most
 * common web and mobile development languages. Adding a language requires only
 * a new entry here plus a [SyntaxHighlighter] implementation.
 */
enum class EditorLanguage(
    val displayName: String,
    val fileExtensions: List<String>,
) {
    KOTLIN("Kotlin", listOf("kt", "kts")),
    JAVA("Java", listOf("java")),
    JAVASCRIPT("JavaScript", listOf("js", "jsx", "mjs")),
    TYPESCRIPT("TypeScript", listOf("ts", "tsx")),
    HTML("HTML", listOf("html", "htm")),
    CSS("CSS", listOf("css", "scss", "less")),
    JSON("JSON", listOf("json")),
    YAML("YAML", listOf("yaml", "yml")),
    MARKDOWN("Markdown", listOf("md", "markdown")),
    PYTHON("Python", listOf("py")),
    SHELL("Shell", listOf("sh", "bash", "zsh")),
    SQL("SQL", listOf("sql")),
    XML("XML", listOf("xml")),
    PLAIN("Plain Text", listOf("txt", "log", "conf", "ini")),
    ;

    companion object {
        /** Resolves a language from a file extension, or PLAIN if unknown. */
        fun fromFileName(fileName: String): EditorLanguage {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            return entries.firstOrNull { ext in it.fileExtensions } ?: PLAIN
        }
    }
}
