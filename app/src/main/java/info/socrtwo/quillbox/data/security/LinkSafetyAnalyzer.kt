package info.socrtwo.quillbox.data.security

import android.net.Uri
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Extracts links from a message, flags links whose domain differs from the sender's
 * (a common phishing signal), and — when a VirusTotal key is available — scans each
 * unique link through VirusTotal.
 */
@Singleton
class LinkSafetyAnalyzer @Inject constructor(
    private val virusTotal: VirusTotalClient
) {
    // http/https URLs, and href="..." targets.
    private val urlRegex = Regex("""https?://[^\s"'<>()\]]+""", RegexOption.IGNORE_CASE)
    private val hrefRegex = Regex("""href\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)

    /** Local-only analysis: extract links and flag sender/link domain mismatches. */
    fun analyzeLocally(fromAddress: String, bodyText: String, bodyHtml: String?): SafetyReport {
        val senderDomain = domainOf(emailAddress(fromAddress))
        val urls = extractUrls(bodyText, bodyHtml)
        val links = urls.map { url ->
            val host = hostOf(url)
            val linkDomain = registrableDomain(host)
            val mismatch = senderDomain.isNotEmpty() && linkDomain.isNotEmpty() && linkDomain != senderDomain
            LinkScan(url = url, host = host, domainMismatch = mismatch)
        }
        return SafetyReport(senderDomain = senderDomain, links = links)
    }

    /** Scans the report's links through VirusTotal (up to [maxLinks]) and returns an updated report. */
    suspend fun scanWithVirusTotal(apiKey: String, report: SafetyReport, maxLinks: Int = 8): SafetyReport {
        if (report.links.isEmpty()) return report.copy(scannedWithVt = true)
        val scanned = report.links.take(maxLinks).map { link ->
            val result = virusTotal.scanUrl(apiKey, link.url)
            link.copy(
                vt = result.verdict,
                vtMaliciousCount = result.malicious,
                vtSuspiciousCount = result.suspicious
            )
        }
        // Preserve any links beyond the scan cap unchanged.
        val rest = report.links.drop(maxLinks)
        return report.copy(links = scanned + rest, scannedWithVt = true)
    }

    private fun extractUrls(bodyText: String, bodyHtml: String?): List<String> {
        val found = LinkedHashSet<String>()
        val html = bodyHtml.orEmpty()
        hrefRegex.findAll(html).forEach { m ->
            val target = m.groupValues[1]
            if (target.startsWith("http", ignoreCase = true)) found.add(target.trim())
        }
        urlRegex.findAll(bodyText).forEach { found.add(it.value.trim().trimEnd('.', ',', ')')) }
        urlRegex.findAll(html).forEach { found.add(it.value.trim().trimEnd('.', ',', ')')) }
        return found.toList()
    }

    private fun emailAddress(from: String): String {
        val start = from.indexOf('<')
        val end = from.indexOf('>')
        val addr = if (start >= 0 && end > start) from.substring(start + 1, end) else from
        return addr.trim().lowercase()
    }

    private fun domainOf(email: String): String = email.substringAfter('@', "").let { registrableDomain(it) }

    private fun hostOf(url: String): String =
        runCatching { Uri.parse(url).host?.lowercase().orEmpty() }.getOrDefault("")

    /**
     * Heuristic registrable domain: the last two labels of the host (e.g. mail.example.com
     * -> example.com). Not a full public-suffix implementation, but good enough to flag
     * obvious sender/link mismatches.
     */
    private fun registrableDomain(host: String): String {
        val h = host.removePrefix("www.")
        val parts = h.split('.').filter { it.isNotEmpty() }
        return if (parts.size >= 2) parts.takeLast(2).joinToString(".") else h
    }
}
