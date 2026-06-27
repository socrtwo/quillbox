package info.socrtwo.quillbox.data.repository

import info.socrtwo.quillbox.data.local.dao.AccountDao
import info.socrtwo.quillbox.data.mail.MailClient
import info.socrtwo.quillbox.data.local.entity.AccountEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepository @Inject constructor(
    private val accountDao: AccountDao,
    private val mailClient: MailClient
) {
    fun observePrimaryAccount(): Flow<AccountEntity?> = accountDao.observePrimaryAccount()

    fun observeAccountCount(): Flow<Int> = accountDao.observeAccountCount()

    suspend fun getPrimaryAccount(): AccountEntity? = accountDao.getPrimaryAccount()

    suspend fun saveAccount(account: AccountEntity): Long = accountDao.insert(account)

    /** Tries to connect with the supplied account settings before saving them. */
    suspend fun testConnection(account: AccountEntity): Result<Unit> =
        mailClient.testConnection(account)
}
