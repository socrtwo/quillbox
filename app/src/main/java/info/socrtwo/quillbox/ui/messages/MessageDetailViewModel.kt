package info.socrtwo.quillbox.ui.messages

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import info.socrtwo.quillbox.data.local.AppPreferences
import info.socrtwo.quillbox.data.local.entity.AttachmentEntity
import info.socrtwo.quillbox.data.local.entity.MessageEntity
import info.socrtwo.quillbox.data.repository.MailRepository
import info.socrtwo.quillbox.data.repository.RuleRepository
import info.socrtwo.quillbox.data.security.LinkSafetyAnalyzer
import info.socrtwo.quillbox.data.security.SafetyReport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MessageDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val mailRepository: MailRepository,
    private val ruleRepository: RuleRepository,
    private val appPreferences: AppPreferences,
    private val analyzer: LinkSafetyAnalyzer
) : ViewModel() {

    private val messageId: Long = savedStateHandle.get<Long>("messageId") ?: 0L

    val message: StateFlow<MessageEntity?> =
        mailRepository.observeMessage(messageId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val attachments: StateFlow<List<AttachmentEntity>> =
        mailRepository.observeAttachments(messageId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val showImagesOnce = MutableStateFlow(false)

    val imagesAllowed: StateFlow<Boolean> = combine(
        message,
        appPreferences.trustedImageSenders,
        showImagesOnce
    ) { msg, trusted, once ->
        once || (msg != null && trusted.contains(senderAddress(msg.fromAddress)))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    // --- Link safety ---
    private val _report = MutableStateFlow<SafetyReport?>(null)
    val report: StateFlow<SafetyReport?> = _report.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _scanMessage = MutableStateFlow<String?>(null)
    val scanMessage: StateFlow<String?> = _scanMessage.asStateFlow()

    val virusTotalConfigured: StateFlow<Boolean> = appPreferences.virusTotalApiKey
        .map { !it.isNullOrBlank() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        viewModelScope.launch { mailRepository.markRead(messageId, true) }
        // Run the local (offline) domain-mismatch analysis once the message loads.
        viewModelScope.launch {
            message.collect { msg ->
                if (msg != null && _report.value == null) {
                    _report.value = analyzer.analyzeLocally(msg.fromAddress, msg.bodyText, msg.bodyHtml)
                }
            }
        }
    }

    /** Runs the message's links through VirusTotal (requires an API key set in Settings). */
    fun scanLinks() {
        val current = _report.value ?: return
        val key = appPreferences.virusTotalApiKey.value
        when {
            key.isNullOrBlank() -> _scanMessage.value = "Add a VirusTotal API key in Settings to scan links."
            current.links.isEmpty() -> _scanMessage.value = "This message has no links to scan."
            else -> viewModelScope.launch {
                _scanning.value = true
                _report.value = analyzer.scanWithVirusTotal(key, current)
                _scanning.value = false
            }
        }
    }

    fun consumeScanMessage() { _scanMessage.value = null }

    fun showImages() { showImagesOnce.value = true }

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

    /** Blacklists the sender (adds a Spam rule) and moves this message to Spam. */
    fun blacklistSender(onDone: () -> Unit) {
        viewModelScope.launch {
            val msg = message.value ?: return@launch
            ruleRepository.blacklistSender(senderAddress(msg.fromAddress))
            mailRepository.moveMessage(messageId, msg.accountId, MailRepository.SPAM)
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
