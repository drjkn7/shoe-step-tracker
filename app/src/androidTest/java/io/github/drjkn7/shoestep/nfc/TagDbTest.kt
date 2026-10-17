package io.github.drjkn7.shoestep.nfc

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.drjkn7.shoestep.data.db.ShoeEntity
import io.github.drjkn7.shoestep.data.db.ShoeStepDatabase
import io.github.drjkn7.shoestep.data.model.ShoeType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** RoomTagStore on a real (in-memory) database: one tag, one pair (T29). */
@RunWith(AndroidJUnit4::class)
class TagDbTest {

    private lateinit var db: ShoeStepDatabase
    private lateinit var store: RoomTagStore

    private val uuid1 = "11111111-1111-4111-8111-111111111111"
    private val uuid2 = "22222222-2222-4222-8222-222222222222"

    @Before fun setUp() = runTest {
        db = ShoeStepDatabase.buildInMemory(ApplicationProvider.getApplicationContext())
        store = RoomTagStore(db)
        listOf("a", "b").forEach {
            db.shoeDao().insert(
                ShoeEntity(
                    id = it, tagUuid = null, name = "Shoe $it", brand = "", model = "", type = ShoeType.WALKING,
                    priceMinor = null, currencyCode = "EUR", purchaseEpochDay = null, accentColor = 0,
                    archived = false, createdAtUtc = 0,
                ),
            )
        }
    }

    @After fun tearDown() = db.close()

    @Test fun aTagBelongsToOnePairOnly() = runTest {
        assertEquals(LinkResult.Linked, store.link("a", uuid1))
        assertEquals("a", store.shoeForTag(uuid1)?.id)
        assertEquals(LinkResult.InUse("Shoe a"), store.link("b", uuid1))
        assertNull(store.findShoe("b")?.tagUuid)
        assertEquals("re-linking the same pair is fine", LinkResult.Linked, store.link("a", uuid1))
    }

    @Test fun replacingAndUnlinking() = runTest {
        store.link("a", uuid1)
        assertEquals(LinkResult.Linked, store.link("a", uuid2))
        assertNull("the old tag is free again", store.shoeForTag(uuid1))
        assertEquals(LinkResult.Linked, store.link("b", uuid1))

        assertTrue(store.unlink("a"))
        assertNull(store.shoeForTag(uuid2))
        assertFalse(store.unlink("missing"))
        assertEquals(LinkResult.NotFound, store.link("missing", uuid2))
    }
}
