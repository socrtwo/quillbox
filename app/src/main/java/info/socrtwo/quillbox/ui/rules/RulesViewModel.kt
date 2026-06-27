package info.socrtwo.quillbox.ui.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import info.socrtwo.quillbox.data.local.entity.RuleEntity
import info.socrtwo.quillbox.data.model.MatchLogic
import info.socrtwo.quillbox.data.model.RuleActionType
import info.socrtwo.quillbox.data.model.RuleCriterion
import info.socrtwo.quillbox.data.repository.RuleRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RulesViewModel @Inject constructor(
    private val ruleRepository: RuleRepository
) : ViewModel() {

    val rules: StateFlow<List<RuleEntity>> = ruleRepository.observeRules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addRule(
        name: String,
        logic: MatchLogic,
        criteria: List<RuleCriterion>,
        action: RuleActionType,
        targetFolder: String?
    ) {
        viewModelScope.launch {
            ruleRepository.saveRule(
                RuleEntity(
                    name = name.ifBlank { "Untitled rule" },
                    logic = logic,
                    criteria = criteria,
                    actionType = action,
                    targetFolder = if (action == RuleActionType.MOVE_TO_FOLDER) targetFolder else null,
                    priority = 100
                )
            )
        }
    }

    fun setEnabled(rule: RuleEntity, enabled: Boolean) {
        viewModelScope.launch { ruleRepository.updateRule(rule.copy(enabled = enabled)) }
    }

    fun delete(rule: RuleEntity) {
        viewModelScope.launch { ruleRepository.deleteRule(rule) }
    }
}
