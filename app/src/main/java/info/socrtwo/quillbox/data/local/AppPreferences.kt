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

    // --- Junk protection settings -------------------------------------------------------

    private val _spamEnabled = MutableStateFlow(prefs.getBoolean(KEY_SPAM_ENABLED, true))
    /** Analyse incoming mail with the junk engine. */
    val spamEnabled: StateFlow<Boolean> = _spamEnabled.asStateFlow()

    private val _autoMoveSpam = MutableStateFlow(prefs.getBoolean(KEY_AUTO_MOVE_SPAM, true))
    /** Move messages classified as junk to the Spam folder automatically. */
    val autoMoveSpam: StateFlow<Boolean> = _autoMoveSpam.asStateFlow()

    private val _safeSenders = MutableStateFlow(readSet(KEY_SAFE_SENDERS))
    val safeSenders: StateFlow<Set<String>> = _safeSenders.asStateFlow()

    private val _blockedSenders = MutableStateFlow(readSet(KEY_BLOCKED_SENDERS))
    val blockedSenders: StateFlow<Set<String>> = _blockedSenders.asStateFlow()

    fun setSpamEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SPAM_ENABLED, enabled).apply()
        _spamEnabled.value = enabled
    }

    fun setAutoMoveSpam(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_MOVE_SPAM, enabled).apply()
        _autoMoveSpam.value = enabled
    }

    fun addSafeSender(sender: String) {
        val s = sender.lowercase().trim()
        val safe = readSet(KEY_SAFE_SENDERS) + s
        val blocked = readSet(KEY_BLOCKED_SENDERS) - s
        prefs.edit().putStringSet(KEY_SAFE_SENDERS, safe).putStringSet(KEY_BLOCKED_SENDERS, blocked).apply()
        _safeSenders.value = safe; _blockedSenders.value = blocked
    }

    fun addBlockedSender(sender: String) {
        val s = sender.lowercase().trim()
        val blocked = readSet(KEY_BLOCKED_SENDERS) + s
        val safe = readSet(KEY_SAFE_SENDERS) - s
        prefs.edit().putStringSet(KEY_SAFE_SENDERS, safe).putStringSet(KEY_BLOCKED_SENDERS, blocked).apply()
        _safeSenders.value = safe; _blockedSenders.value = blocked
    }

    private fun readSet(key: String): Set<String> = prefs.getStringSet(key, emptySet())?.toSet() ?: emptySet()

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

    companion object {
        private const val KEY_SELECTED_ACCOUNT = "selected_account_id"
        private const val KEY_TRUSTED_SENDERS = "trusted_image_senders"
        private const val KEY_SPAM_ENABLED = "spam_enabled"
        private const val KEY_AUTO_MOVE_SPAM = "spam_auto_move"
        private const val KEY_SAFE_SENDERS = "spam_safe_senders"
        private const val KEY_BLOCKED_SENDERS = "spam_blocked_senders"
    }
}
