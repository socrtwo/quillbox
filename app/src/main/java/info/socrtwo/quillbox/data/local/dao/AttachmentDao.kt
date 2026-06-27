package info.socrtwo.quillbox.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import info.socrtwo.quillbox.data.local.entity.AttachmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AttachmentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(attachment: AttachmentEntity): Long

    @Query("SELECT * FROM attachments WHERE messageId = :messageId ORDER BY id")
    fun observeByMessage(messageId: Long): Flow<List<AttachmentEntity>>
}
