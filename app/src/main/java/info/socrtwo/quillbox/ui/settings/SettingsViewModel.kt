package info.socrtwo.quillbox.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import info.socrtwo.quillbox.data.local.entity.AccountEntity
import info.socrtwo.quillbox.data.repository.AccountRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val accountRepository: AccountRepository
) : ViewModel() {

    val accounts: StateFlow<List<AccountEntity>> = accountRepository.observeAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val selectedAccountId: StateFlow<Long?> = accountRepository.selectedAccountId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun selectAccount(id: Long) = accountRepository.selectAccount(id)

    fun deleteAccount(account: AccountEntity) {
        viewModelScope.launch { accountRepository.deleteAccount(account) }
    }
}
