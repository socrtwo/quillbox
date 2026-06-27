package info.socrtwo.quillbox.data.local

import androidx.room.TypeConverter
import info.socrtwo.quillbox.data.model.CriteriaField
import info.socrtwo.quillbox.data.model.MailProtocol
import info.socrtwo.quillbox.data.model.MatchLogic
import info.socrtwo.quillbox.data.model.RuleActionType
import info.socrtwo.quillbox.data.model.RuleCriterion
import info.socrtwo.quillbox.data.model.SecurityType
import org.json.JSONArray
import org.json.JSONObject

/** Room type converters for enums and the rule-criteria list. */
class Converters {

    @TypeConverter
    fun protocolToString(value: MailProtocol): String = value.name

    @TypeConverter
    fun stringToProtocol(value: String): MailProtocol = MailProtocol.valueOf(value)

    @TypeConverter
    fun securityToString(value: SecurityType): String = value.name

    @TypeConverter
    fun stringToSecurity(value: String): SecurityType = SecurityType.valueOf(value)

    @TypeConverter
    fun logicToString(value: MatchLogic): String = value.name

    @TypeConverter
    fun stringToLogic(value: String): MatchLogic = MatchLogic.valueOf(value)

    @TypeConverter
    fun actionToString(value: RuleActionType): String = value.name

    @TypeConverter
    fun stringToAction(value: String): RuleActionType = RuleActionType.valueOf(value)

    @TypeConverter
    fun criteriaToJson(criteria: List<RuleCriterion>): String {
        val array = JSONArray()
        criteria.forEach { c ->
            array.put(
                JSONObject()
                    .put("field", c.field.name)
                    .put("value", c.value)
            )
        }
        return array.toString()
    }

    @TypeConverter
    fun jsonToCriteria(json: String): List<RuleCriterion> {
        if (json.isBlank()) return emptyList()
        val array = JSONArray(json)
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                add(
                    RuleCriterion(
                        field = CriteriaField.valueOf(obj.getString("field")),
                        value = obj.getString("value")
                    )
                )
            }
        }
    }
}
