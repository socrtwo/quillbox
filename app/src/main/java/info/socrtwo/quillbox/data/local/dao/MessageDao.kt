package info.socrtwo.quillbox.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import info.socrtwo.quillbox.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: MessageEntity): Long

    @Query("SELECT * FROM messages WHERE folderId = :folderId ORDER BY sentDate DESC")
    fun observeByFolder(folderId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE id = :id")
    fun observeById(id: Long): Flow<MessageEntity?>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getById(id: Long): MessageEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM messages WHERE accountId = :accountId AND messageId = :messageId)")
    suspend fun exists(accountId: Long, messageId: String): Boolean

    @Query("UPDATE messages SET isRead = :read WHERE id = :id")
    suspend fun setRead(id: Long, read: Boolean)

    @Query("UPDATE messages SET folderId = :folderId WHERE id = :id")
    suspend fun moveToFolder(id: Long, folderId: Long)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM messages WHERE folderId = :folderId AND isRead = 0")
    fun observeUnreadCount(folderId: Long): Flow<Int>
}
