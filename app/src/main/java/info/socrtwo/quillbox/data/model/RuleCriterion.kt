package info.socrtwo.quillbox.data.model

/**
 * A single criterion within a rule, e.g. "SENDER contains spam@". Multiple criteria
 * are combined per the owning rule's [MatchLogic].
 */
data class RuleCriterion(
    val field: CriteriaField,
    val value: String
)
