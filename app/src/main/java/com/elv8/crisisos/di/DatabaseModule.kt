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

    /**
     * Feature 7 — aggregation tally columns for majority-vote
     * computation. CSV-encoded counts in enum-declaration order so
     * the schema stays cheap (3 TEXT cols) while still letting the
     * repository compute argmax on each incoming report and prevent
     * a single voice from flipping the displayed threat status.
     */
    private val MIGRATION_16_17 = object : Migration(16, 17) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `checkpoints` ADD COLUMN `threatVotes` TEXT NOT NULL DEFAULT '0,0,0'")
            db.execSQL("ALTER TABLE `checkpoints` ADD COLUMN `docsVotes` TEXT NOT NULL DEFAULT '0,0,0,0'")
            db.execSQL("ALTER TABLE `checkpoints` ADD COLUMN `waitVotes` TEXT NOT NULL DEFAULT '0,0,0,0'")
        }
    }

    /**
     * Normalize chat thread request linkage so older installs that created
     * nullable `connectionRequestId` values don't crash when opening/creating
     * direct message threads.
     */
    private val MIGRATION_17_18 = object : Migration(17, 18) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `chat_threads_new` (
                    `threadId` TEXT NOT NULL,
                    `type` TEXT NOT NULL,
                    `peerCrsId` TEXT,
                    `groupId` TEXT,
                    `displayName` TEXT NOT NULL,
                    `avatarColor` INTEGER NOT NULL,
                    `lastMessagePreview` TEXT NOT NULL,
                    `lastMessageAt` INTEGER NOT NULL,
                    `unreadCount` INTEGER NOT NULL,
                    `isPinned` INTEGER NOT NULL,
                    `isMuted` INTEGER NOT NULL,
                    `isMock` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `connectionRequestId` TEXT NOT NULL,
                    PRIMARY KEY(`threadId`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT INTO `chat_threads_new` (
                    `threadId`, `type`, `peerCrsId`, `groupId`, `displayName`,
                    `avatarColor`, `lastMessagePreview`, `lastMessageAt`,
                    `unreadCount`, `isPinned`, `isMuted`, `isMock`,
                    `createdAt`, `connectionRequestId`
                )
                SELECT
                    `threadId`, `type`, `peerCrsId`, `groupId`, `displayName`,
                    `avatarColor`, `lastMessagePreview`, `lastMessageAt`,
                    `unreadCount`, `isPinned`, `isMuted`, `isMock`,
                    `createdAt`, COALESCE(`connectionRequestId`, '')
                FROM `chat_threads`
                """.trimIndent()
            )
            db.execSQL("DROP TABLE `chat_threads`")
            db.execSQL("ALTER TABLE `chat_threads_new` RENAME TO `chat_threads`")
        }
    }

    /**
     * Feature 7 (Checkpoint Threat Intelligence) — adds the four
     * spec-aligned columns to the existing `checkpoints` table.
     * Defaults are chosen to match the safest no-info reading
     * (UNKNOWN threat, NONE docs, UNDER_15M wait, not NGO-verified)
     * so previously-stored rows render coherently on first launch.
     */
    private val MIGRATION_15_16 = object : Migration(15, 16) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `checkpoints` ADD COLUMN `threatLevel` TEXT NOT NULL DEFAULT 'UNKNOWN'")
            db.execSQL("ALTER TABLE `checkpoints` ADD COLUMN `docsRequired` TEXT NOT NULL DEFAULT 'NONE'")
            db.execSQL("ALTER TABLE `checkpoints` ADD COLUMN `waitTime` TEXT NOT NULL DEFAULT 'UNDER_15M'")
            db.execSQL("ALTER TABLE `checkpoints` ADD COLUMN `verifiedByNgo` INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `safe_zones` (
                    `id` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `type` TEXT NOT NULL,
                    `latitude` REAL NOT NULL,
                    `longitude` REAL NOT NULL,
                    `capacity` INTEGER,
                    `currentOccupancy` INTEGER,
                    `isOperational` INTEGER NOT NULL,
                    `operatedBy` TEXT NOT NULL,
                    `lastUpdated` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `deconfliction_reports` (
                    `id` TEXT NOT NULL,
                    `reportType` TEXT NOT NULL,
                    `facilityName` TEXT NOT NULL,
                    `coordinates` TEXT NOT NULL,
                    `protectionStatus` TEXT NOT NULL,
                    `genevaArticle` TEXT NOT NULL,
                    `submittedAt` INTEGER NOT NULL,
                    `broadcastHash` TEXT NOT NULL,
                    `submittedBy` TEXT NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `news_items` (
                    `id` TEXT NOT NULL,
                    `headline` TEXT NOT NULL,
                    `body` TEXT NOT NULL,
                    `category` TEXT NOT NULL,
                    `sourceAlias` TEXT NOT NULL,
                    `sourceCrsId` TEXT NOT NULL,
                    `isOfficial` INTEGER NOT NULL,
                    `publishedAt` INTEGER NOT NULL,
                    `expiresAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `community_posts` (
                    `id` TEXT NOT NULL,
                    `body` TEXT NOT NULL,
                    `category` TEXT NOT NULL,
                    `pinned` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `expiresAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `fake_news_checks` (
                    `id` TEXT NOT NULL,
                    `claimText` TEXT NOT NULL,
                    `verdict` TEXT NOT NULL,
                    `confidenceScore` REAL NOT NULL,
                    `reasoning` TEXT NOT NULL,
                    `sources` TEXT NOT NULL,
                    `checkedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
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
        .addMigrations(
            MIGRATION_1_2,
            MIGRATION_7_8,
            MIGRATION_8_9,
            MIGRATION_11_12,
            MIGRATION_12_13,
            MIGRATION_14_15,
            MIGRATION_15_16,
            MIGRATION_16_17,
            MIGRATION_17_18
        )
        // NOTE (production-readiness): a destructive fallback remains here as
        // a safety net for the v13→v14 gap (no migration was authored at the
        // time v14 shipped). Removing this without first writing
        // MIGRATION_13_14 would crash existing v13 installs on first launch.
        // Tracked as a release blocker for the next non-hackathon build.
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

    @Provides
    @Singleton
    fun provideSafeZoneDao(database: CrisisDatabase): com.elv8.crisisos.data.local.dao.SafeZoneDao = database.safeZoneDao()

    @Provides
    @Singleton
    fun provideDeconflictionDao(database: CrisisDatabase): com.elv8.crisisos.data.local.dao.DeconflictionDao = database.deconflictionDao()

    @Provides
    @Singleton
    fun provideNewsItemDao(database: CrisisDatabase): com.elv8.crisisos.data.local.dao.NewsItemDao = database.newsItemDao()

    @Provides
    @Singleton
    fun provideCommunityPostDao(database: CrisisDatabase): com.elv8.crisisos.data.local.dao.CommunityPostDao = database.communityPostDao()

    @Provides
    @Singleton
    fun provideFakeNewsCheckDao(database: CrisisDatabase): com.elv8.crisisos.data.local.dao.FakeNewsCheckDao = database.fakeNewsCheckDao()

    @Provides
    @Singleton
    fun provideChildRecordDao(database: CrisisDatabase): com.elv8.crisisos.data.local.dao.ChildRecordDao = database.childRecordDao()
}

