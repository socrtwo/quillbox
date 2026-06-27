package info.socrtwo.quillbox.desktop.mail

import info.socrtwo.quillbox.desktop.model.Account
import info.socrtwo.quillbox.desktop.model.MailProtocol
import info.socrtwo.quillbox.desktop.model.SecurityType
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Properties

/** A message fetched from the server. */
data class FetchedMessage(
    val from: String,
    val to: String,
    val subject: String,
    val bodyText: String,
    val bodyHtml: String?,
    val sentDate: Long,
    val hasAttachments: Boolean
)

/**
 * Jakarta (Angus) Mail wrapper for the desktop client — the same protocol logic the
 * Android app uses, running on the desktop JVM. Network calls run on [Dispatchers.IO].
 */
class MailClient {

    suspend fun fetchInbox(account: Account, limit: Int = 100): List<FetchedMessage> =
        withContext(Dispatchers.IO) {
            val session = Session.getInstance(incomingProperties(account))
            val store = session.getStore(storeProtocol(account))
            store.connect(account.incomingHost, account.incomingPort, account.username, account.password)
            try {
                val folder = store.getFolder("INBOX")
                folder.open(Folder.READ_ONLY)
                try {
                    val all = folder.messages
                    val slice = if (limit < all.size) all.copyOfRange(all.size - limit, all.size) else all
                    slice.map { it.toFetched() }.reversed()
                } finally {
                    if (folder.isOpen) folder.close(false)
                }
            } finally {
                if (store.isConnected) store.close()
            }
        }

    suspend fun send(
        account: Account,
        to: List<String>,
        cc: List<String>,
        bcc: List<String>,
        subject: String,
        plainBody: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val session = Session.getInstance(outgoingProperties(account), object : Authenticator() {
                override fun getPasswordAuthentication() =
                    PasswordAuthentication(account.username, account.password)
            })
            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(account.email, account.displayName))
                setRecipients(Message.RecipientType.TO, addresses(to))
                if (cc.isNotEmpty()) setRecipients(Message.RecipientType.CC, addresses(cc))
                if (bcc.isNotEmpty()) setRecipients(Message.RecipientType.BCC, addresses(bcc))
                setSubject(subject)
            }
            val body = MimeMultipart("alternative").apply {
                addBodyPart(MimeBodyPart().apply { setText(plainBody, "utf-8") })
            }
            message.setContent(body)
            message.saveChanges()
            Transport.send(message)
        }
    }

    private fun addresses(list: List<String>): Array<InternetAddress> =
        list.filter { it.isNotBlank() }.map { InternetAddress(it.trim()) }.toTypedArray()

    private fun storeProtocol(account: Account): String {
        val ssl = account.incomingSecurity == SecurityType.SSL_TLS
        return when (account.protocol) {
            MailProtocol.IMAP -> if (ssl) "imaps" else "imap"
            MailProtocol.POP3 -> if (ssl) "pop3s" else "pop3"
        }
    }

    private fun incomingProperties(account: Account): Properties {
        val proto = if (account.protocol == MailProtocol.IMAP) "imap" else "pop3"
        return Properties().apply {
            put("mail.store.protocol", storeProtocol(account))
            put("mail.$proto.host", account.incomingHost)
            put("mail.$proto.port", account.incomingPort.toString())
            when (account.incomingSecurity) {
                SecurityType.SSL_TLS -> put("mail.$proto.ssl.enable", "true")
                SecurityType.STARTTLS -> put("mail.$proto.starttls.enable", "true")
                SecurityType.NONE -> {}
            }
            put("mail.$proto.ssl.trust", account.incomingHost)
            put("mail.$proto.connectiontimeout", "15000")
            put("mail.$proto.timeout", "30000")
        }
    }

    private fun outgoingProperties(account: Account): Properties = Properties().apply {
        put("mail.transport.protocol", "smtp")
        put("mail.smtp.host", account.smtpHost)
        put("mail.smtp.port", account.smtpPort.toString())
        put("mail.smtp.auth", "true")
        when (account.smtpSecurity) {
            SecurityType.SSL_TLS -> put("mail.smtp.ssl.enable", "true")
            SecurityType.STARTTLS -> put("mail.smtp.starttls.enable", "true")
            SecurityType.NONE -> {}
        }
        put("mail.smtp.ssl.trust", account.smtpHost)
        put("mail.smtp.connectiontimeout", "15000")
        put("mail.smtp.timeout", "30000")
    }

    private fun Message.toFetched(): FetchedMessage {
        val from = (from?.firstOrNull() as? InternetAddress)?.let {
            it.personal?.let { name -> "$name <${it.address}>" } ?: it.address
        } ?: (from?.firstOrNull()?.toString() ?: "")
        val to = getRecipients(Message.RecipientType.TO)
            ?.joinToString(", ") { (it as? InternetAddress)?.address ?: it.toString() } ?: ""

        val acc = Body()
        extract(this, acc)

        return FetchedMessage(
            from = from,
            to = to,
            subject = subject ?: "(no subject)",
            bodyText = acc.text.toString().trim(),
            bodyHtml = acc.html?.trim(),
            sentDate = sentDate?.time ?: System.currentTimeMillis(),
            hasAttachments = acc.hasAttachments
        )
    }

    private class Body {
        val text = StringBuilder()
        var html: String? = null
        var hasAttachments = false
    }

    private fun extract(part: Part, out: Body) {
        try {
            val disposition = part.disposition
            if (disposition != null && disposition.equals(Part.ATTACHMENT, ignoreCase = true)) {
                out.hasAttachments = true
                return
            }
            when {
                part.isMimeType("text/plain") -> out.text.append(part.content?.toString().orEmpty())
                part.isMimeType("text/html") -> out.html = (out.html ?: "") + part.content?.toString().orEmpty()
                part.isMimeType("multipart/*") -> {
                    val mp = part.content as Multipart
                    for (i in 0 until mp.count) extract(mp.getBodyPart(i), out)
                }
                part.fileName != null -> out.hasAttachments = true
            }
        } catch (_: Exception) {
            // Tolerate malformed parts.
        }
    }
}
