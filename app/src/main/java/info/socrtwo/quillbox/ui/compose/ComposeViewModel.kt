package info.socrtwo.quillbox.ui.compose

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import info.socrtwo.quillbox.data.mail.OutgoingAttachment
import info.socrtwo.quillbox.data.repository.AccountRepository
import info.socrtwo.quillbox.data.repository.MailRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ComposeUiState(
    val to: String = "",
    val cc: String = "",
    val bcc: String = "",
    val subject: String = "",
    val showCcBcc: Boolean = false,
    val attachments: List<OutgoingAttachment> = emptyList(),
    val sending: Boolean = false,
    val error: String? = null,
    val sent: Boolean = false
)

@HiltViewModel
class ComposeViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val mailRepository: MailRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ComposeUiState())
    val state: StateFlow<ComposeUiState> = _state.asStateFlow()

    fun update(transform: ComposeUiState.() -> ComposeUiState) = _state.update(transform)

    fun toggleCcBcc() = _state.update { it.copy(showCcBcc = !it.showCcBcc) }

    fun addAttachment(attachment: OutgoingAttachment) = _state.update {
        it.copy(attachments = it.attachments + attachment)
    }

    fun removeAttachment(attachment: OutgoingAttachment) = _state.update {
        it.copy(attachments = it.attachments - attachment)
    }

    /** Sends the message. [htmlBody]/[plainBody] are produced from the rich-text editor. */
    fun send(htmlBody: String, plainBody: String) {
        val s = _state.value
        val recipients = splitAddresses(s.to)
        if (recipients.isEmpty()) {
            _state.update { it.copy(error = "At least one recipient is required") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(sending = true, error = null) }
            val account = accountRepository.getPrimaryAccount()
            if (account == null) {
                _state.update { it.copy(sending = false, error = "No account configured") }
                return@launch
            }
            val result = mailRepository.sendMessage(
                account = account,
                to = recipients,
                cc = splitAddresses(s.cc),
                bcc = splitAddresses(s.bcc),
                subject = s.subject,
                htmlBody = htmlBody,
                plainBody = plainBody,
                attachments = s.attachments
            )
            _state.update { st ->
                result.fold(
                    onSuccess = { st.copy(sending = false, sent = true) },
                    onFailure = { e -> st.copy(sending = false, error = "Send failed: ${e.message}") }
                )
            }
        }
    }

    private fun splitAddresses(raw: String): List<String> =
        raw.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }
}
