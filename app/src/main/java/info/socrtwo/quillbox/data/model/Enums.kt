package info.socrtwo.quillbox.data.model

/** Incoming mail protocol selected during account setup. */
enum class MailProtocol { IMAP, POP3 }

/** Transport security applied to the connection. */
enum class SecurityType {
    /** Implicit TLS/SSL (e.g. IMAPS 993, POP3S 995, SMTPS 465). */
    SSL_TLS,

    /** Upgrade a plaintext connection with STARTTLS (e.g. IMAP 143, SMTP 587). */
    STARTTLS,

    /** No transport security. Not recommended; offered for completeness. */
    NONE
}

/** How a rule combines its criteria. */
enum class MatchLogic { AND, OR }

/** Which message field a single criterion inspects. */
enum class CriteriaField { SENDER, SUBJECT, BODY }

/** What a rule does to a message that matches. */
enum class RuleActionType { MOVE_TO_FOLDER, MARK_READ, DELETE }
