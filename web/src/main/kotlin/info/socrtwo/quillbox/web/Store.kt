package info.socrtwo.quillbox.web

import info.socrtwo.quillbox.web.spam.BayesClassifier
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

@Serializable
data class CachedAnalysis(
    val verdict: VerdictDto,
    val preview: String,
    val at: Long,
    val subject: String = "",
    val fromAddress: String = ""
)

@Serializable
data class AnalysisCache(
    val version: Int = 1,
    val entries: Map<String, CachedAnalysis> = emptyMap(),
    /** Highest Inbox UID already processed, keyed by UIDVALIDITY, so only new mail is scanned. */
    val inboxWatermark: Map<String, Long> = emptyMap()
)

/**
 * On-disk persistence for per-account settings, the learned Bayes model and the analysis
 * cache. Everything lives under `$QUILLBOX_DATA_DIR` (default `~/.quillbox`). Passwords are
 * never written here — they only ever live in the browser and in the server's memory.
 */
class DataStore(rootDir: File? = null) {
    val root: File = rootDir
        ?: System.getenv("QUILLBOX_DATA_DIR")?.takeIf { it.isNotBlank() }?.let { File(it) }
        ?: File(System.getProperty("user.home"), ".quillbox")

    val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    init { root.mkdirs() }

    fun accountDir(email: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(email.trim().lowercase().toByteArray())
        val id = digest.joinToString("") { "%02x".format(it) }.take(16)
        return File(root, "accounts/$id").apply { mkdirs() }
    }

    fun loadSettings(email: String): AccountSettingsDto {
        val f = File(accountDir(email), "settings.json")
        val loaded = if (f.exists()) runCatching { json.decodeFromString<AccountSettingsDto>(f.readText()) }.getOrNull() else null
        return normalise(loaded ?: AccountSettingsDto())
    }

    /** Fills in the built-in blocklist catalogue and the default rule set for new accounts. */
    private fun normalise(s: AccountSettingsDto): AccountSettingsDto {
        val catalogue = info.socrtwo.quillbox.web.spam.Blacklists.catalogue
        val existing = s.spam.blacklists.associateBy { it.id }
        val merged = catalogue.map { def ->
            existing[def.id]?.copy(label = def.label, zone = def.zone, kind = def.kind, builtIn = true, homepage = def.homepage)
                ?: BlacklistDefDto(def.id, def.label, def.zone, def.kind, def.weight, def.enabled, true, def.homepage)
        } + s.spam.blacklists.filter { !it.builtIn && it.id !in catalogue.map { c -> c.id } }
        val rules = if (s.rules.isEmpty() && s.version < 1) defaultRules() else s.rules
        return s.copy(version = 1, rules = rules, spam = s.spam.copy(blacklists = merged))
    }

    fun defaultRules(): List<RuleDto> = listOf(
        RuleDto(
            id = "default-spam-filter",
            name = "Default spam filter",
            logic = "OR",
            criteria = listOf(
                RuleCriterionDto("SUBJECT", "CONTAINS", "viagra"),
                RuleCriterionDto("SUBJECT", "CONTAINS", "lottery winner"),
                RuleCriterionDto("BODY", "CONTAINS", "you have won"),
                RuleCriterionDto("BODY", "CONTAINS", "claim your prize")
            ),
            action = "MOVE_TO_FOLDER",
            targetFolder = "Junk",
            priority = 90,
            createdBy = "user",
            createdAt = System.currentTimeMillis(),
            note = "Seeded on first run; edit or delete freely."
        )
    )

    fun saveSettings(email: String, settings: AccountSettingsDto) {
        writeAtomic(File(accountDir(email), "settings.json"), json.encodeToString(settings))
    }

    fun loadBayes(email: String): BayesClassifier {
        val f = File(accountDir(email), "bayes.tsv")
        val b = BayesClassifier()
        if (f.exists()) runCatching { b.load(f.readText()) }
        if (b.trainedSpam == 0 && b.trainedHam == 0) b.seed()
        return b
    }

    fun saveBayes(email: String, bayes: BayesClassifier) {
        writeAtomic(File(accountDir(email), "bayes.tsv"), bayes.serialize())
    }

    fun loadCache(email: String): AnalysisCache {
        val f = File(accountDir(email), "analysis-cache.json")
        return if (f.exists()) runCatching { json.decodeFromString<AnalysisCache>(f.readText()) }.getOrDefault(AnalysisCache()) else AnalysisCache()
    }

    fun saveCache(email: String, cache: AnalysisCache) {
        // Keep the cache bounded: drop the oldest entries beyond 5000.
        val trimmed = if (cache.entries.size > 5000)
            cache.copy(entries = cache.entries.entries.sortedByDescending { it.value.at }.take(4000).associate { it.key to it.value })
        else cache
        writeAtomic(File(accountDir(email), "analysis-cache.json"), Json { ignoreUnknownKeys = true; encodeDefaults = true }.encodeToString(trimmed))
    }

    private fun writeAtomic(target: File, content: String) {
        target.parentFile.mkdirs()
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(content)
        Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}
