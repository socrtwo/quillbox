package info.socrtwo.quillbox.ui.messages

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import info.socrtwo.quillbox.data.local.entity.MessageEntity
import info.socrtwo.quillbox.data.repository.MailRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MessageDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val mailRepository: MailRepository
) : ViewModel() {

    private val messageId: Long = savedStateHandle.get<Long>("messageId") ?: 0L

    val message: StateFlow<MessageEntity?> =
        mailRepository.observeMessage(messageId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // Mark as read once it is opened.
        viewModelScope.launch { mailRepository.markRead(messageId, true) }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            val acc = message.value?.accountId ?: return@launch
            mailRepository.deleteMessage(messageId, acc)
            onDone()
        }
    }

    fun moveToSpam(onDone: () -> Unit) {
        viewModelScope.launch {
            val acc = message.value?.accountId ?: return@launch
            mailRepository.moveMessage(messageId, acc, MailRepository.SPAM)
            onDone()
        }
    }
}
