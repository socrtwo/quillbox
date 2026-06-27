package info.socrtwo.quillbox.data.mail

/** A message retrieved from the server before it is persisted to Room. */
data class FetchedMessage(
    val messageId: String,
    val from: String,
    val to: String,
    val cc: String,
    val subject: String,
    val bodyText: String,
    val bodyHtml: String?,
    val sentDate: Long,
    val receivedDate: Long,
    val hasAttachments: Boolean
)

/** An attachment selected by the user to send with an outgoing message. */
data class OutgoingAttachment(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray
)
