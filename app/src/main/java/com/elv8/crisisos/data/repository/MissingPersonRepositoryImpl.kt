package com.elv8.crisisos.data.repository

import com.elv8.crisisos.core.event.AppEvent
import com.elv8.crisisos.core.event.EventBus
import com.elv8.crisisos.data.dto.PacketFactory
import com.elv8.crisisos.data.dto.payloads.MissingPersonPayload
import com.elv8.crisisos.data.local.dao.MissingPersonDao
import com.elv8.crisisos.data.local.entity.MissingPersonEntity
import com.elv8.crisisos.data.mesh.MeshMessenger
import com.elv8.crisisos.domain.repository.MissingPersonRepository
import com.elv8.crisisos.ui.screens.missingperson.RegisteredPerson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import javax.inject.Inject

class MissingPersonRepositoryImpl @Inject constructor(
    private val missingPersonDao: MissingPersonDao,
    private val messenger: MeshMessenger,
    private val eventBus: EventBus,
    private val notificationBus: com.elv8.crisisos.core.notification.NotificationEventBus,
    private val scope: kotlinx.coroutines.CoroutineScope
) : MissingPersonRepository {

    override fun getRegisteredPersons(): Flow<List<RegisteredPerson>> {
        return missingPersonDao.getAllMissingPersons().map { entities ->        
            entities.map { it.toDomainModel() }
        }
    }

    override fun searchPersons(query: String): Flow<List<RegisteredPerson>> {   
        val payload = MissingPersonPayload(
            queryType = "SEARCH",
            crsId = "",
            name = query,
            age = null,
            description = null,
            lastLocation = null
        )
        // Hardcoded identity as sender for now, or you could get it from messenger if supported
        val packet = PacketFactory.buildMissingPersonQueryPacket("local_device", "Local User", payload)
        kotlinx.coroutines.GlobalScope.launch {
            messenger.send(packet)
        }
        
        return missingPersonDao.searchPersons(query).map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    override suspend fun registerPerson(person: RegisteredPerson) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            missingPersonDao.insertPerson(person.toEntity())
        }
        
        val payload = MissingPersonPayload(
            queryType = "REGISTER",
            crsId = person.crsId,
            name = person.name,
            age = person.age.toIntOrNull(),
            description = person.photoDescription,
            lastLocation = person.lastKnownLocation
        )
        
        val packet = PacketFactory.buildMissingPersonQueryPacket("local_device", "Local User", payload)
        messenger.send(packet)
    }

    override fun observeIncomingPersonData(): Flow<Unit> = flow {
        eventBus.events.filterIsInstance<AppEvent.MissingPersonEvent>().collect { event ->
            when (event) {
                is AppEvent.MissingPersonEvent.QueryBroadcast -> {
                    if (event.queryType == "REGISTER") {
                        // insert to Room if not exists
                        val entity = MissingPersonEntity(
                            crsId = event.crsId,
                            name = event.name,
                            age = event.age?.toString() ?: "",
                            photoDescription = event.description ?: "",
                            lastKnownLocation = event.lastLocation ?: "Unknown",
                            registeredAt = System.currentTimeMillis().toString()
                        )
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            missingPersonDao.insertPerson(entity)
                        }
                    } else if (event.queryType == "SEARCH") {
                        // search local DB, if found -> send RESPONSE
                        val localMatches = missingPersonDao.searchPersonsDirect(event.name)
                        if (localMatches.isNotEmpty()) {
                            val match = localMatches.first()
                            // Send response packet
                            val packet = PacketFactory.buildMissingPersonResponsePacket(
                                senderId = "local_device",
                                senderAlias = "Local User",
                                targetId = event.senderId,
                                crsId = match.crsId,
                                lastLocation = match.lastKnownLocation,
                                hopsAway = 1
                            )
                            messenger.send(packet)
                        }
                    }
                }
                is AppEvent.MissingPersonEvent.ResponseReceived -> {
                    val localMatches = missingPersonDao.searchPersonsDirect(event.crsId) // or find by id if available
                    // Assuming searchPersonsDirect matches name/crsId, let's just query dao
                    // Actually we need to check local DB. Since dao might not have `getByCrsId`, we can just get all and filter
                    val allPersons = missingPersonDao.getAllMissingPersons().first()
                    val foundPerson = allPersons.find { it.crsId == event.crsId }

                    if (foundPerson != null) {
                        scope.launch {
                            notificationBus.emitMissingPerson(
                                com.elv8.crisisos.core.notification.event.NotificationEvent.MissingPerson.SearchResponseReceived(
                                    queryCrsId = event.crsId,
                                    name = foundPerson.name,
                                    locationFound = event.lastLocation
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

// Mappers
fun MissingPersonEntity.toDomainModel(): RegisteredPerson {
    return RegisteredPerson(
        crsId = crsId,
        name = name,
        age = age,
        photoDescription = photoDescription,
        lastKnownLocation = lastKnownLocation,
        registeredAt = registeredAt
    )
}

fun RegisteredPerson.toEntity(): MissingPersonEntity {
    return MissingPersonEntity(
        crsId = crsId,
        name = name,
        age = age,
        photoDescription = photoDescription,
        lastKnownLocation = lastKnownLocation,
        registeredAt = registeredAt
    )
}
