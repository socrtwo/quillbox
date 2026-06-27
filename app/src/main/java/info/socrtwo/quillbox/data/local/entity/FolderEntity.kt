package info.socrtwo.quillbox.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A mailbox folder belonging to an account (e.g. Inbox, Spam, Sent). */
@Entity(
    tableName = "folders",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["accountId", "name"], unique = true)]
)
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val name: String,
    /** Remote folder path on the server, when applicable (IMAP). */
    val remotePath: String? = null
)
