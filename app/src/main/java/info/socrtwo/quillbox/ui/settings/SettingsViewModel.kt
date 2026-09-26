package info.socrtwo.quillbox.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import info.socrtwo.quillbox.data.local.AppPreferences
import info.socrtwo.quillbox.data.local.entity.AccountEntity
import info.socrtwo.quillbox.data.repository.AccountRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val appPreferences: AppPreferences
) : ViewModel() {

    val accounts: StateFlow<List<AccountEntity>> = accountRepository.observeAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val selectedAccountId: StateFlow<Long?> = accountRepository.selectedAccountId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Junk protection switches. */
    val spamEnabled: StateFlow<Boolean> = appPreferences.spamEnabled
    val autoMoveSpam: StateFlow<Boolean> = appPreferences.autoMoveSpam
    val safeSenders: StateFlow<Set<String>> = appPreferences.safeSenders
    val blockedSenders: StateFlow<Set<String>> = appPreferences.blockedSenders

    fun setSpamEnabled(enabled: Boolean) = appPreferences.setSpamEnabled(enabled)
    fun setAutoMoveSpam(enabled: Boolean) = appPreferences.setAutoMoveSpam(enabled)

    fun selectAccount(id: Long) = accountRepository.selectAccount(id)

    fun deleteAccount(account: AccountEntity) {
        viewModelScope.launch { accountRepository.deleteAccount(account) }
    }
}
