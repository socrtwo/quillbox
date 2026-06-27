package info.socrtwo.quillbox.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import info.socrtwo.quillbox.data.local.QuillboxDatabase
import info.socrtwo.quillbox.data.local.dao.AccountDao
import info.socrtwo.quillbox.data.local.dao.AttachmentDao
import info.socrtwo.quillbox.data.local.dao.FolderDao
import info.socrtwo.quillbox.data.local.dao.MessageDao
import info.socrtwo.quillbox.data.local.dao.RuleDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): QuillboxDatabase =
        Room.databaseBuilder(context, QuillboxDatabase::class.java, QuillboxDatabase.NAME)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    // Seed a default Spam rule so the rules engine has a working example
                    // out of the box (sender/subject keyword -> move to Spam).
                    db.execSQL(
                        """
                        INSERT INTO rules (name, enabled, logic, criteria, actionType, targetFolder, priority)
                        VALUES (
                            'Default Spam Filter',
                            1,
                            'OR',
                            '[{"field":"SUBJECT","value":"viagra"},{"field":"SUBJECT","value":"lottery"},{"field":"BODY","value":"you have won"},{"field":"SENDER","value":"no-reply@spam"}]',
                            'MOVE_TO_FOLDER',
                            'Spam',
                            10
                        )
                        """.trimIndent()
                    )
                }
            })
            // The attachments table was added in v2. Recreate the local cache on schema
            // change rather than ship a hand-written migration; accounts/rules are quick to
            // re-add and mail can simply be re-synced from the server.
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideAccountDao(db: QuillboxDatabase): AccountDao = db.accountDao()
    @Provides fun provideFolderDao(db: QuillboxDatabase): FolderDao = db.folderDao()
    @Provides fun provideMessageDao(db: QuillboxDatabase): MessageDao = db.messageDao()
    @Provides fun provideRuleDao(db: QuillboxDatabase): RuleDao = db.ruleDao()
    @Provides fun provideAttachmentDao(db: QuillboxDatabase): AttachmentDao = db.attachmentDao()
}
