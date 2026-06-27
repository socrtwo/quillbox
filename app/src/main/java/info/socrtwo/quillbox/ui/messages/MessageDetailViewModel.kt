package info.socrtwo.quillbox.ui.messages

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import info.socrtwo.quillbox.data.local.AppPreferences
import info.socrtwo.quillbox.data.local.entity.AttachmentEntity
import info.socrtwo.quillbox.data.local.entity.MessageEntity
import info.socrtwo.quillbox.data.repository.MailRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MessageDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val mailRepository: MailRepository,
    private val appPreferences: AppPreferences
) : ViewModel() {

    private val messageId: Long = savedStateHandle.get<Long>("messageId") ?: 0L

    val message: StateFlow<MessageEntity?> =
        mailRepository.observeMessage(messageId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val attachments: StateFlow<List<AttachmentEntity>> =
        mailRepository.observeAttachments(messageId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Set when the user taps "Show images" for this message only. */
    private val showImagesOnce = MutableStateFlow(false)

    /** Whether remote images should be loaded: sender is trusted, or user allowed once. */
    val imagesAllowed: StateFlow<Boolean> = combine(
        message,
        appPreferences.trustedImageSenders,
        showImagesOnce
    ) { msg, trusted, once ->
        once || (msg != null && trusted.contains(senderAddress(msg.fromAddress)))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        viewModelScope.launch { mailRepository.markRead(messageId, true) }
    }

    fun showImages() {
        showImagesOnce.value = true
    }

    fun alwaysShowImagesFromSender() {
        message.value?.let { appPreferences.trustImageSender(senderAddress(it.fromAddress)) }
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

    companion object {
        /** Extracts the bare email address from a "Name <addr@host>" style string. */
        fun senderAddress(from: String): String {
            val start = from.indexOf('<')
            val end = from.indexOf('>')
            val addr = if (start >= 0 && end > start) from.substring(start + 1, end) else from
            return addr.trim().lowercase()
        }
    }
}
