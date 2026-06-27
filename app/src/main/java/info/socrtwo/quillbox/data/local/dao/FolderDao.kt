package info.socrtwo.quillbox.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import info.socrtwo.quillbox.data.local.entity.FolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(folder: FolderEntity): Long

    @Query("SELECT * FROM folders WHERE accountId = :accountId ORDER BY name")
    fun observeFolders(accountId: Long): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE accountId = :accountId AND name = :name LIMIT 1")
    suspend fun getByName(accountId: Long, name: String): FolderEntity?

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getById(id: Long): FolderEntity?

    @Query("SELECT COUNT(*) FROM folders WHERE accountId = :accountId")
    suspend fun countForAccount(accountId: Long): Int
}
