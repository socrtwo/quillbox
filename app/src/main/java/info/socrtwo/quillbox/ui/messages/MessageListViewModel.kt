package info.socrtwo.quillbox.ui.messages

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import info.socrtwo.quillbox.data.local.entity.MessageEntity
import info.socrtwo.quillbox.data.repository.MailRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MessageListUiState(
    val isRefreshing: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class MessageListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val mailRepository: MailRepository
) : ViewModel() {

    val folderId: Long = savedStateHandle.get<Long>("folderId") ?: 0L
    val folderName: String = savedStateHandle.get<String>("folderName") ?: "Folder"

    val messages: StateFlow<List<MessageEntity>> =
        mailRepository.observeMessages(folderId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _uiState = MutableStateFlow(MessageListUiState())
    val uiState: StateFlow<MessageListUiState> = _uiState.asStateFlow()

    /** Fetch new mail for the account that owns this folder. */
    fun refresh() {
        viewModelScope.launch {
            _uiState.value = MessageListUiState(isRefreshing = true)
            val result = mailRepository.syncFolder(folderId)
            _uiState.value = MessageListUiState(
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
