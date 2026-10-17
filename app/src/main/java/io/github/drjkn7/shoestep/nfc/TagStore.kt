package io.github.drjkn7.shoestep.nfc

import androidx.room3.withWriteTransaction
import io.github.drjkn7.shoestep.data.db.ShoeEntity
import io.github.drjkn7.shoestep.data.db.ShoeStepDatabase

/** A tap: what the tag holds, a key for the repeat guard, and a way to write it (null if it can't be). */
class TappedTag(val key: String, val content: TagContent, val handle: TagHandle?)

/** Result of writing (and optionally locking) a tag. */
enum class WriteResult {
    /** Written and read back correctly (and locked, if asked). */
    OK,

    /** Written and read back, but locking failed: the tag still works, it just isn't locked. */
    LOCK_FAILED,

    /** The tag is locked (read-only) and holds something else. */
    READ_ONLY,

    /** Our data doesn't fit on this tag. */
    TOO_SMALL,

    /** The phone moved away, or the tag stopped answering. */
    LOST,

    /** Written, but reading back gave something different. */
    VERIFY_FAILED,
}

/** Writes our format to the tag that was just tapped. The real one is AndroidTagHandle. */
fun interface TagHandle {
    suspend fun write(uuid: String, lock: Boolean): WriteResult
}

sealed interface LinkResult {
    data object Linked : LinkResult

    /** Another pair already uses this tag (T29). */
    data class InUse(val otherShoeName: String) : LinkResult

    data object NotFound : LinkResult
}

/** Shoe/tag storage the tag coordinator needs; an interface so it can be unit-tested. */
interface TagStore {
    suspend fun findShoe(id: String): ShoeEntity?

    suspend fun shoeForTag(uuid: String): ShoeEntity?

    /** Links [uuid] to [shoeId] (replacing any old tag) unless another pair already has it. */
    suspend fun link(shoeId: String, uuid: String): LinkResult

    suspend fun unlink(shoeId: String): Boolean
}

class RoomTagStore(private val db: ShoeStepDatabase) : TagStore {
    private val shoes = db.shoeDao()

    override suspend fun findShoe(id: String) = shoes.getById(id)

    override suspend fun shoeForTag(uuid: String) = shoes.getByTagUuid(uuid)

    override suspend fun link(shoeId: String, uuid: String): LinkResult = db.withWriteTransaction {
        val owner = shoes.getByTagUuid(uuid)
        when {
            owner != null && owner.id != shoeId -> LinkResult.InUse(owner.name)
            shoes.setTagUuid(shoeId, uuid) == 1 -> LinkResult.Linked
            else -> LinkResult.NotFound
        }
    }

    override suspend fun unlink(shoeId: String): Boolean = shoes.setTagUuid(shoeId, null) == 1
}
