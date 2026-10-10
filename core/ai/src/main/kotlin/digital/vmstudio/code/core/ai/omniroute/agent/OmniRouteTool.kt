package digital.vmstudio.code.core.ai.omniroute.agent

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * The tools the OmniRoute agent loop offers a model, independent of which wire
 * dialect eventually carries them.
 *
 * Deliberately small: four tools cover "audit and edit a project" (read, list,
 * write, run a command) without trying to match Claude Code CLI's much larger
 * toolset. `run_command` alone reaches everything a shell can do, the same way
 * it does for a human at a terminal.
 */
enum class OmniRouteTool(val toolName: String, val description: String) {
    READ_FILE(
        "read_file",
        "Read a text file's contents from the project. Fails if the file is too " +
            "large or does not exist.",
    ),
    LIST_DIRECTORY(
        "list_directory",
        "List the files and subdirectories at a path in the project.",
    ),
    WRITE_FILE(
        "write_file",
        "Create or overwrite a text file with the given content. Always writes the " +
            "complete file.",
    ),
    EDIT_FILE(
        "edit_file",
        "Perform a targeted search-and-replace edit on an existing file. " +
            "Replaces target_content with replacement_content.",
    ),
    GREP_SEARCH(
        "grep_search",
        "Search for a pattern or regular expression across files in the project. " +
            "Returns matching files and lines.",
    ),
    WEB_FETCH(
        "web_fetch",
        "Fetch a public web page or text URL (http or https) and return its readable " +
            "text, up to about 20,000 characters. Use it for documentation, changelogs " +
            "and API references.",
    ),
    RUN_COMMAND(
        "run_command",
        "Run a shell command on the server and return its output. Subject to the " +
            "same safety review as a command a person types.",
    ),
    RUN_BUILD(
        "run_build",
        "Build the project (run ./gradlew assembleRelease) from the " +
            "working directory and return the artifact path plus build output.",
    ),
    LIST_APKS(
        "list_apks",
        "List APK files under a directory (defaults to the working directory).",
    ),
    GOOGLE_SHEET_READ(
        "google_sheet_read",
        "Read rows and cells from a Google Spreadsheet by spreadsheet_id and range (e.g. Sheet1!A1:D10).",
    ),
    GOOGLE_SHEET_APPEND(
        "google_sheet_append",
        "Append a row of comma-separated or tab-separated values to a Google Spreadsheet by spreadsheet_id and range.",
    ),
    ;

    companion object {
        fun fromToolName(name: String): OmniRouteTool? = entries.firstOrNull { it.toolName == name }
    }
}

/** JSON Schema for each tool's arguments, shared by both wire dialects. */
private fun OmniRouteTool.parametersSchema(): JsonObject = when (this) {
    OmniRouteTool.READ_FILE, OmniRouteTool.LIST_DIRECTORY -> buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("path") {
                put("type", "string")
                put("description", "Absolute or working-directory-relative path.")
            }
        }
        putJsonArray("required") { add("path") }
    }

    OmniRouteTool.WRITE_FILE -> buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("path") {
                put("type", "string")
                put("description", "Absolute or working-directory-relative path.")
            }
            putJsonObject("content") {
                put("type", "string")
                put("description", "The complete new contents of the file.")
            }
        }
        putJsonArray("required") {
            add("path")
            add("content")
        }
    }

    OmniRouteTool.EDIT_FILE -> buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("path") {
                put("type", "string")
                put("description", "Absolute or working-directory-relative path.")
            }
            putJsonObject("target_content") {
                put("type", "string")
                put(
                    "description",
                    "The exact lines or block of code to find and replace. " +
                        "Must match existing file content exactly.",
                )
            }
            putJsonObject("replacement_content") {
                put("type", "string")
                put("description", "The replacement content to substitute.")
            }
        }
        putJsonArray("required") {
            add("path")
            add("target_content")
            add("replacement_content")
        }
    }

    OmniRouteTool.GREP_SEARCH -> buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("query") {
                put("type", "string")
                put("description", "Search string or regular expression.")
            }
            putJsonObject("path") {
                put("type", "string")
                put("description", "Directory or file to search within. Defaults to the working directory.")
            }
            putJsonObject("case_sensitive") {
                put("type", "boolean")
                put("description", "Whether the search is case-sensitive. Defaults to false.")
            }
        }
        putJsonArray("required") { add("query") }
    }

    OmniRouteTool.WEB_FETCH -> buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("url") {
                put("type", "string")
                put("description", "The full http or https URL to fetch.")
            }
        }
        putJsonArray("required") { add("url") }
    }

    OmniRouteTool.RUN_COMMAND -> buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("command") {
                put("type", "string")
                put("description", "A shell command, run from the project's working directory.")
            }
        }
        putJsonArray("required") { add("command") }
    }
    OmniRouteTool.RUN_BUILD -> buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("directory") {
                put("type", "string")
                put("description", "Working directory to run the build from (defaults to the project's working directory).")
            }
            putJsonObject("tasks") {
                put("type", "string")
                put("description", "Gradle tasks to run. Defaults to assembleRelease.")
            }
        }
        putJsonArray("required") { }
    }
    OmniRouteTool.LIST_APKS -> buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("directory") {
                put("type", "string")
                put("description", "Directory to scan for APKs (defaults to the working directory).")
            }
        }
        putJsonArray("required") { }
    }
    OmniRouteTool.GOOGLE_SHEET_READ -> buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("spreadsheet_id") {
                put("type", "string")
                put("description", "The Google Sheets spreadsheet ID.")
            }
            putJsonObject("range") {
                put("type", "string")
                put("description", "The A1 notation range to read (e.g. Sheet1!A1:D25).")
            }
        }
        putJsonArray("required") {
            add("spreadsheet_id")
            add("range")
        }
    }
    OmniRouteTool.GOOGLE_SHEET_APPEND -> buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("spreadsheet_id") {
                put("type", "string")
                put("description", "The Google Sheets spreadsheet ID.")
            }
            putJsonObject("range") {
                put("type", "string")
                put("description", "The table or sheet range to append to (e.g. Sheet1!A1).")
            }
            putJsonObject("values") {
                put("type", "string")
                put("description", "Comma-separated or newline-separated values for the new row(s).")
            }
        }
        putJsonArray("required") {
            add("spreadsheet_id")
            add("range")
            add("values")
        }
    }
}

/** OpenAI `tools` array entry: `{type: "function", function: {name, description, parameters}}`. */
fun OmniRouteTool.toOpenAiToolJson(): JsonObject = buildJsonObject {
    put("type", "function")
    putJsonObject("function") {
        put("name", toolName)
        put("description", description)
        put("parameters", parametersSchema())
    }
}

/** Anthropic `tools` array entry: `{name, description, input_schema}`. */
fun OmniRouteTool.toAnthropicToolJson(): JsonObject = buildJsonObject {
    put("name", toolName)
    put("description", description)
    put("input_schema", parametersSchema())
}
