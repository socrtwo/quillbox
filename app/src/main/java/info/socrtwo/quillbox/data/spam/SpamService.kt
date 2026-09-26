package info.socrtwo.quillbox.data.spam

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import info.socrtwo.quillbox.data.local.AppPreferences
import info.socrtwo.quillbox.data.local.entity.MessageEntity
import info.socrtwo.quillbox.data.local.entity.RuleEntity
import info.socrtwo.quillbox.data.mail.FetchedMessage
import info.socrtwo.quillbox.data.model.CriteriaField
import info.socrtwo.quillbox.data.model.MatchLogic
import info.socrtwo.quillbox.data.model.RuleActionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android front for the shared junk engine (the same sources as the web client): DNS
 * blocklists, sender authentication, brand-impersonation detection, a Bayesian classifier
 * that learns from Junk / Not junk, and content heuristics. Nothing leaves the device except
 * plain DNS queries.
 */
@Singleton
class SpamService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: AppPreferences
) {
    private val bayesFile = File(context.filesDir, "bayes.tsv")
    val bayes: BayesClassifier = BayesClassifier().also { b ->
        if (bayesFile.exists()) runCatching { b.load(bayesFile.readText()) }
        if (b.trainedSpam == 0 && b.trainedHam == 0) b.seed()
    }
    private val engine = SpamEngine(BlacklistChecker(), bayes)

    val enabled: Boolean get() = prefs.spamEnabled.value
    val autoMove: Boolean get() = prefs.autoMoveSpam.value

    private fun config(): SpamConfig = SpamConfig(
        enabled = enabled,
        autoMoveToJunk = autoMove,
        safeSenders = prefs.safeSenders.value.toList(),
        blockedSenders = prefs.blockedSenders.value.toList()
    )

    fun facts(msg: FetchedMessage, userAddresses: List<String>): MessageFacts = MessageFacts(
        messageId = msg.messageId,
        fromName = msg.fromName,
        fromAddress = msg.fromAddress,
        replyTo = msg.replyTo,
        toAddresses = msg.to.split(',').map { it.trim().lowercase() }.filter { it.isNotBlank() },
        subject = msg.subject,
        bodyText = msg.bodyText,
        bodyHtml = msg.bodyHtml,
        receivedHeaders = msg.receivedHeaders,
        authenticationResults = msg.authenticationResults,
        originatingIpHeader = msg.originatingIp,
        listUnsubscribe = msg.listUnsubscribe,
        precedence = msg.precedence,
        attachmentNames = msg.attachments.map { it.fileName },
        date = msg.sentDate,
        userAddresses = userAddresses
    )

    /** Facts rebuilt from a stored message (headers are not persisted, which is fine for training and rule proposals). */
    fun facts(m: MessageEntity, userAddresses: List<String>): MessageFacts = MessageFacts(
        messageId = m.messageId,
        fromName = m.senderName,
        fromAddress = m.senderEmail.ifBlank { HeaderParser.splitAddress(m.fromAddress).second },
        replyTo = m.replyTo,
        toAddresses = m.toAddresses.split(',').map { it.trim().lowercase() }.filter { it.isNotBlank() },
        subject = m.subject,
        bodyText = m.bodyText,
        bodyHtml = m.bodyHtml,
        date = m.sentDate,
        userAddresses = userAddresses
    )

    /** Runs the full analysis (including DNS lookups) off the main thread. */
    suspend fun analyze(facts: MessageFacts): SpamVerdict = withContext(Dispatchers.IO) {
        engine.analyze(facts, config(), emptyList())
    }

    fun train(facts: MessageFacts, spam: Boolean, previouslyTrained: String?) {
        val tokens = BayesClassifier.tokenize(facts)
        when (previouslyTrained) {
            "spam" -> if (!spam) bayes.untrain(tokens, spam = true)
            "ham" -> if (spam) bayes.untrain(tokens, spam = false)
        }
        if (previouslyTrained != (if (spam) "spam" else "ham")) bayes.train(tokens, spam)
        runCatching { bayesFile.writeText(bayes.serialize()) }
    }

    fun propose(facts: MessageFacts, verdict: SpamVerdict, targetFolder: String): RuleProposal =
        RuleProposer.propose(facts, verdict, bayes, targetFolder)

    /** Converts an engine proposal into the app's rule model (criteria are "contains" matches). */
    fun toRuleEntity(proposal: RuleProposal): RuleEntity = RuleEntity(
        name = proposal.rule.name,
        logic = if (proposal.rule.logic == RuleLogic.AND) MatchLogic.AND else MatchLogic.OR,
        criteria = proposal.rule.criteria.map { c ->
            val field = when (c.field) {
                RuleField.SUBJECT -> CriteriaField.SUBJECT
                RuleField.BODY -> CriteriaField.BODY
                else -> CriteriaField.SENDER
            }
            info.socrtwo.quillbox.data.model.RuleCriterion(field, c.value)
        },
        actionType = RuleActionType.MOVE_TO_FOLDER,
        targetFolder = proposal.rule.targetFolder ?: "Spam",
        priority = 50
    )

    companion object {
        /** Copies a verdict into the stored message so screens can show it without re-analysing. */
        fun apply(entity: MessageEntity, v: SpamVerdict, autoFiled: Boolean): MessageEntity = entity.copy(
            spamScore = v.score,
            spamLevel = v.level.name,
            spamReasons = v.reasons.joinToString("\n") { r -> (if (r.weight > 0) "+" else "") + r.weight + " " + r.title + (if (r.detail.isNotBlank()) " — " + r.detail else "") },
            claimedBrand = v.brand.claimedBrand,
            brandMismatch = v.brand.mismatch,
            brandExplanation = v.brand.explanation,
            authSummary = listOfNotNull(
                v.auth.dmarc?.let { "DMARC $it" }, v.auth.spf?.let { "SPF $it" }, v.auth.dkim?.let { "DKIM $it" }
            ).joinToString(" · "),
            autoFiled = autoFiled
        )
    }
}
