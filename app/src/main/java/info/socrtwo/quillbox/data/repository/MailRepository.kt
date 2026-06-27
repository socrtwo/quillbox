package info.socrtwo.quillbox.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import info.socrtwo.quillbox.data.local.dao.AccountDao
import info.socrtwo.quillbox.data.local.dao.AttachmentDao
import info.socrtwo.quillbox.data.local.dao.FolderDao
import info.socrtwo.quillbox.data.local.dao.MessageDao
import info.socrtwo.quillbox.data.local.entity.AccountEntity
import info.socrtwo.quillbox.data.local.entity.AttachmentEntity
import info.socrtwo.quillbox.data.local.entity.FolderEntity
import info.socrtwo.quillbox.data.local.entity.MessageEntity
import info.socrtwo.quillbox.data.mail.FetchedMessage
import info.socrtwo.quillbox.data.mail.MailClient
import info.socrtwo.quillbox.data.mail.OutgoingAttachment
import info.socrtwo.quillbox.data.rules.RulesEngine
import kotlinx.coroutines.flow.Flow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MailRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accountDao: AccountDao,
    private val folderDao: FolderDao,
    private val messageDao: MessageDao,
    private val attachmentDao: AttachmentDao,
    private val mailClient: MailClient,
    private val ruleRepository: RuleRepository,
    private val rulesEngine: RulesEngine
) {
    companion object {
        const val INBOX = "Inbox"
        const val SPAM = "Spam"
        const val SENT = "Sent"
        const val TRASH = "Trash"
        val DEFAULT_FOLDERS = listOf(INBOX, SENT, SPAM, TRASH)
    }

    fun observeFolders(accountId: Long): Flow<List<FolderEntity>> =
        folderDao.observeFolders(accountId)

    fun observeMessages(folderId: Long): Flow<List<MessageEntity>> =
        messageDao.observeByFolder(folderId)

    fun observeMessage(messageId: Long): Flow<MessageEntity?> =
        messageDao.observeById(messageId)

    fun observeUnreadCount(folderId: Long): Flow<Int> =
        messageDao.observeUnreadCount(folderId)

    /** Creates the default local folder set for an account if it has none yet. */
    suspend fun ensureDefaultFolders(accountId: Long) {
        if (folderDao.countForAccount(accountId) == 0) {
            DEFAULT_FOLDERS.forEach { name ->
                folderDao.insert(FolderEntity(accountId = accountId, name = name))
            }
        }
    }

    private suspend fun folderId(accountId: Long, name: String): Long {
        folderDao.getByName(accountId, name)?.let { return it.id }
        return folderDao.insert(FolderEntity(accountId = accountId, name = name))
    }

    /**
     * Pulls new mail for [account] from the server, runs each message through the rules
     * engine and stores the result locally. Returns the number of newly stored messages.
     */
    suspend fun syncMessages(account: AccountEntity): Result<Int> = runCatching {
        ensureDefaultFolders(account.id)
        val rules = ruleRepository.getEnabledRules()
        val inboxId = folderId(account.id, INBOX)

        val fetched: List<FetchedMessage> = mailClient.fetchMessages(account)
        var stored = 0
        for (msg in fetched) {
            if (messageDao.exists(account.id, msg.messageId)) continue

            val outcome = rulesEngine.evaluate(msg, rules)
            if (outcome.delete) continue // dropped by a rule before it ever lands

            val destFolderId = outcome.targetFolder
                ?.let { folderId(account.id, it) }
                ?: inboxId

            val rowId = messageDao.insert(
                MessageEntity(
                    accountId = account.id,
                    folderId = destFolderId,
                    messageId = msg.messageId,
                    fromAddress = msg.from,
                    toAddresses = msg.to,
                    ccAddresses = msg.cc,
                    subject = msg.subject,
                    bodyText = msg.bodyText,
                    bodyHtml = msg.bodyHtml,
                    sentDate = msg.sentDate,
                    receivedDate = msg.receivedDate,
                    isRead = outcome.markRead,
                    hasAttachments = msg.hasAttachments
                )
            )
            // rowId is -1 when the insert was ignored as a duplicate.
            if (rowId > 0) {
                storeAttachments(rowId, msg)
                stored++
            }
        }
        stored
    }

    /** Writes a message's attachment bytes to private storage and records their metadata. */
    private suspend fun storeAttachments(messageRowId: Long, msg: FetchedMessage) {
        if (msg.attachments.isEmpty()) return
        val dir = File(context.filesDir, "attachments/$messageRowId").apply { mkdirs() }
        msg.attachments.forEachIndexed { index, att ->
            runCatching {
                val safeName = att.fileName.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "file" }
                val file = File(dir, "${index}_$safeName")
                file.writeBytes(att.bytes)
                attachmentDao.insert(
                    AttachmentEntity(
                        messageId = messageRowId,
                        fileName = att.fileName,
                        mimeType = att.mimeType,
                        sizeBytes = att.bytes.size.toLong(),
                        filePath = file.absolutePath
                    )
                )
            }
        }
    }

    fun observeAttachments(messageId: Long): Flow<List<AttachmentEntity>> =
        attachmentDao.observeByMessage(messageId)

    /** Syncs the account that owns [folderId]. Convenience for the message-list screen. */
    suspend fun syncFolder(folderId: Long): Result<Int> {
        val folder = folderDao.getById(folderId) ?: return Result.success(0)
        val account = accountDao.getById(folder.accountId) ?: return Result.success(0)
        return syncMessages(account)
    }

    suspend fun markRead(messageId: Long, read: Boolean) = messageDao.setRead(messageId, read)

    suspend fun moveMessage(messageId: Long, accountId: Long, folderName: String) {
        messageDao.moveToFolder(messageId, folderId(accountId, folderName))
    }

    suspend fun deleteMessage(messageId: Long, accountId: Long) {
        // Soft-delete: route to Trash so it can be recovered.
        messageDao.moveToFolder(messageId, folderId(accountId, TRASH))
    }

    suspend fun getMessage(messageId: Long): MessageEntity? = messageDao.getById(messageId)

    /** Sends a message via SMTP and files a copy into the local Sent folder. */
    suspend fun sendMessage(
        account: AccountEntity,
        to: List<String>,
        cc: List<String>,
        bcc: List<String>,
        subject: String,
        htmlBody: String,
        plainBody: String,
        attachments: List<OutgoingAttachment>
    ): Result<Unit> {
        val result = mailClient.sendMessage(
            account, to, cc, bcc, subject, htmlBody, plainBody, attachments
        )
        if (result.isSuccess) {
            val sentId = folderId(account.id, SENT)
            messageDao.insert(
                MessageEntity(
                    accountId = account.id,
                    folderId = sentId,
                    messageId = "sent-${account.id}-${subject.hashCode()}-${to.joinToString().hashCode()}-${plainBody.length}",
                    fromAddress = account.email,
                    toAddresses = to.joinToString(", "),
                    ccAddresses = cc.joinToString(", "),
                    subject = subject,
                    bodyText = plainBody,
                    bodyHtml = htmlBody,
                    sentDate = System.currentTimeMillis(),
                    receivedDate = System.currentTimeMillis(),
                    isRead = true,
                    hasAttachments = attachments.isNotEmpty()
                )
            )
        }
        return result
    }
}
