package info.socrtwo.quillbox.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import info.socrtwo.quillbox.data.local.entity.AccountEntity
import info.socrtwo.quillbox.data.model.MailProtocol
import info.socrtwo.quillbox.data.model.SecurityType
import info.socrtwo.quillbox.data.repository.AccountRepository
import info.socrtwo.quillbox.data.repository.MailRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountFormState(
    val displayName: String = "",
    val email: String = "",
    val incomingHost: String = "",
    val incomingPort: String = "993",
    val protocol: MailProtocol = MailProtocol.IMAP,
    val incomingSecurity: SecurityType = SecurityType.SSL_TLS,
    val smtpHost: String = "",
    val smtpPort: String = "587",
    val smtpSecurity: SecurityType = SecurityType.STARTTLS,
    val username: String = "",
    val password: String = "",
    val saving: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val mailRepository: MailRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AccountFormState())
    val state: StateFlow<AccountFormState> = _state.asStateFlow()

    fun onField(update: AccountFormState.() -> AccountFormState) = _state.update(update)

    fun onProtocolChange(protocol: MailProtocol) = _state.update {
        // Suggest sensible default ports when the protocol/security changes.
        val port = when (protocol) {
            MailProtocol.IMAP -> if (it.incomingSecurity == SecurityType.SSL_TLS) "993" else "143"
            MailProtocol.POP3 -> if (it.incomingSecurity == SecurityType.SSL_TLS) "995" else "110"
        }
        it.copy(protocol = protocol, incomingPort = port)
    }

    fun onIncomingSecurityChange(security: SecurityType) = _state.update {
        val port = when (it.protocol) {
            MailProtocol.IMAP -> if (security == SecurityType.SSL_TLS) "993" else "143"
            MailProtocol.POP3 -> if (security == SecurityType.SSL_TLS) "995" else "110"
        }
        it.copy(incomingSecurity = security, incomingPort = port)
    }

    /**
     * Validates and persists the account. [verify] additionally attempts a live
     * connection before saving. On success [onSaved] is invoked.
     */
    fun save(verify: Boolean, onSaved: () -> Unit) {
        val s = _state.value
        val validationError = validate(s)
        if (validationError != null) {
            _state.update { it.copy(error = validationError) }
            return
        }
        val account = s.toEntity()
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            if (verify) {
                val result = accountRepository.testConnection(account)
                if (result.isFailure) {
                    _state.update {
                        it.copy(saving = false, error = "Connection failed: ${result.exceptionOrNull()?.message}")
                    }
                    return@launch
                }
            }
            val id = accountRepository.saveAccount(account)
            mailRepository.ensureDefaultFolders(id)
            _state.update { it.copy(saving = false) }
            onSaved()
        }
    }

    private fun validate(s: AccountFormState): String? = when {
        s.email.isBlank() -> "Email is required"
        s.incomingHost.isBlank() -> "Incoming host is required"
        s.incomingPort.toIntOrNull() == null -> "Incoming port must be a number"
        s.smtpHost.isBlank() -> "SMTP host is required"
        s.smtpPort.toIntOrNull() == null -> "SMTP port must be a number"
        s.username.isBlank() -> "Username is required"
        else -> null
    }

    private fun AccountFormState.toEntity() = AccountEntity(
        displayName = displayName.ifBlank { email },
        email = email.trim(),
        incomingHost = incomingHost.trim(),
        incomingPort = incomingPort.toInt(),
        protocol = protocol,
        incomingSecurity = incomingSecurity,
        smtpHost = smtpHost.trim(),
        smtpPort = smtpPort.toInt(),
        smtpSecurity = smtpSecurity,
        username = username.trim(),
        password = password
    )
}
