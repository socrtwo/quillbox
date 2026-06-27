package info.socrtwo.quillbox.desktop.model

enum class MailProtocol { IMAP, POP3 }

enum class SecurityType { SSL_TLS, STARTTLS, NONE }

/** A configured mail account (held in memory for this session). */
data class Account(
    val displayName: String,
    val email: String,
    val incomingHost: String,
    val incomingPort: Int,
    val protocol: MailProtocol,
    val incomingSecurity: SecurityType,
    val smtpHost: String,
    val smtpPort: Int,
    val smtpSecurity: SecurityType,
    val username: String,
    val password: String
)
