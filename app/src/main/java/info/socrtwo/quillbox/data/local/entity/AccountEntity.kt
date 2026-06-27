package info.socrtwo.quillbox.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import info.socrtwo.quillbox.data.model.MailProtocol
import info.socrtwo.quillbox.data.model.SecurityType

/**
 * A configured mail account. Credentials are stored locally only; nothing is
 * transmitted anywhere except the user's own mail servers.
 */
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val displayName: String,
    val email: String,
    // Incoming (IMAP/POP3)
    val incomingHost: String,
    val incomingPort: Int,
    val protocol: MailProtocol,
    val incomingSecurity: SecurityType,
    // Outgoing (SMTP)
    val smtpHost: String,
    val smtpPort: Int,
    val smtpSecurity: SecurityType,
    // Auth
    val username: String,
    val password: String
)
