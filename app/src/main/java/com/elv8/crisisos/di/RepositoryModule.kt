package com.elv8.crisisos.di

import com.elv8.crisisos.data.repository.*
import com.elv8.crisisos.domain.repository.*
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindOutboxRepository(
        outboxRepositoryImpl: OutboxRepositoryImpl
    ): OutboxRepository

    @Binds
    @Singleton
    abstract fun bindMeshRepository(
        chatRepositoryImpl: ChatRepositoryImpl
    ): MeshRepository

    @Binds
    @Singleton
    abstract fun bindSosRepository(
        sosRepositoryImpl: SosRepositoryImpl
    ): SosRepository

    @Binds
    @Singleton
    abstract fun bindMissingPersonRepository(
        missingPersonRepositoryImpl: MissingPersonRepositoryImpl
    ): MissingPersonRepository

    @Binds
    @Singleton
    abstract fun bindSupplyRepository(
        supplyRepositoryImpl: SupplyRepositoryImpl
    ): SupplyRepository

    @Binds
    @Singleton
    abstract fun bindDeadManRepository(
        deadManRepositoryImpl: DeadManRepositoryImpl
    ): DeadManRepository

    @Binds
    @Singleton
    abstract fun bindCheckpointRepository(
        checkpointRepositoryImpl: com.elv8.crisisos.data.repository.CheckpointRepositoryImpl
    ): com.elv8.crisisos.domain.repository.CheckpointRepository

    @Binds
    @Singleton
    abstract fun bindDangerZoneRepository(
        dangerZoneRepositoryImpl: DangerZoneRepositoryImpl
    ): DangerZoneRepository

    @Binds
    @Singleton
    abstract fun bindLocationRepository(
        locationRepositoryImpl: LocationRepositoryImpl
    ): LocationRepository

    @Binds
    @Singleton
    abstract fun bindIdentityRepository(
        identityRepositoryImpl: IdentityRepositoryImpl
    ): IdentityRepository

    @Binds
    @Singleton
    abstract fun bindPeerRepository(
        peerRepositoryImpl: PeerRepositoryImpl
    ): PeerRepository

    @Binds
    @Singleton
    abstract fun bindConnectionRequestRepository(
        connectionRequestRepositoryImpl: ConnectionRequestRepositoryImpl
    ): ConnectionRequestRepository

    @Binds
    @Singleton
    abstract fun bindContactRepository(
        contactRepositoryImpl: ContactRepositoryImpl
    ): ContactRepository

    @Binds
    @Singleton
    abstract fun bindGroupRepository(
        groupRepositoryImpl: GroupRepositoryImpl
    ): GroupRepository

    @Binds
    @Singleton
    abstract fun bindThreadChatRepository(
        threadChatRepositoryImpl: ThreadChatRepositoryImpl
    ): ThreadChatRepository

    @Binds
    @Singleton
    abstract fun bindMessageRequestRepository(
        messageRequestRepositoryImpl: MessageRequestRepositoryImpl
    ): MessageRequestRepository

}
