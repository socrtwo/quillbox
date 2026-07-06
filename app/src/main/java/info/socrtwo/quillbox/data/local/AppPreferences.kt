package info.socrtwo.quillbox.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Small key/value store for app-level UI state. Currently tracks which account is
 * selected so the mailbox screens know whose folders/mail to show across app restarts.
 */
@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("quillbox_prefs", Context.MODE_PRIVATE)

    private val _selectedAccountId = MutableStateFlow(readSelectedAccountId())
    val selectedAccountId: StateFlow<Long?> = _selectedAccountId.asStateFlow()

    /** Sender addresses (lower-cased) the user has approved to load remote images from. */
    private val _trustedImageSenders = MutableStateFlow(readTrustedSenders())
    val trustedImageSenders: StateFlow<Set<String>> = _trustedImageSenders.asStateFlow()

    /** Optional VirusTotal API key used to scan links in messages. */
    private val _virusTotalApiKey = MutableStateFlow(readVirusTotalApiKey())
    val virusTotalApiKey: StateFlow<String?> = _virusTotalApiKey.asStateFlow()

    private fun readSelectedAccountId(): Long? =
        prefs.getLong(KEY_SELECTED_ACCOUNT, -1L).takeIf { it >= 0 }

    fun setSelectedAccountId(id: Long) {
        prefs.edit().putLong(KEY_SELECTED_ACCOUNT, id).apply()
        _selectedAccountId.value = id
    }

    private fun readTrustedSenders(): Set<String> =
        prefs.getStringSet(KEY_TRUSTED_SENDERS, emptySet())?.toSet() ?: emptySet()

    fun trustImageSender(sender: String) {
        val updated = readTrustedSenders() + sender.lowercase().trim()
        prefs.edit().putStringSet(KEY_TRUSTED_SENDERS, updated).apply()
        _trustedImageSenders.value = updated
    }

    private fun readVirusTotalApiKey(): String? =
        prefs.getString(KEY_VT_API_KEY, null)?.takeIf { it.isNotBlank() }

    fun setVirusTotalApiKey(key: String) {
        val trimmed = key.trim()
        prefs.edit().putString(KEY_VT_API_KEY, trimmed).apply()
        _virusTotalApiKey.value = trimmed.takeIf { it.isNotBlank() }
    }

    companion object {
        private const val KEY_SELECTED_ACCOUNT = "selected_account_id"
        private const val KEY_TRUSTED_SENDERS = "trusted_image_senders"
        private const val KEY_VT_API_KEY = "virustotal_api_key"
    }
}
