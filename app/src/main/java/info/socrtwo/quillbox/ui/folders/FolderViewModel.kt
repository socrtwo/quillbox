package info.socrtwo.quillbox.ui.folders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import info.socrtwo.quillbox.data.local.entity.AccountEntity
import info.socrtwo.quillbox.data.local.entity.FolderEntity
import info.socrtwo.quillbox.data.repository.AccountRepository
import info.socrtwo.quillbox.data.repository.MailRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FolderUiState(
    val isRefreshing: Boolean = false,
    val message: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FolderViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val mailRepository: MailRepository
) : ViewModel() {

    /** All configured accounts (for the account switcher). */
    val accounts: StateFlow<List<AccountEntity>> = accountRepository.observeAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The account currently being viewed: the selected one, or the first if none chosen. */
    val currentAccount: StateFlow<AccountEntity?> = combine(
        accountRepository.observeAccounts(),
        accountRepository.selectedAccountId
    ) { list, selectedId ->
        list.firstOrNull { it.id == selectedId } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val folders: StateFlow<List<FolderEntity>> = currentAccount
        .flatMapLatest { acc ->
            if (acc == null) flowOf(emptyList()) else mailRepository.observeFolders(acc.id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _uiState = MutableStateFlow(FolderUiState())
    val uiState: StateFlow<FolderUiState> = _uiState.asStateFlow()

    fun selectAccount(id: Long) = accountRepository.selectAccount(id)

    /** Fetch new mail for the current account and route it through the rules engine. */
    fun refresh() {
        val acc = currentAccount.value ?: return
        viewModelScope.launch {
            _uiState.value = FolderUiState(isRefreshing = true)
            val result = mailRepository.syncMessages(acc)
            _uiState.value = FolderUiState(
                isRefreshing = false,
                message = result.fold(
                    onSuccess = { count -> if (count > 0) "$count new message(s)" else "No new mail" },
                    onFailure = { "Sync failed: ${it.message}" }
                )
            )
        }
    }

    fun consumeMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}
