package digital.vmstudio.code.core.connectors.gmail

import kotlinx.serialization.Serializable

@Serializable
data class GmailMessageRef(
    val id: String,
    val threadId: String? = null,
)

@Serializable
data class GmailMessageListResponse(
    val messages: List<GmailMessageRef> = emptyList(),
    val nextPageToken: String? = null,
    val resultSizeEstimate: Int = 0,
)

@Serializable
data class GmailHeader(
    val name: String,
    val value: String,
)

@Serializable
data class GmailMessagePartBody(
    val size: Int = 0,
    val data: String? = null,
)

@Serializable
data class GmailMessagePart(
    val partId: String? = null,
    val mimeType: String? = null,
    val filename: String? = null,
    val headers: List<GmailHeader> = emptyList(),
    val body: GmailMessagePartBody? = null,
    val parts: List<GmailMessagePart> = emptyList(),
)

@Serializable
data class GmailMessage(
    val id: String,
    val threadId: String? = null,
    val labelIds: List<String> = emptyList(),
    val snippet: String? = null,
    val payload: GmailMessagePart? = null,
    val sizeEstimate: Int = 0,
) {
    val from: String?
        get() = payload?.headers?.firstOrNull { it.name.equals("From", ignoreCase = true) }?.value

    val to: String?
        get() = payload?.headers?.firstOrNull { it.name.equals("To", ignoreCase = true) }?.value

    val subject: String?
        get() = payload?.headers?.firstOrNull { it.name.equals("Subject", ignoreCase = true) }?.value

    val date: String?
        get() = payload?.headers?.firstOrNull { it.name.equals("Date", ignoreCase = true) }?.value
}

@Serializable
data class GmailSendResponse(
    val id: String? = null,
    val threadId: String? = null,
    val labelIds: List<String> = emptyList(),
)

@Serializable
data class GmailDraft(
    val id: String? = null,
    val message: GmailMessage? = null,
)
