package digital.vmstudio.code.core.connectors.sheets

import digital.vmstudio.code.core.common.result.VmResult
import digital.vmstudio.code.core.common.result.flatMap
import digital.vmstudio.code.core.common.result.vmCatching
import digital.vmstudio.code.core.network.http.RetryPolicy
import digital.vmstudio.code.core.network.http.VmHttpClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Serializable
data class SheetValueRange(
    val range: String? = null,
    val majorDimension: String = "ROWS",
    val values: List<List<JsonElement>> = emptyList(),
) {
    val stringValues: List<List<String>>
        get() = values.map { row ->
            row.map { cell ->
                if (cell is JsonPrimitive) {
                    cell.contentOrNull ?: cell.toString()
                } else {
                    cell.toString()
                }
            }
        }
}

@Serializable
data class AppendValuesResponse(
    val spreadsheetId: String? = null,
    val tableRange: String? = null,
    val updates: SheetUpdates? = null,
)

@Serializable
data class SheetUpdates(
    val spreadsheetId: String? = null,
    val updatedRange: String? = null,
    val updatedRows: Int = 0,
    val updatedColumns: Int = 0,
    val updatedCells: Int = 0,
)

/**
 * Client for Google Sheets REST API v4.
 * Enables read, write, and append operations on Google Spreadsheets.
 */
@Singleton
class GoogleSheetsClient @Inject constructor(
    private val httpClient: VmHttpClient,
    private val json: Json,
) {

    /**
     * Reads cell values from a specified spreadsheet range (e.g., "Sheet1!A1:D10").
     */
    suspend fun getValues(
        accessToken: String,
        spreadsheetId: String,
        range: String,
    ): VmResult<SheetValueRange> {
        val encodedRange = URLEncoder.encode(range, "UTF-8").replace("+", "%20")
        val url = "$SHEETS_API_BASE/$spreadsheetId/values/$encodedRange"

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()

        return httpClient.execute(request, RetryPolicy())
            .flatMap { body ->
                vmCatching {
                    json.decodeFromString<SheetValueRange>(body)
                }
            }
    }

    /**
     * Appends rows to a spreadsheet after the last row with data in the specified range.
     */
    suspend fun appendValues(
        accessToken: String,
        spreadsheetId: String,
        range: String,
        rows: List<List<String>>,
    ): VmResult<AppendValuesResponse> {
        val encodedRange = URLEncoder.encode(range, "UTF-8").replace("+", "%20")
        val url = "$SHEETS_API_BASE/$spreadsheetId/values/$encodedRange:append?valueInputOption=USER_ENTERED"

        val requestBodyJson = buildJsonObject {
            put("range", range)
            put("majorDimension", "ROWS")
            putJsonArray("values") {
                rows.forEach { row ->
                    add(kotlinx.serialization.json.buildJsonArray {
                        row.forEach { cell -> add(kotlinx.serialization.json.JsonPrimitive(cell)) }
                    })
                }
            }
        }.toString()

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .post(requestBodyJson.toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .build()

        return httpClient.execute(request, RetryPolicy(maxAttempts = 2))
            .flatMap { body ->
                vmCatching {
                    json.decodeFromString<AppendValuesResponse>(body)
                }
            }
    }

    companion object {
        private const val SHEETS_API_BASE = "https://sheets.googleapis.com/v4/spreadsheets"
    }
}
