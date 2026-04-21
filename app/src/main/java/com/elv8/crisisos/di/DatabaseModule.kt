package com.elv8.crisisos.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.elv8.crisisos.data.local.dao.*
import com.elv8.crisisos.data.local.db.CrisisDatabase
import dagger.Module
import dagger.Provides
import com.elv8.crisisos.data.local.dao.PeerDao
import com.elv8.crisisos.data.local.dao.ContactDao
import com.elv8.crisisos.data.local.dao.GroupDao
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `outbox_messages` (
                    `id` TEXT NOT NULL,
                    `packetJson` TEXT NOT NULL,
                    `packetType` TEXT NOT NULL,
                    `priority` INTEGER NOT NULL,
                    `targetId` TEXT,
                    `createdAt` INTEGER NOT NULL,
                    `scheduledAt` INTEGER NOT NULL,
                    `lastAttemptAt` INTEGER,
                    `attemptCount` INTEGER NOT NULL,
                    `maxAttempts` INTEGER NOT NULL,
                    `ttlExpiry` INTEGER NOT NULL,
                    `status` TEXT NOT NULL,
                    `failureReason` TEXT,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
        }
    }

    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `connection_requests` (`requestId` TEXT NOT NULL, `fromCrsId` TEXT NOT NULL, `fromAlias` TEXT NOT NULL, `fromAvatarColor` INTEGER NOT NULL, `toCrsId` TEXT NOT NULL, `message` TEXT NOT NULL, `sentAt` INTEGER NOT NULL, `respondedAt` INTEGER, `status` TEXT NOT NULL, `direction` TEXT NOT NULL, `expiresAt` INTEGER NOT NULL, PRIMARY KEY(`requestId`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `message_requests` (`requestId` TEXT NOT NULL, `fromCrsId` TEXT NOT NULL, `fromAlias` TEXT NOT NULL, `fromAvatarColor` INTEGER NOT NULL, `previewText` TEXT NOT NULL, `fullMessageJson` TEXT NOT NULL, `sentAt` INTEGER NOT NULL, `status` TEXT NOT NULL, `threadId` TEXT, PRIMARY KEY(`requestId`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `chat_threads` (`threadId` TEXT NOT NULL, `type` TEXT NOT NULL, `peerCrsId` TEXT, `groupId` TEXT, `displayName` TEXT NOT NULL, `avatarColor` INTEGER NOT NULL, `lastMessagePreview` TEXT NOT NULL, `lastMessageAt` INTEGER NOT NULL, `unreadCount` INTEGER NOT NULL, `isPinned` INTEGER NOT NULL, `isMuted` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `connectionRequestId` TEXT NOT NULL, PRIMARY KEY(`threadId`))")
        }
    }

    private val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `chat_messages` ADD COLUMN `threadId` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `chat_messages` ADD COLUMN `replyToMessageId` TEXT")
            db.execSQL("ALTER TABLE `chat_messages` ADD COLUMN `fromCrsId` TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE `chat_messages` ADD COLUMN `fromAlias` TEXT NOT NULL DEFAULT ''")
        }
    }

    private val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `media_items` (
                    `mediaId` TEXT NOT NULL,
                    `threadId` TEXT NOT NULL,
                    `senderCrsId` TEXT NOT NULL,
                    `receiverCrsId` TEXT,
                    `type` TEXT NOT NULL,
                    `localUri` TEXT,
                    `remoteUri` TEXT,
                    `fileName` TEXT NOT NULL,
                    `mimeType` TEXT NOT NULL,
                    `fileSizeBytes` INTEGER NOT NULL,
                    `compressedSizeBytes` INTEGER,
                    `durationMs` INTEGER,
                    `thumbnailUri` TEXT,
                    `timestamp` INTEGER NOT NULL,
                    `status` TEXT NOT NULL,
                    `isOwn` INTEGER NOT NULL,
                    `messageId` TEXT,
                    `chunkCount` INTEGER NOT NULL,
                    `chunksReceived` INTEGER NOT NULL,
                    `transferId` TEXT,
                    PRIMARY KEY(`mediaId`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_items_threadId` ON `media_items` (`threadId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_items_timestamp` ON `media_items` (`timestamp`)")

            db.execSQL("ALTER TABLE `chat_messages` ADD COLUMN `mediaId` TEXT")
            db.execSQL("ALTER TABLE `chat_messages` ADD COLUMN `mediaThumbnailUri` TEXT")
            db.execSQL("ALTER TABLE `chat_messages` ADD COLUMN `mediaDurationMs` INTEGER")
        }
    }

    private val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `chat_threads` ADD COLUMN `isMock` INTEGER NOT NULL DEFAULT 0")
        }
    }


    @Provides
    @Singleton
    fun provideCrisisDatabase(@ApplicationContext context: Context): CrisisDatabase {
        return Room.databaseBuilder(
            context,
            CrisisDatabase::class.java,
            "crisis_database"
        )
        .setJournalMode(androidx.room.RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
        .addMigrations(MIGRATION_1_2, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_11_12, MIGRATION_12_13)
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    @Singleton
    fun provideOutboxDao(database: CrisisDatabase): OutboxDao = database.outboxDao()

    @Provides
    @Singleton
    fun provideMeshDao(database: CrisisDatabase): MeshDao = database.meshDao()

    @Provides
    @Singleton
    fun provideSosDao(database: CrisisDatabase): SosDao = database.sosDao()

    @Provides
    @Singleton
    fun provideMissingPersonDao(database: CrisisDatabase): MissingPersonDao = database.missingPersonDao()

    @Provides
    @Singleton
    fun provideSupplyDao(database: CrisisDatabase): SupplyDao = database.supplyDao()

    @Provides
    @Singleton
    fun provideDeadManDao(database: CrisisDatabase): DeadManDao = database.deadManDao()

    @Provides
    @Singleton
    fun provideCheckpointDao(database: CrisisDatabase): CheckpointDao = database.checkpointDao()

    @Provides
    @Singleton
    fun provideDangerZoneDao(database: CrisisDatabase): DangerZoneDao = database.dangerZoneDao()

        @Provides
    @Singleton
    fun provideChatDao(database: CrisisDatabase): ChatDao = database.chatDao()

    @Provides
    @Singleton
    fun provideUserIdentityDao(database: CrisisDatabase): UserIdentityDao = database.userIdentityDao()

    @Provides
    @Singleton
    fun providePeerDao(database: CrisisDatabase): com.elv8.crisisos.data.local.dao.PeerDao = database.peerDao()

    @Provides
    @Singleton
    fun provideContactDao(database: CrisisDatabase): com.elv8.crisisos.data.local.dao.ContactDao = database.contactDao()

    @Provides
    @Singleton
    fun provideGroupDao(database: CrisisDatabase): com.elv8.crisisos.data.local.dao.GroupDao = database.groupDao()

    @Provides
    @Singleton
    fun provideConnectionRequestDao(database: CrisisDatabase): com.elv8.crisisos.data.local.dao.ConnectionRequestDao = database.connectionRequestDao()

    @Provides
    @Singleton
    fun provideMessageRequestDao(database: CrisisDatabase): com.elv8.crisisos.data.local.dao.MessageRequestDao = database.messageRequestDao()

    @Provides
    @Singleton
    fun provideChatThreadDao(database: CrisisDatabase): com.elv8.crisisos.data.local.dao.ChatThreadDao = database.chatThreadDao()

    @Provides
    @Singleton
    fun provideMediaDao(database: CrisisDatabase): com.elv8.crisisos.data.local.dao.MediaDao = database.mediaDao()



    @Provides
    @Singleton
    fun provideNotificationLogDao(database: CrisisDatabase): com.elv8.crisisos.data.local.dao.NotificationLogDao = database.notificationLogDao()

}

