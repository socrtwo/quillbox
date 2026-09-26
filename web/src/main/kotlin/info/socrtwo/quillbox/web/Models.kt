package info.socrtwo.quillbox.web

import kotlinx.serialization.Serializable

// ---------------------------------------------------------------------------------------
// Account / session
// ---------------------------------------------------------------------------------------

@Serializable
data class AccountDto(
    val displayName: String = "",
    val email: String,
    val incomingHost: String,
    val incomingPort: Int,
    val protocol: String,          // "IMAP" or "POP3"
    val incomingSecurity: String,  // "SSL_TLS", "STARTTLS", or "NONE"
    val smtpHost: String,
    val smtpPort: Int,
    val smtpSecurity: String,
    val username: String,
    val password: String
)

@Serializable
data class SessionRequest(val account: AccountDto)

@Serializable
data class Capabilities(
    val folders: Boolean,
    val move: Boolean,
    val flags: Boolean,
    val search: Boolean,
    val drafts: Boolean
)

@Serializable
data class SessionResponse(
    val token: String,
    val email: String,
    val displayName: String,
    val protocol: String,
    val capabilities: Capabilities,
    val settings: AccountSettingsDto
)

@Serializable
data class VerifyResponse(val ok: Boolean, val incoming: String, val smtp: String)

// ---------------------------------------------------------------------------------------
// Legacy endpoints (kept byte-for-byte compatible for the iOS client)
// ---------------------------------------------------------------------------------------

@Serializable
data class MessageDto(
    val from: String,
    val to: String,
    val subject: String,
    val bodyText: String,
    val bodyHtml: String? = null,
    val sentDate: Long,
    val hasAttachments: Boolean
)

@Serializable
data class InboxRequest(val account: AccountDto, val limit: Int = 100)

@Serializable
data class OutgoingAttachmentDto(val fileName: String, val mimeType: String, val base64: String)

@Serializable
data class SendRequest(
    val account: AccountDto? = null,
    val to: List<String>,
    val cc: List<String> = emptyList(),
    val bcc: List<String> = emptyList(),
    val subject: String,
    val body: String,
    val html: String? = null,
    val attachments: List<OutgoingAttachmentDto> = emptyList(),
    val inReplyTo: String? = null,
    val references: String? = null,
    /** When replying, the original message so it can be flagged \Answered. */
    val replyTo: MessageRef? = null,
    /** Draft UID to delete after a successful send. */
    val draftUid: Long? = null
)

@Serializable
data class ApiError(val error: String)

@Serializable
data class ApiStatus(val status: String)

// ---------------------------------------------------------------------------------------
// Mailbox
// ---------------------------------------------------------------------------------------

@Serializable
data class FolderDto(
    val path: String,
    val name: String,
    /** inbox, junk, sent, drafts, trash, archive, other */
    val role: String,
    val delimiter: String,
    val unread: Int,
    val total: Int,
    val depth: Int
)

@Serializable
data class MessageRef(val folder: String, val uid: Long)

@Serializable
data class SpamReasonDto(val code: String, val title: String, val detail: String, val weight: Int)

@Serializable
data class BlacklistHitDto(val list: String, val zone: String, val subject: String, val kind: String, val response: String, val note: String)

@Serializable
data class BlacklistStatusDto(val list: String, val zone: String, val queried: Int, val hits: Int, val refused: Boolean, val error: String? = null)

@Serializable
data class AuthDto(val spf: String? = null, val dkim: String? = null, val dmarc: String? = null, val dkimDomain: String? = null)

@Serializable
data class BrandDto(
    val claimedBrand: String? = null,
    val claimSource: String? = null,
    val claimedText: String? = null,
    val legitimateDomains: List<String> = emptyList(),
    val senderDomain: String = "",
    val mismatch: Boolean = false,
    val lookalike: Boolean = false,
    val freemailSender: Boolean = false,
    val knownBrand: Boolean = false,
    val verified: Boolean = false,
    val explanation: String = ""
)

@Serializable
data class VerdictDto(
    val score: Int,
    /** CLEAN, SUSPICIOUS or SPAM */
    val level: String,
    val reasons: List<SpamReasonDto>,
    val brand: BrandDto,
    val auth: AuthDto,
    val blacklistHits: List<BlacklistHitDto> = emptyList(),
    val blacklistStatus: List<BlacklistStatusDto> = emptyList(),
    val bayesProbability: Double? = null,
    val linkDomains: List<String> = emptyList(),
    val originatingIps: List<String> = emptyList(),
    val senderDomain: String = "",
    val matchedRule: String? = null,
    val safeSender: Boolean = false,
    val blockedSender: Boolean = false,
    val analyzedAt: Long = 0,
    /** Set when Quillbox itself moved the message out of the Inbox. */
    val autoMoved: Boolean = false,
    val autoMovedTo: String? = null,
    /** "spam" or "ham" when the user has trained the classifier with this message. */
    val trained: String? = null
)

@Serializable
data class MessageSummaryDto(
    val uid: Long,
    val folder: String,
    val messageId: String,
    val fromName: String,
    val fromAddress: String,
    val to: List<String>,
    val subject: String,
    val preview: String,
    val date: Long,
    val read: Boolean,
    val flagged: Boolean,
    val answered: Boolean,
    val hasAttachments: Boolean,
    val size: Long,
    val verdict: VerdictDto? = null
)

@Serializable
data class AttachmentDto(
    val index: Int,
    val fileName: String,
    val mimeType: String,
    val size: Long,
    val inline: Boolean,
    val contentId: String? = null
)

@Serializable
data class MessageDetailDto(
    val uid: Long,
    val folder: String,
    val messageId: String,
    val fromName: String,
    val fromAddress: String,
    val to: List<String>,
    val cc: List<String>,
    val replyTo: String,
    val subject: String,
    val date: Long,
    val read: Boolean,
    val flagged: Boolean,
    val answered: Boolean,
    val bodyText: String,
    val bodyHtml: String? = null,
    val attachments: List<AttachmentDto>,
    val inReplyTo: String? = null,
    val references: String? = null,
    val listUnsubscribe: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val verdict: VerdictDto? = null
)

@Serializable
data class MessageListResponse(
    val folder: String,
    val total: Int,
    val unread: Int,
    val messages: List<MessageSummaryDto>,
    val autoMoved: List<MessageSummaryDto> = emptyList(),
    /** Messages in this page still waiting for background analysis. */
    val pending: Int = 0,
    /** Monotonic counter bumped whenever analysis or a mutation changes the account. */
    val version: Long = 0
)

@Serializable
data class MoveRequest(val items: List<MessageRef>, val to: String)

@Serializable
data class FlagRequest(val items: List<MessageRef>, val read: Boolean? = null, val flagged: Boolean? = null)

@Serializable
data class DeleteRequest(val items: List<MessageRef>, val permanent: Boolean = false)

@Serializable
data class JunkRequest(
    val items: List<MessageRef>,
    /** true = move to Junk and learn as spam, false = restore to Inbox and learn as legitimate */
    val junk: Boolean,
    val blockSender: Boolean = false,
    val safeSender: Boolean = false
)

@Serializable
data class BatchResult(
    val ok: Int,
    val failed: Int,
    val errors: List<String> = emptyList(),
    /** Where the affected messages now live (new UIDs after a move), so the client can undo. */
    val moved: List<MessageRef> = emptyList()
)

@Serializable
data class SearchResponse(val folder: String, val query: String, val messages: List<MessageSummaryDto>)

@Serializable
data class DraftRequest(
    val uid: Long? = null,
    val to: List<String> = emptyList(),
    val cc: List<String> = emptyList(),
    val bcc: List<String> = emptyList(),
    val subject: String = "",
    val body: String = "",
    val html: String? = null,
    val inReplyTo: String? = null,
    val references: String? = null
)

@Serializable
data class DraftResponse(val folder: String, val uid: Long)

// ---------------------------------------------------------------------------------------
// Rules / spam settings
// ---------------------------------------------------------------------------------------

@Serializable
data class RuleCriterionDto(val field: String, val operator: String = "CONTAINS", val value: String)

@Serializable
data class RuleDto(
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    val logic: String = "OR",
    val criteria: List<RuleCriterionDto>,
    val action: String = "MOVE_TO_FOLDER",
    val targetFolder: String? = "Junk",
    val priority: Int = 100,
    val createdBy: String = "user",
    val createdAt: Long = 0,
    val note: String = "",
    val hitCount: Int = 0
)

@Serializable
data class BlacklistDefDto(
    val id: String,
    val label: String,
    val zone: String,
    val kind: String,
    val weight: Int,
    val enabled: Boolean = true,
    val builtIn: Boolean = true,
    val homepage: String = ""
)

@Serializable
data class SpamConfigDto(
    val enabled: Boolean = true,
    val autoMoveToJunk: Boolean = true,
    val spamThreshold: Int = 60,
    val suspiciousThreshold: Int = 30,
    val blacklists: List<BlacklistDefDto> = emptyList(),
    val checkLinkDomains: Boolean = true,
    val useBayes: Boolean = true,
    val brandDetection: Boolean = true,
    val blacklistHitIsSpam: Boolean = true,
    val brandMismatchIsSpam: Boolean = true,
    val safeSenders: List<String> = emptyList(),
    val blockedSenders: List<String> = emptyList(),
    val dnsTimeoutMs: Int = 3000,
    /** Also scan messages already in the Inbox on first sign-in (not just new arrivals). */
    val scanExisting: Boolean = true
)

@Serializable
data class OllamaConfigDto(
    val enabled: Boolean = false,
    val url: String = "http://localhost:11434",
    val model: String = "llama3.2"
)

@Serializable
data class AccountSettingsDto(
    val version: Int = 1,
    val rules: List<RuleDto> = emptyList(),
    val spam: SpamConfigDto = SpamConfigDto(),
    val ollama: OllamaConfigDto = OllamaConfigDto(),
    val signature: String = "",
    /** Senders whose remote images may load automatically. */
    val imageSenders: List<String> = emptyList(),
    /** Local-only junk marks for POP3 accounts (server cannot move messages). */
    val pop3Junk: List<String> = emptyList()
)

@Serializable
data class ProposedCriterionDto(val criterion: RuleCriterionDto, val rationale: String, val confidence: Int)

@Serializable
data class RuleProposalDto(
    val rule: RuleDto,
    val criteria: List<ProposedCriterionDto>,
    val summary: String,
    val claimedOrganisation: String? = null,
    val verdictSummary: String
)

@Serializable
data class LlmAssessmentDto(
    val model: String,
    val available: Boolean,
    val claimedOrganisation: String? = null,
    val isSpam: Boolean? = null,
    val confidence: Double? = null,
    val summary: String = "",
    val extraCriteria: List<RuleCriterionDto> = emptyList(),
    val error: String? = null
)

@Serializable
data class AnalyzeResponse(
    val verdict: VerdictDto,
    val proposal: RuleProposalDto,
    val llm: LlmAssessmentDto? = null,
    val bayes: BayesStatsDto
)

@Serializable
data class BayesStatsDto(val spam: Int, val ham: Int, val vocabulary: Int, val ready: Boolean)

@Serializable
data class RulePreviewRequest(val rule: RuleDto, val folder: String = "INBOX", val limit: Int = 300)

@Serializable
data class RulePreviewResponse(val matches: List<MessageSummaryDto>, val scanned: Int)

@Serializable
data class ApplyRuleRequest(val rule: RuleDto, val folder: String = "INBOX", val limit: Int = 300)

@Serializable
data class TrainRequest(val items: List<MessageRef>, val spam: Boolean)

@Serializable
data class SpamStatusDto(
    val bayes: BayesStatsDto,
    val cached: Int,
    val autoMoved: Int,
    val rules: Int,
    val blacklists: List<BlacklistDefDto>
)

@Serializable
data class ActivityDto(val subject: String, val fromAddress: String, val at: Long, val movedTo: String?, val score: Int, val level: String)

@Serializable
data class OllamaTestResponse(val ok: Boolean, val models: List<String> = emptyList(), val error: String? = null)

// ---------------------------------------------------------------------------------------
// Setup
// ---------------------------------------------------------------------------------------

@Serializable
data class AutodiscoverResponse(
    val email: String,
    val domain: String,
    val found: Boolean,
    val source: String,
    val provider: String? = null,
    val incomingHost: String = "",
    val incomingPort: Int = 993,
    val protocol: String = "IMAP",
    val incomingSecurity: String = "SSL_TLS",
    val smtpHost: String = "",
    val smtpPort: Int = 587,
    val smtpSecurity: String = "STARTTLS",
    val username: String = "",
    val notes: List<String> = emptyList(),
    val appPasswordUrl: String? = null,
    val oauthOnly: Boolean = false
)
