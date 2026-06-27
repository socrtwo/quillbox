package info.socrtwo.quillbox.data.rules

import info.socrtwo.quillbox.data.local.entity.RuleEntity
import info.socrtwo.quillbox.data.mail.FetchedMessage
import info.socrtwo.quillbox.data.model.CriteriaField
import info.socrtwo.quillbox.data.model.MatchLogic
import info.socrtwo.quillbox.data.model.RuleActionType
import javax.inject.Inject
import javax.inject.Singleton

/** The effect to apply to a message after rule evaluation. */
data class RuleOutcome(
    val matchedRuleName: String? = null,
    val targetFolder: String? = null,
    val markRead: Boolean = false,
    val delete: Boolean = false
) {
    val matched: Boolean get() = matchedRuleName != null
}

/**
 * Evaluates user-defined rules against incoming mail. The first enabled rule (in
 * priority order) whose criteria match determines the outcome.
 */
@Singleton
class RulesEngine @Inject constructor() {

    fun evaluate(message: FetchedMessage, rules: List<RuleEntity>): RuleOutcome {
        val sorted = rules.filter { it.enabled }.sortedWith(compareBy({ it.priority }, { it.id }))
        for (rule in sorted) {
            if (matches(rule, message)) {
                return when (rule.actionType) {
                    RuleActionType.MOVE_TO_FOLDER ->
                        RuleOutcome(matchedRuleName = rule.name, targetFolder = rule.targetFolder)
                    RuleActionType.MARK_READ ->
                        RuleOutcome(matchedRuleName = rule.name, markRead = true)
                    RuleActionType.DELETE ->
                        RuleOutcome(matchedRuleName = rule.name, delete = true)
                }
            }
        }
        return RuleOutcome()
    }

    private fun matches(rule: RuleEntity, message: FetchedMessage): Boolean {
        if (rule.criteria.isEmpty()) return false
        val results = rule.criteria.map { criterion ->
            val haystack = when (criterion.field) {
                CriteriaField.SENDER -> message.from
                CriteriaField.SUBJECT -> message.subject
                CriteriaField.BODY -> message.bodyText.ifBlank { message.bodyHtml.orEmpty() }
            }
            haystack.contains(criterion.value, ignoreCase = true)
        }
        return when (rule.logic) {
            MatchLogic.AND -> results.all { it }
            MatchLogic.OR -> results.any { it }
        }
    }
}
