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

    private fun readSelectedAccountId(): Long? =
        prefs.getLong(KEY_SELECTED_ACCOUNT, -1L).takeIf { it >= 0 }

    fun setSelectedAccountId(id: Long) {
        prefs.edit().putLong(KEY_SELECTED_ACCOUNT, id).apply()
        _selectedAccountId.value = id
    }

    companion object {
        private const val KEY_SELECTED_ACCOUNT = "selected_account_id"
    }
}
