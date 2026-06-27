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
    val hasAttachments: Boolean = false
)
