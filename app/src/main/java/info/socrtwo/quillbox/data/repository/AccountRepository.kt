package info.socrtwo.quillbox.data.repository

import info.socrtwo.quillbox.data.local.AppPreferences
import info.socrtwo.quillbox.data.local.dao.AccountDao
import info.socrtwo.quillbox.data.mail.MailClient
import info.socrtwo.quillbox.data.local.entity.AccountEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepository @Inject constructor(
    private val accountDao: AccountDao,
    private val mailClient: MailClient,
    private val appPreferences: AppPreferences
) {
    fun observeAccounts(): Flow<List<AccountEntity>> = accountDao.observeAll()

    fun observePrimaryAccount(): Flow<AccountEntity?> = accountDao.observePrimaryAccount()

    fun observeAccountCount(): Flow<Int> = accountDao.observeAccountCount()

    /** Flow of the currently-selected account id (persisted across restarts). */
    val selectedAccountId = appPreferences.selectedAccountId

    fun selectAccount(id: Long) = appPreferences.setSelectedAccountId(id)

    suspend fun getPrimaryAccount(): AccountEntity? = accountDao.getPrimaryAccount()

    suspend fun getAccount(id: Long): AccountEntity? = accountDao.getById(id)

    /** The selected account, falling back to the first account if none is selected. */
    suspend fun getSelectedAccount(): AccountEntity? =
        appPreferences.selectedAccountId.value?.let { accountDao.getById(it) }
            ?: accountDao.getPrimaryAccount()

    suspend fun saveAccount(account: AccountEntity): Long = accountDao.insert(account)

    suspend fun deleteAccount(account: AccountEntity) = accountDao.delete(account)

    /** Tries to connect with the supplied account settings before saving them. */
    suspend fun testConnection(account: AccountEntity): Result<Unit> =
        mailClient.testConnection(account)
}
