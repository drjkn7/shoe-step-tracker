package io.github.drjkn7.shoestep.data

import io.github.drjkn7.shoestep.core.Clock
import io.github.drjkn7.shoestep.data.db.ShoeDao
import io.github.drjkn7.shoestep.data.db.ShoeEntity
import io.github.drjkn7.shoestep.data.db.ShoeWithStats
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.Flow

sealed interface SaveResult {
    data class Saved(val shoeId: String) : SaveResult
    data class Invalid(val errors: Map<ShoeField, FieldError>) : SaveResult
    data object NotFound : SaveResult
}

/** All shoe reads and writes go through here; every write is validated first. */
class ShoeRepository(
    private val shoeDao: ShoeDao,
    private val clock: Clock,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    fun observeShoes(archived: Boolean = false): Flow<List<ShoeWithStats>> = shoeDao.observeWithStats(archived)

    fun observeShoe(id: String): Flow<ShoeWithStats?> = shoeDao.observeWithStatsById(id)

    suspend fun findShoe(id: String): ShoeEntity? = shoeDao.getById(id)

    suspend fun add(input: ShoeInput): SaveResult {
        val valid = when (val v = ShoeValidator.validate(input, today())) {
            is ShoeValidation.Invalid -> return SaveResult.Invalid(v.errors)
            is ShoeValidation.Valid -> v.shoe
        }
        val id = newId()
        shoeDao.insert(
            ShoeEntity(
                id = id,
                tagUuid = null,
                name = valid.name,
                brand = valid.brand,
                model = valid.model,
                type = valid.type,
                priceMinor = valid.priceMinor,
                currencyCode = valid.currencyCode,
                purchaseEpochDay = valid.purchaseEpochDay,
                accentColor = valid.accentColor,
                archived = false,
                createdAtUtc = clock.nowUtcMillis(),
            ),
        )
        return SaveResult.Saved(id)
    }

    /** Edits user-visible fields only; tag, archive state and creation time are kept. */
    suspend fun update(id: String, input: ShoeInput): SaveResult {
        val valid = when (val v = ShoeValidator.validate(input, today())) {
            is ShoeValidation.Invalid -> return SaveResult.Invalid(v.errors)
            is ShoeValidation.Valid -> v.shoe
        }
        val existing = shoeDao.getById(id) ?: return SaveResult.NotFound
        shoeDao.update(
            existing.copy(
                name = valid.name,
                brand = valid.brand,
                model = valid.model,
                type = valid.type,
                priceMinor = valid.priceMinor,
                currencyCode = valid.currencyCode,
                purchaseEpochDay = valid.purchaseEpochDay,
                accentColor = valid.accentColor,
            ),
        )
        return SaveResult.Saved(id)
    }

    /** Archiving hides a shoe but keeps all its history. */
    suspend fun setArchived(id: String, archived: Boolean): Boolean = shoeDao.setArchived(id, archived) > 0

    /** Permanently deletes the shoe and, by cascade, its sessions and segments. */
    suspend fun delete(id: String): Boolean = shoeDao.delete(id) > 0

    private fun today(): LocalDate = Instant.ofEpochMilli(clock.nowUtcMillis()).atZone(clock.zoneId()).toLocalDate()
}
