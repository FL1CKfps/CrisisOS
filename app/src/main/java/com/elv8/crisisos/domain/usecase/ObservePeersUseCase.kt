package com.elv8.crisisos.domain.usecase

import com.elv8.crisisos.data.local.entity.PeerEntity
import com.elv8.crisisos.data.repository.NearbyMeshRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObservePeersUseCase @Inject constructor(
    private val repo: NearbyMeshRepository
) {
    operator fun invoke(): Flow<List<PeerEntity>> = repo.observeActivePeers()
}
