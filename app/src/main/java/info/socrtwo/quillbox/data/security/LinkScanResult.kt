package info.socrtwo.quillbox.data.security

/** VirusTotal verdict for a single URL. */
enum class VtVerdict { NOT_SCANNED, CLEAN, SUSPICIOUS, MALICIOUS, ERROR }

/** Per-link analysis: where it points, whether that differs from the sender, and VT result. */
data class LinkScan(
    val url: String,
    val host: String,
    val domainMismatch: Boolean,
    val vt: VtVerdict = VtVerdict.NOT_SCANNED,
    val vtMaliciousCount: Int = 0,
    val vtSuspiciousCount: Int = 0
)

/** Overall safety report for a message. */
data class SafetyReport(
    val senderDomain: String,
    val links: List<LinkScan>,
    val scannedWithVt: Boolean = false,
    val error: String? = null
) {
    val mismatchedLinks: List<LinkScan> get() = links.filter { it.domainMismatch }
    val malicious: Boolean get() = links.any { it.vt == VtVerdict.MALICIOUS }
    val suspicious: Boolean get() = links.any { it.vt == VtVerdict.SUSPICIOUS }

    /** Any reason to warn the user: a VT hit or a sender/link domain mismatch. */
    val hasWarning: Boolean get() = malicious || suspicious || mismatchedLinks.isNotEmpty()

    val severity: Severity
        get() = when {
            malicious -> Severity.DANGER
            suspicious -> Severity.WARNING
            mismatchedLinks.isNotEmpty() -> Severity.CAUTION
            else -> Severity.NONE
        }

    enum class Severity { NONE, CAUTION, WARNING, DANGER }
}
