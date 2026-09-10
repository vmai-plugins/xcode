package digital.vmstudio.code.core.editor

/**
 * Language-specific highlighter instances.
 */
object SyntaxHighlighters {

    val kotlin = RegexSyntaxHighlighter(
        keywords = setOf(
            "as", "break", "class", "continue", "do", "else", "false", "for", "fun",
            "if", "in", "interface", "is", "null", "object", "package", "return",
            "super", "this", "throw", "true", "try", "typealias", "typeof", "val",
            "var", "when", "while", "by", "catch", "constructor", "delegate",
            "dynamic", "field", "file", "finally", "get", "import", "init",
            "param", "property", "receiver", "set", "setparam", "where", "actual",
            "abstract", "annotation", "companion", "const", "crossinline", "data",
            "enum", "expect", "external", "final", "infix", "inline", "inner",
            "internal", "lateinit", "noinline", "open", "operator", "out", "override",
            "private", "protected", "public", "reified", "sealed", "suspend",
            "tailrec", "vararg",
        ),
    )

    val java = RegexSyntaxHighlighter(
        keywords = setOf(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch",
            "char", "class", "const", "continue", "default", "do", "double",
            "else", "enum", "extends", "final", "finally", "float", "for",
            "goto", "if", "implements", "import", "instanceof", "int",
            "interface", "long", "native", "new", "package", "private",
            "protected", "public", "return", "short", "static", "strictfp",
            "super", "switch", "synchronized", "this", "throw", "throws",
            "transient", "try", "void", "volatile", "while", "true", "false", "null",
        ),
    )

    val javascript = RegexSyntaxHighlighter(
        keywords = setOf(
            "break", "case", "catch", "class", "const", "continue", "debugger",
            "default", "delete", "do", "else", "export", "extends", "false",
            "finally", "for", "function", "if", "import", "in", "instanceof",
            "let", "new", "null", "return", "super", "switch", "this", "throw",
            "true", "try", "typeof", "var", "void", "while", "with", "yield",
            "async", "await", "of",
        ),
    )

    val typescript = RegexSyntaxHighlighter(
        keywords = javascript.keywords + setOf(
            "abstract", "as", "asserts", "constructor", "declare", "enum",
            "implements", "interface", "is", "keyof", "module", "namespace",
            "never", "readonly", "string", "number", "boolean", "any", "unknown",
            "undefined", "unique", "symbol",
        ),
    )

    val python = RegexSyntaxHighlighter(
        keywords = setOf(
            "False", "None", "True", "and", "as", "assert", "async", "await",
            "break", "class", "continue", "def", "del", "elif", "else", "except",
            "finally", "for", "from", "global", "if", "import", "in", "is",
            "lambda", "nonlocal", "not", "or", "pass", "raise", "return",
            "try", "while", "with", "yield",
        ),
        lineCommentPrefix = "#",
        blockCommentStart = null,
        blockCommentEnd = null,
    )

    val shell = RegexSyntaxHighlighter(
        keywords = setOf(
            "if", "then", "else", "elif", "fi", "case", "esac", "for", "while",
            "until", "do", "done", "function", "return", "exit", "export",
            "readonly", "unset", "shift", "source",
        ),
        lineCommentPrefix = "#",
        blockCommentStart = null,
        blockCommentEnd = null,
    )

    val sql = RegexSyntaxHighlighter(
        keywords = setOf(
            "SELECT", "FROM", "WHERE", "INSERT", "INTO", "VALUES", "UPDATE",
            "SET", "DELETE", "CREATE", "TABLE", "ALTER", "DROP", "INDEX",
            "JOIN", "INNER", "LEFT", "RIGHT", "FULL", "OUTER", "ON",
            "GROUP", "BY", "ORDER", "HAVING", "LIMIT", "OFFSET", "UNION",
            "AND", "OR", "NOT", "IN", "BETWEEN", "LIKE", "IS", "NULL",
            "EXISTS", "CASE", "WHEN", "THEN", "ELSE", "END", "BEGIN",
            "COMMIT", "ROLLBACK", "PRIMARY", "KEY", "FOREIGN", "REFERENCES",
            "CONSTRAINT", "DEFAULT", "UNIQUE", "CHECK", "CASCADE",
        ),
        lineCommentPrefix = "--",
        blockCommentStart = "/*",
        blockCommentEnd = "*/",
    )

    val empty = object : SyntaxHighlighter {
        override fun tokenize(source: String, scheme: ColorScheme): List<SyntaxToken> = emptyList()
    }

    fun forLanguage(language: EditorLanguage): SyntaxHighlighter = when (language) {
        EditorLanguage.KOTLIN -> kotlin
        EditorLanguage.JAVA -> java
        EditorLanguage.JAVASCRIPT -> javascript
        EditorLanguage.TYPESCRIPT -> typescript
        EditorLanguage.PYTHON -> python
        EditorLanguage.SHELL -> shell
        EditorLanguage.SQL -> sql
        else -> empty
    }
}
