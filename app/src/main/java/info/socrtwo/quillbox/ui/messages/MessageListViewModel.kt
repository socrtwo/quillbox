package info.socrtwo.quillbox.ui.messages

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import info.socrtwo.quillbox.data.local.entity.MessageEntity
import info.socrtwo.quillbox.data.repository.AccountRepository
import info.socrtwo.quillbox.data.repository.MailRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MessageListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val accountRepository: AccountRepository,
    private val mailRepository: MailRepository
) : ViewModel() {

    val folderId: Long = savedStateHandle.get<Long>("folderId") ?: 0L
    val folderName: String = savedStateHandle.get<String>("folderName") ?: "Folder"

    val messages: StateFlow<List<MessageEntity>> =
        mailRepository.observeMessages(folderId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val acc = accountRepository.getPrimaryAccount() ?: return@launch
            _isRefreshing.value = true
            mailRepository.syncMessages(acc)
            _isRefreshing.value = false
        }
    }
}
