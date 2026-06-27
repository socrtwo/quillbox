package info.socrtwo.quillbox.data.mail

import info.socrtwo.quillbox.data.local.entity.AccountEntity
import info.socrtwo.quillbox.data.model.MailProtocol
import info.socrtwo.quillbox.data.model.SecurityType
import jakarta.activation.DataHandler
import jakarta.mail.Authenticator
import jakarta.mail.Folder
import jakarta.mail.Message
import jakarta.mail.Multipart
import jakarta.mail.Part
import jakarta.mail.PasswordAuthentication
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import jakarta.mail.util.ByteArrayDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Properties
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper over Jakarta (Angus) Mail providing the protocol work for an account:
 * fetching headers + bodies over IMAP/POP3 and sending over SMTP. All calls perform
 * blocking network IO and are dispatched to [Dispatchers.IO].
 */
@Singleton
class MailClient @Inject constructor() {

    /** Verifies that the incoming server accepts the supplied credentials. */
    suspend fun testConnection(account: AccountEntity): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val session = Session.getInstance(incomingProperties(account))
            session.getStore(storeProtocol(account)).use { store ->
                store.connect(account.incomingHost, account.incomingPort, account.username, account.password)
            }
        }
    }

    /**
     * Connects to the account's incoming server, opens [remoteFolder] and returns the
     * messages currently present. POP3 only exposes INBOX, so [remoteFolder] is ignored
     * for POP3 accounts.
     */
    suspend fun fetchMessages(
        account: AccountEntity,
        remoteFolder: String = "INBOX",
        limit: Int = Int.MAX_VALUE
    ): List<FetchedMessage> = withContext(Dispatchers.IO) {
        val session = Session.getInstance(incomingProperties(account))
        val store = session.getStore(storeProtocol(account))
        store.connect(account.incomingHost, account.incomingPort, account.username, account.password)
        try {
            val folderName = if (account.protocol == MailProtocol.POP3) "INBOX" else remoteFolder
            val folder = store.getFolder(folderName)
            folder.open(Folder.READ_ONLY)
            try {
                val all = folder.messages
                // By default sync every message; callers may pass a smaller [limit] to cap it.
                val slice = if (limit < all.size) all.copyOfRange(all.size - limit, all.size) else all
                slice.map { it.toFetchedMessage() }.reversed()
            } finally {
                if (folder.isOpen) folder.close(false)
            }
        } finally {
            if (store.isConnected) store.close()
        }
    }

    /** Sends a message over SMTP using the account credentials. */
    suspend fun sendMessage(
        account: AccountEntity,
        to: List<String>,
        cc: List<String>,
        bcc: List<String>,
        subject: String,
        htmlBody: String,
        plainBody: String,
        attachments: List<OutgoingAttachment>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val props = outgoingProperties(account)
            val session = Session.getInstance(props, object : Authenticator() {
                override fun getPasswordAuthentication() =
                    PasswordAuthentication(account.username, account.password)
            })

            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(account.email, account.displayName))
                setRecipients(Message.RecipientType.TO, toAddressArray(to))
                if (cc.isNotEmpty()) setRecipients(Message.RecipientType.CC, toAddressArray(cc))
                if (bcc.isNotEmpty()) setRecipients(Message.RecipientType.BCC, toAddressArray(bcc))
                setSubject(subject)
            }

            // Body: alternative plain/html part, plus any attachments as a mixed multipart.
            val alternative = MimeMultipart("alternative").apply {
                addBodyPart(MimeBodyPart().apply { setText(plainBody, "utf-8") })
                addBodyPart(MimeBodyPart().apply { setContent(htmlBody, "text/html; charset=utf-8") })
            }

            if (attachments.isEmpty()) {
                message.setContent(alternative)
            } else {
                val mixed = MimeMultipart("mixed")
                mixed.addBodyPart(MimeBodyPart().apply { setContent(alternative) })
                attachments.forEach { att ->
                    mixed.addBodyPart(MimeBodyPart().apply {
                        dataHandler = DataHandler(ByteArrayDataSource(att.bytes, att.mimeType))
                        fileName = att.fileName
                    })
                }
                message.setContent(mixed)
            }

            message.saveChanges()
            Transport.send(message)
        }
    }

    // --- Connection properties -------------------------------------------------

    private fun storeProtocol(account: AccountEntity): String {
        val ssl = account.incomingSecurity == SecurityType.SSL_TLS
        return when (account.protocol) {
            MailProtocol.IMAP -> if (ssl) "imaps" else "imap"
            MailProtocol.POP3 -> if (ssl) "pop3s" else "pop3"
        }
    }

    private fun incomingProperties(account: AccountEntity): Properties {
        val proto = when (account.protocol) {
            MailProtocol.IMAP -> "imap"
            MailProtocol.POP3 -> "pop3"
        }
        return Properties().apply {
            put("mail.store.protocol", storeProtocol(account))
            put("mail.$proto.host", account.incomingHost)
            put("mail.$proto.port", account.incomingPort.toString())
            when (account.incomingSecurity) {
                SecurityType.SSL_TLS -> put("mail.$proto.ssl.enable", "true")
                SecurityType.STARTTLS -> put("mail.$proto.starttls.enable", "true")
                SecurityType.NONE -> { /* no transport security */ }
            }
            // Trust the server's certificate chain via the platform trust store.
            put("mail.$proto.ssl.trust", account.incomingHost)
            put("mail.$proto.connectiontimeout", "15000")
            put("mail.$proto.timeout", "30000")
        }
    }

    private fun outgoingProperties(account: AccountEntity): Properties = Properties().apply {
        put("mail.transport.protocol", "smtp")
        put("mail.smtp.host", account.smtpHost)
        put("mail.smtp.port", account.smtpPort.toString())
        put("mail.smtp.auth", "true")
        when (account.smtpSecurity) {
            SecurityType.SSL_TLS -> put("mail.smtp.ssl.enable", "true")
            SecurityType.STARTTLS -> put("mail.smtp.starttls.enable", "true")
            SecurityType.NONE -> { /* no transport security */ }
        }
        put("mail.smtp.ssl.trust", account.smtpHost)
        put("mail.smtp.connectiontimeout", "15000")
        put("mail.smtp.timeout", "30000")
    }

    private fun toAddressArray(addresses: List<String>): Array<InternetAddress> =
        addresses.filter { it.isNotBlank() }
            .map { InternetAddress(it.trim()) }
            .toTypedArray()

    // --- Parsing ---------------------------------------------------------------

    private fun Message.toFetchedMessage(): FetchedMessage {
        val from = (from?.firstOrNull() as? InternetAddress)?.let {
            it.personal?.let { name -> "$name <${it.address}>" } ?: it.address
        } ?: (from?.firstOrNull()?.toString() ?: "")

        val to = addressList(getRecipients(Message.RecipientType.TO))
        val cc = addressList(getRecipients(Message.RecipientType.CC))

        val parts = ExtractedBody()
        extractBody(this, parts)

        val msgId = (this as? MimeMessage)?.messageID
            ?: "${subject ?: ""}|${sentDate?.time ?: 0}|$from"

        return FetchedMessage(
            messageId = msgId,
            from = from,
            to = to,
            cc = cc,
            subject = subject ?: "(no subject)",
            bodyText = parts.text.toString().trim(),
            bodyHtml = parts.html?.trim(),
            sentDate = sentDate?.time ?: System.currentTimeMillis(),
            receivedDate = (this as? MimeMessage)?.receivedDate?.time ?: System.currentTimeMillis(),
            hasAttachments = parts.attachments.isNotEmpty(),
            attachments = parts.attachments.toList()
        )
    }

    private fun addressList(addresses: Array<jakarta.mail.Address>?): String =
        addresses?.joinToString(", ") { (it as? InternetAddress)?.address ?: it.toString() } ?: ""

    private class ExtractedBody {
        val text = StringBuilder()
        var html: String? = null
        val attachments = mutableListOf<FetchedAttachment>()
    }

    /** Recursively walks a MIME part, collecting plain text, HTML and attachment bytes. */
    private fun extractBody(part: Part, out: ExtractedBody) {
        try {
            val disposition = part.disposition
            val isAttachment = disposition != null &&
                (disposition.equals(Part.ATTACHMENT, ignoreCase = true) ||
                    (disposition.equals(Part.INLINE, ignoreCase = true) && part.fileName != null))

            if (isAttachment || (part.fileName != null && !part.isMimeType("multipart/*"))) {
                captureAttachment(part, out)
                return
            }

            when {
                part.isMimeType("text/plain") -> {
                    out.text.append(part.content?.toString().orEmpty())
                }
                part.isMimeType("text/html") -> {
                    val html = part.content?.toString().orEmpty()
                    out.html = (out.html ?: "") + html
                }
                part.isMimeType("multipart/*") -> {
                    val mp = part.content as Multipart
                    for (i in 0 until mp.count) {
                        extractBody(mp.getBodyPart(i), out)
                    }
                }
            }
        } catch (_: Exception) {
            // Tolerate malformed parts; partial bodies are acceptable for display.
        }
    }

    private fun captureAttachment(part: Part, out: ExtractedBody) {
        try {
            val rawName = part.fileName ?: "attachment"
            val name = runCatching { jakarta.mail.internet.MimeUtility.decodeText(rawName) }.getOrDefault(rawName)
            val mime = part.contentType?.substringBefore(';')?.trim()?.ifBlank { null }
                ?: "application/octet-stream"
            val bytes = part.inputStream.use { it.readBytes() }
            out.attachments.add(FetchedAttachment(fileName = name, mimeType = mime, bytes = bytes))
        } catch (_: Exception) {
            // Skip an attachment we can't read rather than failing the whole message.
        }
    }
}
