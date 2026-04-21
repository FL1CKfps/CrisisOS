package com.elv8.crisisos.domain.usecase

import com.elv8.crisisos.domain.repository.MissingPersonRepository
import com.elv8.crisisos.ui.screens.missingperson.RegisteredPerson
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

class SearchMissingPersonUseCase @Inject constructor(
    private val repository: MissingPersonRepository
) {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
    operator fun invoke(searchFlow: Flow<String>): Flow<List<RegisteredPerson>> {
        // Assume the caller passes in a Flow of search queries (e.g., from a snapshotFlow of state text)
        // Note: For simpler use cases, we might return Flow<List<RegisteredPerson>> from just a String query directly
        return searchFlow
            .debounce(300L)
            .filter { it.length >= 2 }
            .flatMapLatest { query ->
                repository.searchPersons(query)
            }
    }
    
    // Simpler fallback method just in case we call it with a single string repeatedly
    fun search(query: String): Flow<List<RegisteredPerson>> {
        return repository.searchPersons(query)
    }
}