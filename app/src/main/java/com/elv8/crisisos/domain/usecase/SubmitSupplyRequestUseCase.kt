package com.elv8.crisisos.domain.usecase

import com.elv8.crisisos.domain.model.SupplyRequest
import com.elv8.crisisos.domain.repository.SupplyRepository
import javax.inject.Inject

class SubmitSupplyRequestUseCase @Inject constructor(
    private val supplyRepository: SupplyRepository
) {
    suspend operator fun invoke(request: SupplyRequest): Boolean {
        // Validate fields
        if (request.location.isBlank() || request.quantity <= 0) {
            return false
        }

        supplyRepository.submitRequest(request)
        return true
    }
}