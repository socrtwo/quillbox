package info.socrtwo.quillbox.data.repository

import info.socrtwo.quillbox.data.local.dao.RuleDao
import info.socrtwo.quillbox.data.local.entity.RuleEntity
import info.socrtwo.quillbox.data.model.CriteriaField
import info.socrtwo.quillbox.data.model.MatchLogic
import info.socrtwo.quillbox.data.model.RuleActionType
import info.socrtwo.quillbox.data.model.RuleCriterion
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RuleRepository @Inject constructor(
    private val ruleDao: RuleDao
) {
    fun observeRules(): Flow<List<RuleEntity>> = ruleDao.observeRules()

    suspend fun getEnabledRules(): List<RuleEntity> = ruleDao.getEnabledRules()

    suspend fun saveRule(rule: RuleEntity): Long = ruleDao.insert(rule)

    suspend fun updateRule(rule: RuleEntity) = ruleDao.update(rule)

    suspend fun deleteRule(rule: RuleEntity) = ruleDao.delete(rule)

    /** Adds a high-priority rule routing future mail from [senderAddress] into Spam. */
    suspend fun blacklistSender(senderAddress: String): Long = ruleDao.insert(
        RuleEntity(
            name = "Blacklist: $senderAddress",
            enabled = true,
            logic = MatchLogic.OR,
            criteria = listOf(RuleCriterion(CriteriaField.SENDER, senderAddress)),
            actionType = RuleActionType.MOVE_TO_FOLDER,
            targetFolder = "Spam",
            priority = 5
        )
    )
}
