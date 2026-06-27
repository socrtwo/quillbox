package info.socrtwo.quillbox.web

import kotlinx.serialization.Serializable

@Serializable
data class AccountDto(
    val displayName: String = "",
    val email: String,
    val incomingHost: String,
    val incomingPort: Int,
    val protocol: String,          // "IMAP" or "POP3"
    val incomingSecurity: String,  // "SSL_TLS", "STARTTLS", or "NONE"
    val smtpHost: String,
    val smtpPort: Int,
    val smtpSecurity: String,
    val username: String,
    val password: String
)

@Serializable
data class MessageDto(
    val from: String,
    val to: String,
    val subject: String,
    val bodyText: String,
    val bodyHtml: String? = null,
    val sentDate: Long,
    val hasAttachments: Boolean
)

@Serializable
data class InboxRequest(val account: AccountDto, val limit: Int = 100)

@Serializable
data class SendRequest(
    val account: AccountDto,
    val to: List<String>,
    val cc: List<String> = emptyList(),
    val bcc: List<String> = emptyList(),
    val subject: String,
    val body: String
)

@Serializable
data class ApiError(val error: String)

@Serializable
data class ApiStatus(val status: String)
