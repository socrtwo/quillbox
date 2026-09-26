package info.socrtwo.quillbox.web

import info.socrtwo.quillbox.web.Mapping.preview
import info.socrtwo.quillbox.web.Mapping.toConfig
import info.socrtwo.quillbox.web.Mapping.toDto
import info.socrtwo.quillbox.web.Mapping.toFacts
import info.socrtwo.quillbox.web.Mapping.toRule
import info.socrtwo.quillbox.web.Mapping.toSummaryDto
import info.socrtwo.quillbox.web.spam.BayesClassifier
import info.socrtwo.quillbox.web.spam.BlacklistChecker
import info.socrtwo.quillbox.web.spam.MessageFacts
import info.socrtwo.quillbox.web.spam.Rule
import info.socrtwo.quillbox.web.spam.RuleAction
import info.socrtwo.quillbox.web.spam.RuleEngine
import info.socrtwo.quillbox.web.spam.RuleField
import info.socrtwo.quillbox.web.spam.RuleProposer
import info.socrtwo.quillbox.web.spam.SpamEngine
import info.socrtwo.quillbox.web.spam.SpamLevel
import info.socrtwo.quillbox.web.spam.SpamReason
import jakarta.mail.Flags
import jakarta.mail.MessagingException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Everything the server keeps for one signed-in account: the live mail connection, the
 * account's settings and rules, its learned Bayes model, the per-message analysis cache and
 * the background analyser that scans new mail and files spam into Junk.
 */
class AccountContext(
    val account: AccountDto,
    private val store: DataStore,
    blacklists: BlacklistChecker
) {
    val mail = MailSession(account)
    @Volatile var settings: AccountSettingsDto = store.loadSettings(account.email)
        private set
    val bayes: BayesClassifier = store.loadBayes(account.email)
    private val engine = SpamEngine(blacklists, bayes)

    private val cache = ConcurrentHashMap<String, CachedAnalysis>()
    @Volatile private var initialised: Boolean
    @Volatile var version: Long = 0L
        private set
    @Volatile var lastUsed: Long = System.currentTimeMillis()

    private val ioLock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = LinkedHashMap<String, LinkedHashSet<Long>>()
    private var draining = false
    private val userAddresses: List<String> = listOf(account.email.lowercase(), account.username.lowercase()).distinct()

    companion object {
        const val LOCAL_JUNK = "__local_junk__"
        private const val BATCH = 6
    }

    init {
        val loaded = store.loadCache(account.email)
        cache.putAll(loaded.entries)
        initialised = loaded.inboxWatermark.isNotEmpty() || settings.spam.scanExisting
    }

    val capabilities: Capabilities
        get() = Capabilities(folders = mail.isImap, move = mail.isImap, flags = mail.isImap, search = true, drafts = mail.isImap)

    private suspend fun <T> io(block: () -> T): T = ioLock.withLock { withContext(Dispatchers.IO) { block() } }

    private fun touch() { lastUsed = System.currentTimeMillis() }
    private fun bump() { version++ }

    fun close() {
        scope.cancel()
        mail.close()
        persistCache()
    }

    // --- settings -----------------------------------------------------------------------

    @Synchronized
    fun updateSettings(new: AccountSettingsDto): AccountSettingsDto {
        settings = new.copy(version = 1)
        store.saveSettings(account.email, settings)
        bump()
        return settings
    }

    private fun spamRules(): List<Rule> = settings.rules.map { it.toRule() }

    private fun key(s: RawSummary, folder: String): String = s.messageId.ifBlank { "uid:$folder:${s.uid}" }

    @Synchronized
    private fun persistCache() {
        runCatching {
            store.saveCache(account.email, AnalysisCache(entries = HashMap(cache), inboxWatermark = if (initialised) mapOf("initialised" to 1L) else emptyMap()))
        }
    }

    private fun persistBayes() = runCatching { store.saveBayes(account.email, bayes) }

    // --- folders ------------------------------------------------------------------------

    suspend fun folders(): List<FolderDto> {
        touch()
        val list = io { mail.listFolders() }.map { FolderDto(it.path, it.name, it.role, it.delimiter, it.unread, it.total, it.depth) }.toMutableList()
        if (mail.isImap) {
            if (list.none { it.role == "junk" }) {
                val path = runCatching { io { mail.ensureFolderForRole("junk") } }.getOrNull()
                if (path != null) list.add(FolderDto(path, "Junk Email", "junk", "/", 0, 0, 0))
            }
        } else {
            val junk = settings.pop3Junk.size
            list.add(FolderDto(LOCAL_JUNK, "Junk Email (local)", "junk", "/", 0, junk, 0))
        }
        return list
    }

    // --- listing ------------------------------------------------------------------------

    suspend fun listMessages(folder: String, limit: Int, offset: Int, rescan: Boolean = false): MessageListResponse {
        touch()
        if (!mail.isImap && folder == LOCAL_JUNK) return listLocalJunk(limit)
        val (total, raws0) = io { mail.listMessages(folder, limit, offset) }
        val raws = if (!mail.isImap && folder.equals(MailSession.INBOX, true)) raws0.filter { key(it, folder) !in settings.pop3Junk.toSet() } else raws0
        if (rescan) raws.forEach { cache.remove(key(it, folder)) }
        val missing = raws.filter { !cache.containsKey(key(it, folder)) }
        if (missing.isNotEmpty() && settings.spam.enabled) enqueue(folder, missing.map { it.uid })
        val messages = raws.map { it.toSummaryDto(folder, cache[key(it, folder)]) }
        return MessageListResponse(
            folder = folder, total = total, unread = raws.count { !it.seen }, messages = messages,
            pending = if (settings.spam.enabled) missing.size else 0, version = version
        )
    }

    private suspend fun listLocalJunk(limit: Int): MessageListResponse {
        val junkKeys = settings.pop3Junk.toSet()
        val (_, raws) = io { mail.listMessages(MailSession.INBOX, maxOf(limit, 500), 0) }
        val junk = raws.filter { key(it, MailSession.INBOX) in junkKeys }
        return MessageListResponse(LOCAL_JUNK, junk.size, junk.count { !it.seen }, junk.map { it.toSummaryDto(MailSession.INBOX, cache[key(it, MailSession.INBOX)]) }, version = version)
    }

    suspend fun search(folder: String, q: String, limit: Int): SearchResponse {
        touch()
        val raws = io { mail.search(folder, q, limit) }
        return SearchResponse(folder, q, raws.map { it.toSummaryDto(folder, cache[key(it, folder)]) })
    }

    // --- background analysis -------------------------------------------------------------

    private fun enqueue(folder: String, uids: List<Long>) {
        synchronized(queue) {
            queue.getOrPut(folder) { LinkedHashSet() }.addAll(uids)
            if (!draining) {
                draining = true
                scope.launch { drain() }
            }
        }
    }

    private suspend fun drain() {
        try {
            while (true) {
                val next: Pair<String, List<Long>> = synchronized(queue) {
                    val entry = queue.entries.firstOrNull { it.value.isNotEmpty() }
                    if (entry == null) { draining = false; null } else {
                        val batch = entry.value.take(BATCH)
                        entry.value.removeAll(batch.toSet())
                        if (entry.value.isEmpty()) queue.remove(entry.key)
                        entry.key to batch
                    }
                } ?: break
                runCatching { processBatch(next.first, next.second) }
                    .onFailure { bump() }
            }
        } finally {
            synchronized(queue) { draining = false }
        }
    }

    /** The number of messages still queued for analysis. */
    fun pending(): Int = synchronized(queue) { queue.values.sumOf { it.size } }

    private suspend fun processBatch(folder: String, uids: List<Long>) {
        val raws = io { mail.fetchMessages(folder, uids) }
        if (raws.isEmpty()) return
        val cfg = settings.spam.toConfig()
        val rules = spamRules()
        val isInbox = folder.equals(MailSession.INBOX, ignoreCase = true)
        val results = coroutineScope {
            raws.map { (uid, raw) ->
                async(Dispatchers.IO) { Triple(uid, raw, engine.analyze(raw.toFacts(userAddresses), cfg, rules)) }
            }.awaitAll()
        }
        val moves = HashMap<String, MutableList<Long>>()      // destination -> uids
        val markRead = ArrayList<Long>()
        val flag = ArrayList<Long>()
        val ruleHits = HashMap<String, Int>()
        var moved = 0
        for ((uid, raw, verdict) in results) {
            val k = key(raw.summary, folder)
            val previous = cache[k]
            val userSaidHam = previous?.verdict?.trained == "ham"
            var dto = verdict.toDto().copy(trained = previous?.verdict?.trained)
            val rule = verdict.matchedRule
            var dest: String? = null
            if (isInbox && initialised && !userSaidHam && mail.isImap) {
                when {
                    rule != null && rule.action == RuleAction.DELETE -> dest = runCatching { mail.ensureFolderForRole("trash") }.getOrNull()
                    rule != null && rule.action == RuleAction.MOVE_TO_FOLDER && rule.targetFolder != null ->
                        dest = resolveFolder(rule.targetFolder)
                    verdict.isSpam && cfg.autoMoveToJunk -> dest = runCatching { io { mail.ensureFolderForRole("junk") } }.getOrNull()
                }
                if (rule != null && rule.action == RuleAction.MARK_READ) markRead += uid
                if (rule != null && rule.action == RuleAction.FLAG) flag += uid
            }
            if (isInbox && !mail.isImap && initialised && !userSaidHam && verdict.isSpam && cfg.autoMoveToJunk) {
                // POP3: remember locally that this message belongs in Junk.
                dest = LOCAL_JUNK
            }
            rule?.let { ruleHits[it.id] = (ruleHits[it.id] ?: 0) + 1 }
            if (dest != null && dest != folder) {
                moves.getOrPut(dest) { ArrayList() }.add(uid)
                dto = dto.copy(autoMoved = true, autoMovedTo = dest)
                moved++
            }
            cache[k] = CachedAnalysis(dto, preview(raw.bodyText, raw.bodyHtml), System.currentTimeMillis(), raw.summary.subject, raw.summary.fromAddress)
        }
        if (moves.isNotEmpty() || markRead.isNotEmpty() || flag.isNotEmpty()) {
            io {
                for ((dest, ids) in moves) {
                    if (dest == LOCAL_JUNK) {
                        val keys = ids.mapNotNull { id -> raws[id]?.summary?.let { key(it, folder) } }
                        synchronized(this) { settings = settings.copy(pop3Junk = (settings.pop3Junk + keys).distinct()); store.saveSettings(account.email, settings) }
                    } else runCatching { mail.move(folder, ids, dest) }
                }
                if (markRead.isNotEmpty()) runCatching { mail.setFlag(folder, markRead, Flags.Flag.SEEN, true) }
                if (flag.isNotEmpty()) runCatching { mail.setFlag(folder, flag, Flags.Flag.FLAGGED, true) }
            }
        }
        if (ruleHits.isNotEmpty()) synchronized(this) {
            settings = settings.copy(rules = settings.rules.map { r -> ruleHits[r.id]?.let { n -> r.copy(hitCount = r.hitCount + n) } ?: r })
            store.saveSettings(account.email, settings)
        }
        if (!initialised) initialised = true
        bump()
        persistCache()
    }

    /** Maps a rule's folder name to a real server path, creating it at the top level if needed. */
    private suspend fun resolveFolder(name: String): String? {
        if (engine.isJunkFolder(name)) return runCatching { io { mail.ensureFolderForRole("junk") } }.getOrNull()
        val lower = name.lowercase()
        if (lower in setOf("trash", "deleted", "deleted items", "bin")) return runCatching { io { mail.ensureFolderForRole("trash") } }.getOrNull()
        if (lower in setOf("archive")) return runCatching { io { mail.ensureFolderForRole("archive") } }.getOrNull()
        val folders = runCatching { io { mail.listFolders() } }.getOrDefault(emptyList())
        folders.firstOrNull { it.path.equals(name, true) || it.name.equals(name, true) }?.let { return it.path }
        return runCatching {
            io {
                val s = mail.connect()
                val f = s.getFolder(name)
                if (!f.exists()) f.create(jakarta.mail.Folder.HOLDS_MESSAGES)
                f.fullName
            }
        }.getOrNull()
    }

    // --- single message -----------------------------------------------------------------

    suspend fun message(folder: String, uid: Long): MessageDetailDto? {
        touch()
        val realFolder = if (folder == LOCAL_JUNK) MailSession.INBOX else folder
        val raw = io { mail.getMessage(realFolder, uid) } ?: return null
        val k = key(raw.summary, realFolder)
        val cached = cache[k] ?: run {
            val verdict = withContext(Dispatchers.IO) { engine.analyze(raw.toFacts(userAddresses), settings.spam.toConfig(), spamRules()) }
            CachedAnalysis(verdict.toDto(), preview(raw.bodyText, raw.bodyHtml), System.currentTimeMillis(), raw.summary.subject, raw.summary.fromAddress)
                .also { cache[k] = it; persistCache() }
        }
        val s = raw.summary
        val interesting = listOf("Message-ID", "Return-Path", "Reply-To", "X-Mailer", "Authentication-Results", "List-Unsubscribe", "X-Originating-IP", "Received")
        return MessageDetailDto(
            uid = s.uid, folder = folder, messageId = s.messageId, fromName = s.fromName, fromAddress = s.fromAddress,
            to = s.to, cc = s.cc, replyTo = s.replyTo, subject = s.subject, date = s.date, read = s.seen, flagged = s.flagged,
            answered = s.answered, bodyText = raw.bodyText, bodyHtml = raw.bodyHtml, attachments = raw.attachments,
            inReplyTo = raw.inReplyTo, references = raw.references, listUnsubscribe = s.headers["List-Unsubscribe"]?.firstOrNull(),
            headers = interesting.mapNotNull { h -> s.headers[h]?.let { h to it.joinToString("\n") } }.toMap(),
            verdict = cached.verdict
        )
    }

    suspend fun attachment(folder: String, uid: Long, index: Int?, cid: String?): MailSession.AttachmentData? {
        touch()
        val realFolder = if (folder == LOCAL_JUNK) MailSession.INBOX else folder
        return io { mail.getAttachment(realFolder, uid, index, cid) }
    }

    // --- AI analysis & rule proposals -----------------------------------------------------

    suspend fun analyze(folder: String, uid: Long): AnalyzeResponse? {
        touch()
        val realFolder = if (folder == LOCAL_JUNK) MailSession.INBOX else folder
        val raw = io { mail.getMessage(realFolder, uid) } ?: return null
        val facts = raw.toFacts(userAddresses)
        val cfg = settings.spam.toConfig()
        val verdict = withContext(Dispatchers.IO) { engine.analyze(facts, cfg, spamRules()) }
        val junkName = if (mail.isImap) "Junk" else LOCAL_JUNK
        var proposal = RuleProposer.propose(facts, verdict, if (cfg.useBayes) bayes else null, junkName).toDto()
        val llm = if (settings.ollama.enabled) withContext(Dispatchers.IO) { Ollama(settings.ollama).assess(facts, verdict.senderDomain) } else null
        if (llm != null && llm.error == null) {
            val existing = proposal.rule.criteria.map { it.field + "|" + it.value.lowercase() }.toSet()
            val extra = llm.extraCriteria.filter { (it.field + "|" + it.value.lowercase()) !in existing }
            if (extra.isNotEmpty()) {
                proposal = proposal.copy(
                    rule = proposal.rule.copy(criteria = proposal.rule.criteria + extra),
                    criteria = proposal.criteria + extra.map { ProposedCriterionDto(it, "Suggested by the local ${llm.model} model.", 50) }
                )
            }
        }
        val k = key(raw.summary, realFolder)
        val prev = cache[k]
        cache[k] = CachedAnalysis(verdict.toDto().copy(autoMoved = prev?.verdict?.autoMoved ?: false, autoMovedTo = prev?.verdict?.autoMovedTo, trained = prev?.verdict?.trained),
            preview(raw.bodyText, raw.bodyHtml), System.currentTimeMillis(), raw.summary.subject, raw.summary.fromAddress)
        bump(); persistCache()
        return AnalyzeResponse(cache[k]!!.verdict, proposal, llm, bayesStats())
    }

    fun bayesStats() = BayesStatsDto(bayes.trainedSpam, bayes.trainedHam, bayes.vocabulary, bayes.ready)

    suspend fun previewRule(req: RulePreviewRequest): RulePreviewResponse {
        touch()
        val rule = req.rule.toRule()
        val folder = if (req.folder == LOCAL_JUNK) MailSession.INBOX else req.folder
        val (_, raws) = io { mail.listMessages(folder, req.limit, 0) }
        val needsBody = rule.criteria.any { it.field == RuleField.BODY || it.field == RuleField.ATTACHMENT_NAME }
        val matches = ArrayList<MessageSummaryDto>()
        if (needsBody) {
            val headerOnlyMatches = raws.filter { RuleEngine.matches(rule, it.toFacts(userAddresses)) }.map { it.uid }.toSet()
            for (chunk in raws.chunked(20)) {
                val full = io { mail.fetchMessages(folder, chunk.map { it.uid }) }
                for (s in chunk) {
                    val facts = full[s.uid]?.toFacts(userAddresses) ?: s.toFacts(userAddresses)
                    if (s.uid in headerOnlyMatches || RuleEngine.matches(rule, facts)) matches += s.toSummaryDto(folder, cache[key(s, folder)])
                }
            }
        } else {
            raws.filter { RuleEngine.matches(rule, it.toFacts(userAddresses)) }.forEach { matches += it.toSummaryDto(folder, cache[key(it, folder)]) }
        }
        return RulePreviewResponse(matches, raws.size)
    }

    /** Applies [req.rule] to the messages currently in the folder ("send all similar current mail to Junk"). */
    suspend fun applyRule(req: ApplyRuleRequest): BatchResult {
        val preview = previewRule(RulePreviewRequest(req.rule, req.folder, req.limit))
        val rule = req.rule.toRule()
        val items = preview.matches.map { MessageRef(it.folder, it.uid) }
        if (items.isEmpty()) return BatchResult(0, 0)
        return when (rule.action) {
            RuleAction.MOVE_TO_FOLDER -> {
                val target = rule.targetFolder ?: "Junk"
                if (engine.isJunkFolder(target)) junk(JunkRequest(items, junk = true)) else {
                    val dest = resolveFolder(target) ?: return BatchResult(0, items.size, listOf("Folder $target not found"))
                    move(MoveRequest(items, dest))
                }
            }
            RuleAction.DELETE -> delete(DeleteRequest(items))
            RuleAction.MARK_READ -> flags(FlagRequest(items, read = true))
            RuleAction.FLAG -> flags(FlagRequest(items, flagged = true))
            RuleAction.MARK_SAFE -> BatchResult(items.size, 0)
        }
    }

    // --- junk / training ------------------------------------------------------------------

    suspend fun junk(req: JunkRequest): BatchResult {
        touch()
        var ok = 0; val errors = ArrayList<String>()
        val junkPath = if (mail.isImap) runCatching { io { mail.ensureFolderForRole("junk") } }.getOrNull() else LOCAL_JUNK
        val byFolder = req.items.groupBy { it.folder }
        val newBlocked = LinkedHashSet<String>(); val newSafe = LinkedHashSet<String>()
        val localJunkAdd = ArrayList<String>(); val localJunkRemove = ArrayList<String>()
        val movedRefs = ArrayList<MessageRef>()
        for ((folder, refs) in byFolder) {
            val realFolder = if (folder == LOCAL_JUNK) MailSession.INBOX else folder
            val raws = runCatching { io { mail.fetchMessages(realFolder, refs.map { it.uid }) } }.getOrElse { errors += it.message.orEmpty(); emptyMap() }
            for ((uid, raw) in raws) {
                val facts = raw.toFacts(userAddresses)
                val k = key(raw.summary, realFolder)
                learn(k, facts, spam = req.junk)
                if (req.junk) {
                    if (req.blockSender && facts.fromAddress.isNotBlank()) newBlocked += facts.fromAddress
                    if (!mail.isImap) localJunkAdd += k
                } else {
                    if (req.safeSender && facts.fromAddress.isNotBlank()) newSafe += facts.fromAddress
                    if (!mail.isImap) localJunkRemove += k
                    val prev = cache[k]?.verdict
                    val cleared = (prev ?: VerdictDto(0, "CLEAN", emptyList(), BrandDto(), AuthDto())).copy(
                        score = 0, level = SpamLevel.CLEAN.name, trained = "ham", autoMoved = false, autoMovedTo = null,
                        reasons = listOf(SpamReasonDto("USER_NOT_JUNK", "Marked as not junk by you", "This message will never be moved automatically again.", -100)) +
                            (prev?.reasons?.filter { it.code != "USER_JUNK" } ?: emptyList())
                    )
                    cache[k] = (cache[k] ?: CachedAnalysis(cleared, preview(raw.bodyText, raw.bodyHtml), System.currentTimeMillis(), raw.summary.subject, raw.summary.fromAddress)).copy(verdict = cleared)
                }
                ok++
            }
            if (mail.isImap && raws.isNotEmpty()) {
                val dest = if (req.junk) junkPath else MailSession.INBOX
                if (dest != null && dest != realFolder) runCatching { io { mail.move(realFolder, raws.keys.toList(), dest) } }
                    .onSuccess { m -> m.forEach { (_, nu) -> if (nu != null) movedRefs += MessageRef(dest, nu) } }
                    .onFailure { errors += it.message.orEmpty() }
            } else if (!mail.isImap) {
                raws.keys.forEach { movedRefs += MessageRef(if (req.junk) LOCAL_JUNK else MailSession.INBOX, it) }
            }
        }
        synchronized(this) {
            var s = settings
            if (newBlocked.isNotEmpty()) s = s.copy(spam = s.spam.copy(blockedSenders = (s.spam.blockedSenders + newBlocked).distinct(), safeSenders = s.spam.safeSenders - newBlocked))
            if (newSafe.isNotEmpty()) s = s.copy(spam = s.spam.copy(safeSenders = (s.spam.safeSenders + newSafe).distinct(), blockedSenders = s.spam.blockedSenders - newSafe))
            if (localJunkAdd.isNotEmpty() || localJunkRemove.isNotEmpty()) s = s.copy(pop3Junk = (s.pop3Junk + localJunkAdd - localJunkRemove.toSet()).distinct())
            if (s != settings) { settings = s; store.saveSettings(account.email, s) }
        }
        persistBayes(); persistCache(); bump()
        return BatchResult(ok, req.items.size - ok, errors.filter { it.isNotBlank() }, movedRefs)
    }

    suspend fun train(req: TrainRequest): BatchResult {
        touch()
        var ok = 0
        for ((folder, refs) in req.items.groupBy { it.folder }) {
            val realFolder = if (folder == LOCAL_JUNK) MailSession.INBOX else folder
            val raws = runCatching { io { mail.fetchMessages(realFolder, refs.map { it.uid }) } }.getOrDefault(emptyMap())
            for ((_, raw) in raws) { learn(key(raw.summary, realFolder), raw.toFacts(userAddresses), req.spam); ok++ }
        }
        persistBayes(); persistCache(); bump()
        return BatchResult(ok, req.items.size - ok)
    }

    private fun learn(k: String, facts: MessageFacts, spam: Boolean) {
        val tokens = BayesClassifier.tokenize(facts)
        val prev = cache[k]
        when (prev?.verdict?.trained) {
            "spam" -> if (!spam) bayes.untrain(tokens, spam = true)
            "ham" -> if (spam) bayes.untrain(tokens, spam = false)
        }
        if (prev?.verdict?.trained != (if (spam) "spam" else "ham")) bayes.train(tokens, spam)
        val label = if (spam) "spam" else "ham"
        val base = prev ?: CachedAnalysis(VerdictDto(if (spam) 100 else 0, if (spam) "SPAM" else "CLEAN", emptyList(), BrandDto(), AuthDto()), preview(facts.bodyText, facts.bodyHtml), System.currentTimeMillis(), facts.subject, facts.fromAddress)
        val reasons = base.verdict.reasons.filter { it.code != "USER_JUNK" && it.code != "USER_NOT_JUNK" }
        val v = if (spam) base.verdict.copy(trained = label, score = 100, level = "SPAM",
            reasons = listOf(SpamReasonDto("USER_JUNK", "Marked as junk by you", "The classifier has learned from this message.", 100)) + reasons)
        else base.verdict.copy(trained = label, score = 0, level = "CLEAN",
            reasons = listOf(SpamReasonDto("USER_NOT_JUNK", "Marked as not junk by you", "The classifier has learned from this message.", -100)) + reasons)
        cache[k] = base.copy(verdict = v, at = System.currentTimeMillis())
    }

    // --- plain mutations ---------------------------------------------------------------------

    suspend fun move(req: MoveRequest): BatchResult {
        touch()
        var ok = 0; val errors = ArrayList<String>(); val moved = ArrayList<MessageRef>()
        for ((folder, refs) in req.items.groupBy { it.folder }) {
            runCatching { io { mail.move(folder, refs.map { it.uid }, req.to) } }
                .onSuccess { m -> ok += m.size; m.forEach { (_, nu) -> if (nu != null) moved += MessageRef(req.to, nu) } }
                .onFailure { errors += it.message ?: "move failed" }
        }
        bump()
        return BatchResult(ok, req.items.size - ok, errors, moved)
    }

    suspend fun flags(req: FlagRequest): BatchResult {
        touch()
        var ok = 0; val errors = ArrayList<String>()
        for ((folder, refs) in req.items.groupBy { it.folder }) {
            val realFolder = if (folder == LOCAL_JUNK) MailSession.INBOX else folder
            runCatching {
                io {
                    req.read?.let { mail.setFlag(realFolder, refs.map { r -> r.uid }, Flags.Flag.SEEN, it) }
                    req.flagged?.let { mail.setFlag(realFolder, refs.map { r -> r.uid }, Flags.Flag.FLAGGED, it) }
                }
            }.onSuccess { ok += refs.size }.onFailure { errors += it.message ?: "flag failed" }
        }
        bump()
        return BatchResult(ok, req.items.size - ok, errors)
    }

    suspend fun delete(req: DeleteRequest): BatchResult {
        touch()
        var ok = 0; val errors = ArrayList<String>()
        val trash = if (mail.isImap) runCatching { io { mail.ensureFolderForRole("trash") } }.getOrNull() else null
        val moved = ArrayList<MessageRef>()
        for ((folder, refs) in req.items.groupBy { it.folder }) {
            val realFolder = if (folder == LOCAL_JUNK) MailSession.INBOX else folder
            val permanent = req.permanent || trash == null || realFolder == trash
            runCatching {
                io {
                    if (permanent) mail.deletePermanently(realFolder, refs.map { it.uid })
                    else mail.move(realFolder, refs.map { it.uid }, trash!!).also { m -> m.forEach { (_, nu) -> if (nu != null) moved += MessageRef(trash, nu) } }.size
                }
            }.onSuccess { ok += it }.onFailure { errors += it.message ?: "delete failed" }
        }
        bump()
        return BatchResult(ok, req.items.size - ok, errors, moved)
    }

    // --- sending / drafts ------------------------------------------------------------------------

    suspend fun send(req: SendRequest): ApiStatus {
        touch()
        val message = io { mail.send(req) }
        if (mail.isImap) {
            runCatching { io { mail.append(mail.ensureFolderForRole("sent"), message, Flags(Flags.Flag.SEEN)) } }
            req.replyTo?.let { ref -> runCatching { io { mail.setFlag(ref.folder, listOf(ref.uid), Flags.Flag.ANSWERED, true) } } }
            req.draftUid?.let { uid -> runCatching { io { mail.deletePermanently(mail.ensureFolderForRole("drafts"), listOf(uid)) } } }
        }
        bump()
        return ApiStatus("sent")
    }

    suspend fun saveDraft(req: DraftRequest): DraftResponse {
        touch()
        if (!mail.isImap) throw MessagingException("Drafts need an IMAP account")
        val drafts = io { mail.ensureFolderForRole("drafts") }
        val msg = io { mail.buildMessage(SendRequest(null, req.to, req.cc, req.bcc, req.subject, req.body, req.html, emptyList(), req.inReplyTo, req.references)) }
        val uid = io {
            req.uid?.let { runCatching { mail.deletePermanently(drafts, listOf(it)) } }
            mail.append(drafts, msg, Flags(Flags.Flag.DRAFT).apply { add(Flags.Flag.SEEN) })
        }
        bump()
        return DraftResponse(drafts, uid ?: -1L)
    }

    /** Messages Quillbox moved automatically after [since]; drives the "moved to Junk" toasts. */
    fun activity(since: Long): List<ActivityDto> = cache.values
        .filter { it.verdict.autoMoved && it.at > since }
        .sortedByDescending { it.at }
        .take(50)
        .map { ActivityDto(it.subject, it.fromAddress, it.at, it.verdict.autoMovedTo, it.verdict.score, it.verdict.level) }

    fun status(): SpamStatusDto = SpamStatusDto(
        bayes = bayesStats(),
        cached = cache.size,
        autoMoved = cache.values.count { it.verdict.autoMoved },
        rules = settings.rules.size,
        blacklists = settings.spam.blacklists
    )
}
