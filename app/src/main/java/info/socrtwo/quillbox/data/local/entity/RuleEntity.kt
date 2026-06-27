package info.socrtwo.quillbox.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import info.socrtwo.quillbox.data.model.MatchLogic
import info.socrtwo.quillbox.data.model.RuleActionType
import info.socrtwo.quillbox.data.model.RuleCriterion

/**
 * A user-defined filtering rule. Criteria are combined with [logic] and, when matched,
 * the [actionType] is applied to the message (optionally routing it to [targetFolder]).
 */
@Entity(tableName = "rules")
data class RuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val enabled: Boolean = true,
    val logic: MatchLogic = MatchLogic.OR,
    val criteria: List<RuleCriterion> = emptyList(),
    val actionType: RuleActionType = RuleActionType.MOVE_TO_FOLDER,
    /** Destination folder name for MOVE_TO_FOLDER actions (e.g. "Spam"). */
    val targetFolder: String? = null,
    /** Lower numbers evaluate first. */
    val priority: Int = 100
)
