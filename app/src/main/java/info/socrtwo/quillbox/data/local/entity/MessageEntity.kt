package info.socrtwo.quillbox.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A downloaded mail message (headers + body) stored locally. */
@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["accountId"]),
        Index(value = ["folderId"]),
        Index(value = ["accountId", "messageId"], unique = true)
    ]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    /** Local folder this message currently lives in (may change via rules). */
    val folderId: Long,
    /** RFC 822 Message-ID (or a synthesized fallback) used for de-duplication. */
    val messageId: String,
    val fromAddress: String,
    val toAddresses: String,
    val ccAddresses: String = "",
    val subject: String,
    val bodyText: String,
    val bodyHtml: String? = null,
    val sentDate: Long,
    val receivedDate: Long,
    val isRead: Boolean = false,
    val hasAttachments: Boolean = false,
    // --- Sender details and junk verdict (filled by the junk engine on sync) ---
    /** Display name part of the From header. */
    val senderName: String = "",
    /** Bare email address part of the From header, lower-cased. */
    val senderEmail: String = "",
    val replyTo: String = "",
    /** 0–100; higher is more junk-like. */
    val spamScore: Int = 0,
    /** CLEAN, SUSPICIOUS or SPAM. */
    val spamLevel: String = "CLEAN",
    /** One "title — detail" line per signal that contributed to the verdict. */
    val spamReasons: String = "",
    /** Organisation the message claims to come from, if any. */
    val claimedBrand: String? = null,
    /** True when the claimed organisation does not own the sender's domain. */
    val brandMismatch: Boolean = false,
    val brandExplanation: String = "",
    /** e.g. "DMARC pass · SPF pass · DKIM pass". */
    val authSummary: String = "",
    /** True when Quillbox moved the message to Spam automatically. */
    val autoFiled: Boolean = false,
    /** "spam" or "ham" once the user has taught the classifier with this message. */
    val trained: String? = null
)
