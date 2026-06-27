package info.socrtwo.quillbox.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Metadata for a downloaded attachment. The bytes live on the filesystem at [filePath]
 * (under the app's private files dir) so large attachments don't bloat the database.
 */
@Entity(
    tableName = "attachments",
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["messageId"])]
)
data class AttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val messageId: Long,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val filePath: String
)
