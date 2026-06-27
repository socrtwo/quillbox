package info.socrtwo.quillbox.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import info.socrtwo.quillbox.data.local.dao.AccountDao
import info.socrtwo.quillbox.data.local.dao.AttachmentDao
import info.socrtwo.quillbox.data.local.dao.FolderDao
import info.socrtwo.quillbox.data.local.dao.MessageDao
import info.socrtwo.quillbox.data.local.dao.RuleDao
import info.socrtwo.quillbox.data.local.entity.AccountEntity
import info.socrtwo.quillbox.data.local.entity.AttachmentEntity
import info.socrtwo.quillbox.data.local.entity.FolderEntity
import info.socrtwo.quillbox.data.local.entity.MessageEntity
import info.socrtwo.quillbox.data.local.entity.RuleEntity

@Database(
    entities = [
        AccountEntity::class,
        FolderEntity::class,
        MessageEntity::class,
        RuleEntity::class,
        AttachmentEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class QuillboxDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun folderDao(): FolderDao
    abstract fun messageDao(): MessageDao
    abstract fun ruleDao(): RuleDao
    abstract fun attachmentDao(): AttachmentDao

    companion object {
        const val NAME = "quillbox.db"
    }
}
