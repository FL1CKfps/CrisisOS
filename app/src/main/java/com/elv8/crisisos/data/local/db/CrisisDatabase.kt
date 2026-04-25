package com.elv8.crisisos.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.elv8.crisisos.data.local.dao.*
import com.elv8.crisisos.data.local.entity.*

@Database(
    entities = [
        ChatMessageEntity::class,
        ChildRecordEntity::class,
        DeadManSwitchEntity::class,
        MissingPersonEntity::class,
        SupplyRequestEntity::class,
        DangerZoneEntity::class,
        CheckpointEntity::class,
        OutboxMessageEntity::class,
        UserIdentityEntity::class,
        PeerEntity::class,
        ContactEntity::class,
        GroupEntity::class,
        ConnectionRequestEntity::class,
        MessageRequestEntity::class,
        ChatThreadEntity::class,
        NotificationLogEntity::class,
        MediaEntity::class,
        SafeZoneEntity::class,
        DeconflictionReportEntity::class,
        NewsItemEntity::class,
        CommunityPostEntity::class,
        FakeNewsCheckEntity::class
    ],
    version = 15,
    exportSchema = false
)
@TypeConverters(Converters::class, CrisisTypeConverters::class)
abstract class CrisisDatabase : RoomDatabase() {
    abstract fun meshDao(): MeshDao
    abstract fun sosDao(): SosDao
    abstract fun missingPersonDao(): MissingPersonDao
    abstract fun supplyDao(): SupplyDao
    abstract fun deadManDao(): DeadManDao
    abstract fun checkpointDao(): CheckpointDao
    abstract fun dangerZoneDao(): DangerZoneDao
    abstract fun outboxDao(): OutboxDao
    abstract fun chatDao(): ChatDao
    abstract fun userIdentityDao(): UserIdentityDao
    abstract fun peerDao(): com.elv8.crisisos.data.local.dao.PeerDao
    abstract fun contactDao(): com.elv8.crisisos.data.local.dao.ContactDao
    abstract fun groupDao(): com.elv8.crisisos.data.local.dao.GroupDao
    abstract fun connectionRequestDao(): com.elv8.crisisos.data.local.dao.ConnectionRequestDao
    abstract fun messageRequestDao(): com.elv8.crisisos.data.local.dao.MessageRequestDao
    abstract fun chatThreadDao(): com.elv8.crisisos.data.local.dao.ChatThreadDao
    abstract fun notificationLogDao(): com.elv8.crisisos.data.local.dao.NotificationLogDao
    abstract fun mediaDao(): com.elv8.crisisos.data.local.dao.MediaDao
    abstract fun safeZoneDao(): com.elv8.crisisos.data.local.dao.SafeZoneDao
    abstract fun deconflictionDao(): com.elv8.crisisos.data.local.dao.DeconflictionDao
    abstract fun newsItemDao(): com.elv8.crisisos.data.local.dao.NewsItemDao
    abstract fun communityPostDao(): com.elv8.crisisos.data.local.dao.CommunityPostDao
    abstract fun fakeNewsCheckDao(): com.elv8.crisisos.data.local.dao.FakeNewsCheckDao
    abstract fun childRecordDao(): com.elv8.crisisos.data.local.dao.ChildRecordDao
}




