package info.socrtwo.quillbox.ui.root

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import info.socrtwo.quillbox.data.repository.AccountRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class RootViewModel @Inject constructor(
    accountRepository: AccountRepository
) : ViewModel() {

    /** null = still loading, true/false = whether an account is configured. */
    val hasAccount = accountRepository.observeAccountCount()
        .map { it > 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
