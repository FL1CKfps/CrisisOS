package com.elv8.crisisos.ui.screens.missingperson

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elv8.crisisos.core.event.AppEvent
import com.elv8.crisisos.core.event.EventBus
import com.elv8.crisisos.data.dto.PacketFactory
import com.elv8.crisisos.data.dto.payloads.MissingPersonPayload
import com.elv8.crisisos.data.local.dao.ChildRecordDao
import com.elv8.crisisos.data.remote.mesh.MeshMessenger
import com.elv8.crisisos.domain.model.ChildStatus
import com.elv8.crisisos.domain.repository.IdentityRepository
import com.elv8.crisisos.domain.repository.MissingPersonRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LookupTab { SEARCH, WATCHES }

enum class ResultSource { LOCAL_CACHE, MESH_RESPONSE, NOT_FOUND }

enum class WatchSource { MANUAL, DEPENDENT, SOS_AUTO }

enum class WatchStatus { SEARCHING, LOCATED, REUNITED }

/** A result row displayed under the SEARCH tab. */
data class LookupResult(
    val crsId: String,
    val displayName: String?,
    val lastLocation: String,
    val lastSeenAgo: String,
    val hopsAway: Int,
    val source: ResultSource,
    val isWatched: Boolean = false
)

/** A row displayed under the WATCHES tab. */
data class WatchEntry(
    val crsId: String,
    val displayName: String?,
    val source: WatchSource,
    val status: WatchStatus,
    val lastLocation: String?,
    val lastUpdate: String,
    val note: String? = null
)

data class MissingPersonUiState(
    val tab: LookupTab = LookupTab.SEARCH,
    val crsIdQuery: String = "",
    val crsIdError: String? = null,
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false,
    val searchResults: List<LookupResult> = emptyList(),
    val watches: List<WatchEntry> = emptyList(),
    val myCrsId: String = "",
    val infoBanner: String? = null
)

/**
 * Unified CRS-ID lookup viewmodel. Replaces the old "register / search" Missing Person flow
 * and absorbs the ChildAlert "watches" surface so users have a single screen for finding
 * other people on the mesh and tracking dependents.
 */
@HiltViewModel
class MissingPersonViewModel @Inject constructor(
    private val missingPersonRepository: MissingPersonRepository,
    private val identityRepository: IdentityRepository,
    private val childRecordDao: ChildRecordDao,
    private val messenger: MeshMessenger,
    private val eventBus: EventBus
) : ViewModel() {

    companion object {
        private val CRS_ID_REGEX = Regex("^[A-Z]{2,4}-\\d{6,8}$")
        private const val SEARCH_TIMEOUT_MS = 4000L
    }

    private val _uiState = MutableStateFlow(MissingPersonUiState())
    val uiState: StateFlow<MissingPersonUiState> = _uiState.asStateFlow()

    private var pendingSearchJob: Job? = null

    init {
        // Resolve own CRS ID for query packets and hydrate watch list from any
        // dependents this user has registered (real ChildRecord rows in Room).
        viewModelScope.launch {
            val identity = identityRepository.getIdentity().first()
            val crsId = identity?.crsId ?: "local_device"
            _uiState.update { it.copy(myCrsId = crsId) }

            childRecordDao.getByGuardian(crsId).collect { children ->
                children.forEach { child ->
                    val status = runCatching { ChildStatus.valueOf(child.status) }
                        .getOrDefault(ChildStatus.SEARCHING)
                    addOrUpdateWatch(
                        WatchEntry(
                            crsId = child.crsChildId,
                            displayName = child.childName,
                            source = WatchSource.DEPENDENT,
                            status = when (status) {
                                ChildStatus.LOCATED, ChildStatus.REUNITED -> WatchStatus.LOCATED
                                ChildStatus.SEARCHING -> WatchStatus.SEARCHING
                            },
                            lastLocation = child.lastKnownLocation.takeUnless { it.isBlank() },
                            lastUpdate = formatRelative(child.registeredAt),
                            note = "Registered dependent (auto-watched)"
                        )
                    )
                }
            }
        }

        // Mesh responses → fold back into the active search.
        viewModelScope.launch {
            eventBus.events
                .filterIsInstance<AppEvent.MissingPersonEvent.ResponseReceived>()
                .collect { event ->
                    handleMeshResponse(event)
                }
        }

        // Child alerts on the wire → automatically watch the child CRS ID.
        viewModelScope.launch {
            eventBus.events
                .filterIsInstance<AppEvent.ChildAlertEvent.AlertBroadcast>()
                .collect { event ->
                    addOrUpdateWatch(
                        WatchEntry(
                            crsId = event.childId,
                            displayName = event.name,
                            source = WatchSource.DEPENDENT,
                            status = WatchStatus.SEARCHING,
                            lastLocation = event.location,
                            lastUpdate = "Just now",
                            note = "Auto-added from Child Alert"
                        )
                    )
                }
        }

        // Own SOS broadcasts → auto-watch own CRS so other devices searching land on us.
        viewModelScope.launch {
            eventBus.events
                .filterIsInstance<AppEvent.SosEvent.SosBroadcastStarted>()
                .collect {
                    val myId = _uiState.value.myCrsId
                    if (myId.isNotBlank()) {
                        addOrUpdateWatch(
                            WatchEntry(
                                crsId = myId,
                                displayName = "You",
                                source = WatchSource.SOS_AUTO,
                                status = WatchStatus.SEARCHING,
                                lastLocation = null,
                                lastUpdate = "Just now",
                                note = "Auto-added when you broadcast SOS"
                            )
                        )
                    }
                }
        }
    }

    // ── Tab + input ────────────────────────────────────────────────────────────

    fun selectTab(tab: LookupTab) {
        _uiState.update { it.copy(tab = tab) }
    }

    fun updateCrsIdQuery(value: String) {
        // Accept lowercase input but normalize for display + search.
        val normalized = value.uppercase().filter { it.isLetterOrDigit() || it == '-' }
        _uiState.update { it.copy(crsIdQuery = normalized, crsIdError = null) }
    }

    fun submitSearch() {
        val state = _uiState.value
        val query = state.crsIdQuery.trim()
        if (query.isBlank()) {
            _uiState.update { it.copy(crsIdError = "Enter a CRS ID to search.") }
            return
        }
        if (!CRS_ID_REGEX.matches(query)) {
            _uiState.update { it.copy(crsIdError = "CRS IDs look like ABCD-150307.") }
            return
        }

        _uiState.update {
            it.copy(
                isSearching = true,
                hasSearched = true,
                searchResults = emptyList(),
                crsIdError = null,
                infoBanner = null
            )
        }

        pendingSearchJob?.cancel()
        pendingSearchJob = viewModelScope.launch {
            // Local cache lookup. The DAO's name-LIKE search isn't useful for CRS-ID queries,
            // so we just walk the registered persons and filter by CRS ID exactly.
            val localHits = missingPersonRepository.getRegisteredPersons().first()
            val localResults = localHits
                .filter { it.crsId.equals(query, ignoreCase = true) }
                .map { p ->
                    LookupResult(
                        crsId = p.crsId,
                        displayName = p.name.ifBlank { null },
                        lastLocation = p.lastKnownLocation,
                        lastSeenAgo = p.registeredAt,
                        hopsAway = 0,
                        source = ResultSource.LOCAL_CACHE,
                        isWatched = isWatched(p.crsId)
                    )
                }

            _uiState.update { it.copy(searchResults = localResults) }

            // Broadcast a mesh query so remote nodes can respond. Local senderId is best-effort.
            broadcastMeshQuery(query)

            // After timeout, if nothing came back, surface the "not found" guidance.
            delay(SEARCH_TIMEOUT_MS)
            val current = _uiState.value
            if (current.crsIdQuery == state.crsIdQuery && current.searchResults.isEmpty()) {
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        searchResults = listOf(
                            LookupResult(
                                crsId = query,
                                displayName = null,
                                lastLocation = "—",
                                lastSeenAgo = "No replies yet",
                                hopsAway = -1,
                                source = ResultSource.NOT_FOUND,
                                isWatched = isWatched(query)
                            )
                        )
                    )
                }
            } else {
                _uiState.update { it.copy(isSearching = false) }
            }
        }
    }

    fun clearSearch() {
        pendingSearchJob?.cancel()
        _uiState.update {
            it.copy(
                crsIdQuery = "",
                searchResults = emptyList(),
                hasSearched = false,
                isSearching = false,
                crsIdError = null
            )
        }
    }

    // ── Watch list mutations ───────────────────────────────────────────────────

    fun addCurrentToWatches() {
        val state = _uiState.value
        val crsId = state.crsIdQuery.trim()
        if (crsId.isBlank() || !CRS_ID_REGEX.matches(crsId)) return
        addOrUpdateWatch(
            WatchEntry(
                crsId = crsId,
                displayName = state.searchResults.firstOrNull { it.crsId == crsId }?.displayName,
                source = WatchSource.MANUAL,
                status = WatchStatus.SEARCHING,
                lastLocation = state.searchResults.firstOrNull { it.crsId == crsId }?.lastLocation,
                lastUpdate = "Just now"
            )
        )
        _uiState.update {
            it.copy(
                infoBanner = "$crsId added to your watch list. You'll be notified when the mesh sees them.",
                searchResults = it.searchResults.map { r -> if (r.crsId == crsId) r.copy(isWatched = true) else r }
            )
        }
    }

    fun watchResult(result: LookupResult) {
        addOrUpdateWatch(
            WatchEntry(
                crsId = result.crsId,
                displayName = result.displayName,
                source = WatchSource.MANUAL,
                status = if (result.source == ResultSource.NOT_FOUND) WatchStatus.SEARCHING else WatchStatus.LOCATED,
                lastLocation = result.lastLocation.takeUnless { it == "—" },
                lastUpdate = "Just now"
            )
        )
        _uiState.update { state ->
            state.copy(
                searchResults = state.searchResults.map { r ->
                    if (r.crsId == result.crsId) r.copy(isWatched = true) else r
                }
            )
        }
    }

    fun removeWatch(crsId: String) {
        _uiState.update {
            it.copy(watches = it.watches.filterNot { w -> w.crsId == crsId })
        }
    }

    fun markReunited(crsId: String) {
        _uiState.update {
            it.copy(
                watches = it.watches.map { w ->
                    if (w.crsId == crsId) w.copy(status = WatchStatus.REUNITED, lastUpdate = "Just now") else w
                }
            )
        }
    }

    fun dismissBanner() {
        _uiState.update { it.copy(infoBanner = null) }
    }

    // ── Internals ──────────────────────────────────────────────────────────────

    private fun handleMeshResponse(event: AppEvent.MissingPersonEvent.ResponseReceived) {
        val current = _uiState.value
        // Drop the synthetic NOT_FOUND row (if any) before adding a real hit.
        val dedup = current.searchResults.filterNot { it.source == ResultSource.NOT_FOUND }
        // Preserve any display name we've already learned (from local cache hit or watch list)
        // so the freshly-arrived mesh response doesn't blank it out.
        val knownName = dedup.firstOrNull { it.crsId == event.crsId }?.displayName
            ?: current.watches.firstOrNull { it.crsId == event.crsId }?.displayName
        val incoming = LookupResult(
            crsId = event.crsId,
            displayName = knownName,
            lastLocation = event.lastLocation,
            lastSeenAgo = "Just now",
            hopsAway = event.hopsAway,
            source = ResultSource.MESH_RESPONSE,
            isWatched = isWatched(event.crsId)
        )
        val combined = if (dedup.any { it.crsId == event.crsId && it.source == ResultSource.MESH_RESPONSE }) {
            dedup.map { if (it.crsId == event.crsId) incoming else it }
        } else {
            dedup + incoming
        }
        _uiState.update { it.copy(searchResults = combined) }
        // If this CRS ID is on a watch list, fold the location update in.
        if (isWatched(event.crsId)) {
            _uiState.update { state ->
                state.copy(
                    watches = state.watches.map { w ->
                        if (w.crsId == event.crsId) {
                            w.copy(
                                status = WatchStatus.LOCATED,
                                lastLocation = event.lastLocation,
                                lastUpdate = "Just now"
                            )
                        } else w
                    }
                )
            }
        }
    }

    private fun broadcastMeshQuery(crsId: String) {
        val payload = MissingPersonPayload(
            queryType = "SEARCH",
            crsId = crsId,
            name = "",
            age = null,
            description = null,
            lastLocation = null
        )
        val state = _uiState.value
        val packet = PacketFactory.buildMissingPersonQueryPacket(
            senderId = state.myCrsId.ifBlank { "local_device" },
            senderAlias = state.myCrsId.ifBlank { "Local User" },
            payload = payload
        )
        viewModelScope.launch { messenger.send(packet) }
    }

    private fun addOrUpdateWatch(entry: WatchEntry) {
        _uiState.update { state ->
            val existing = state.watches.indexOfFirst { it.crsId == entry.crsId }
            val newList = if (existing >= 0) {
                state.watches.toMutableList().also { it[existing] = entry }
            } else {
                state.watches + entry
            }
            state.copy(watches = newList)
        }
    }

    private fun isWatched(crsId: String): Boolean =
        _uiState.value.watches.any { it.crsId.equals(crsId, ignoreCase = true) }

    private fun formatRelative(ts: Long): String {
        val mins = (System.currentTimeMillis() - ts) / 60_000L
        return when {
            mins < 1   -> "Just now"
            mins < 60  -> "${mins}m ago"
            mins < 1440 -> "${mins / 60}h ago"
            else -> "${mins / 1440}d ago"
        }
    }
}
