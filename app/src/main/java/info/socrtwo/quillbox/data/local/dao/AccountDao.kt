package info.socrtwo.quillbox.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import info.socrtwo.quillbox.data.local.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: AccountEntity): Long

    @Update
    suspend fun update(account: AccountEntity)

    @Query("SELECT * FROM accounts ORDER BY id LIMIT 1")
    fun observePrimaryAccount(): Flow<AccountEntity?>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: Long): AccountEntity?

    @Query("SELECT * FROM accounts ORDER BY id LIMIT 1")
    suspend fun getPrimaryAccount(): AccountEntity?

    @Query("SELECT COUNT(*) FROM accounts")
    fun observeAccountCount(): Flow<Int>
}
