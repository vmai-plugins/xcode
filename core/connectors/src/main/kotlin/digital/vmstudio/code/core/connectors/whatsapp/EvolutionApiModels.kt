package digital.vmstudio.code.core.connectors.whatsapp

import kotlinx.serialization.Serializable

@Serializable
data class EvolutionSendTextRequest(
    val number: String,
    val text: String,
    val linkPreview: Boolean = true,
)

@Serializable
data class EvolutionSendMediaRequest(
    val number: String,
    val mediatype: String = "document",
    val mimetype: String = "application/octet-stream",
    val caption: String? = null,
    val media: String,
    val fileName: String,
)

@Serializable
data class EvolutionSendResponse(
    val key: EvolutionMessageKey? = null,
    val status: String? = null,
)

@Serializable
data class EvolutionMessageKey(
    val id: String? = null,
    val remoteJid: String? = null,
    val fromMe: Boolean = false,
)

@Serializable
data class EvolutionConnectionStateResponse(
    val instance: EvolutionInstanceInfo? = null,
)

@Serializable
data class EvolutionInstanceInfo(
    val instanceName: String? = null,
    val state: String? = null,
)

data class EvolutionConfig(
    val baseUrl: String = "https://wa.vmstudio.digital",
    val apiKey: String,
    val instance: String,
    val recipientNumber: String? = null,
) {
    val isConfigured: Boolean
        get() = apiKey.isNotBlank() && instance.isNotBlank()
}
