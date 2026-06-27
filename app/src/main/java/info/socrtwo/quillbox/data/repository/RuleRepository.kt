package info.socrtwo.quillbox.data.repository

import info.socrtwo.quillbox.data.local.dao.RuleDao
import info.socrtwo.quillbox.data.local.entity.RuleEntity
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
}
