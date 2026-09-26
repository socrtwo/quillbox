package info.socrtwo.quillbox.ui.messages

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import info.socrtwo.quillbox.data.local.AppPreferences
import info.socrtwo.quillbox.data.local.entity.AttachmentEntity
import info.socrtwo.quillbox.data.local.entity.MessageEntity
import info.socrtwo.quillbox.data.local.entity.RuleEntity
import info.socrtwo.quillbox.data.model.RuleCriterion
import info.socrtwo.quillbox.data.repository.MailRepository
import info.socrtwo.quillbox.data.repository.RuleRepository
import info.socrtwo.quillbox.data.spam.RuleProposal
import info.socrtwo.quillbox.data.spam.SpamService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** State of the "Analyse & make rule" dialog. */
data class ProposalUiState(
    val loading: Boolean = false,
    val proposal: RuleProposal? = null,
    val draft: RuleEntity? = null,
    val error: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MessageDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val mailRepository: MailRepository,
    private val ruleRepository: RuleRepository,
    private val spamService: SpamService,
    private val appPreferences: AppPreferences
) : ViewModel() {

    private val messageId: Long = savedStateHandle.get<Long>("messageId") ?: 0L

    val message: StateFlow<MessageEntity?> =
        mailRepository.observeMessage(messageId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val attachments: StateFlow<List<AttachmentEntity>> =
        mailRepository.observeAttachments(messageId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** True while the message sits in the Spam folder (drives Junk vs. Not junk). */
    val inSpamFolder: StateFlow<Boolean> = message
        .mapLatest { m -> m != null && mailRepository.folderName(m.folderId) == MailRepository.SPAM }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

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

    private val _proposal = MutableStateFlow(ProposalUiState())
    val proposal: StateFlow<ProposalUiState> = _proposal.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

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

    /** Junk: learn from this message and move it to Spam. */
    fun moveToSpam(onDone: () -> Unit, blockSender: Boolean = false) {
        viewModelScope.launch {
            mailRepository.markJunk(messageId, junk = true, blockSender = blockSender)
            onDone()
        }
    }

    /** Not junk: learn from this message and move it back to the Inbox. */
    fun notJunk(onDone: () -> Unit, trustSender: Boolean = false) {
        viewModelScope.launch {
            mailRepository.markJunk(messageId, junk = false, trustSender = trustSender)
            onDone()
        }
    }

    /** Runs the analyser and prepares an editable rule proposal. */
    fun analyse() {
        _proposal.value = ProposalUiState(loading = true)
        viewModelScope.launch {
            runCatching { mailRepository.proposeRule(messageId) }
                .onSuccess { p ->
                    _proposal.value = if (p == null) ProposalUiState(error = "Message not found")
                    else ProposalUiState(proposal = p, draft = spamService.toRuleEntity(p))
                }
                .onFailure { _proposal.value = ProposalUiState(error = it.message ?: "Analysis failed") }
        }
    }

    fun dismissProposal() {
        _proposal.value = ProposalUiState()
    }

    /** Saves the edited rule; optionally files matching Inbox mail now and this message too. */
    fun saveProposedRule(
        name: String,
        criteria: List<RuleCriterion>,
        applyToInbox: Boolean,
        alsoMoveThis: Boolean,
        onDone: () -> Unit
    ) {
        val draft = _proposal.value.draft ?: return
        val valid = draft.copy(name = name.ifBlank { draft.name }, criteria = criteria.filter { it.value.isNotBlank() })
        if (valid.criteria.isEmpty()) { _notice.value = "Add at least one condition."; return }
        viewModelScope.launch {
            ruleRepository.saveRule(valid)
            var moved = 0
            val acc = message.value?.accountId
            if (applyToInbox && acc != null) moved = mailRepository.applyRuleToInbox(valid, acc)
            if (alsoMoveThis && !inSpamFolder.value) mailRepository.markJunk(messageId, junk = true)
            _proposal.value = ProposalUiState()
            _notice.value = "Rule \"${valid.name}\" saved" + (if (applyToInbox) " · moved $moved message(s)" else "")
            if (alsoMoveThis) onDone()
        }
    }

    fun consumeNotice() {
        _notice.value = null
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
