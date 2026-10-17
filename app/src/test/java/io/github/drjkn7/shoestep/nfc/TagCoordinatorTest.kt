package io.github.drjkn7.shoestep.nfc

import io.github.drjkn7.shoestep.core.Debouncer
import io.github.drjkn7.shoestep.core.FakeClock
import io.github.drjkn7.shoestep.data.db.ShoeEntity
import io.github.drjkn7.shoestep.data.model.SessionState
import io.github.drjkn7.shoestep.session.FakeSessionStore
import io.github.drjkn7.shoestep.session.SessionManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What a tap does (T06, T07, T11–T14, T29) and how linking behaves (T04, T05). */
class TagCoordinatorTest {

    private val clock = FakeClock()
    private val sessionStore = FakeSessionStore().apply {
        addShoe("A")
        addShoe("B")
        addShoe("Z", archived = true)
    }
    private val tagStore = FakeTagStore(sessionStore.shoes)
    private val sessions = SessionManager(sessionStore, clock)
    private val tags = TagCoordinator(sessions, tagStore, Debouncer(clock))

    private val uuidA = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
    private val uuidB = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"
    private val uuidZ = "cccccccc-cccc-4ccc-8ccc-cccccccccccc"

    init {
        tagStore.setTag("A", uuidA)
        tagStore.setTag("B", uuidB)
        tagStore.setTag("Z", uuidZ)
    }

    /** Taps a tag after the phone has been away long enough for the repeat guard. */
    private suspend fun tap(content: TagContent, key: String = keyOf(content), handle: TagHandle? = FakeHandle()) {
        clock.advanceBy(3_000)
        tags.onTag(TappedTag(key, content, handle))
    }

    private fun keyOf(content: TagContent) = (content as? TagContent.Ours)?.uuid ?: "hw-1"

    private suspend fun active() = sessions.activeSession.first()

    private suspend fun TestScope.nextEvent(): TagEvent? = withTimeoutOrNull(100) { tags.events.first() }

    /** Every navigation event sent so far, in order. */
    private suspend fun TestScope.allEvents(): List<TagEvent> = buildList {
        while (true) add(nextEvent() ?: break)
    }

    // --- taps -----------------------------------------------------------------------------------

    @Test fun tapOnIdlePairStartsItAndOpensItWithABuzz() = runTest {
        tap(TagContent.Ours(uuidA))
        assertEquals("A", active()?.session?.shoeId)
        assertEquals(SessionState.RUNNING, active()?.session?.state)
        assertEquals(TagEvent.OpenShoe("A", confirm = true), nextEvent())
        assertNull(tags.prompt.value)
    }

    @Test fun secondTapAsksPauseEndOrCancel() = runTest {
        tap(TagContent.Ours(uuidA))
        val id = active()!!.session.id
        tap(TagContent.Ours(uuidA))
        assertEquals(TagPrompt.RunningTap(id, "Shoe A"), tags.prompt.value)

        tags.dismissPrompt() // Cancel changes nothing
        assertNull(tags.prompt.value)
        assertEquals(SessionState.RUNNING, active()?.session?.state)

        tap(TagContent.Ours(uuidA))
        tags.pause()
        assertEquals(SessionState.PAUSED, active()?.session?.state)

        tap(TagContent.Ours(uuidA))
        assertEquals("a paused pair resumes straight away", SessionState.RUNNING, active()?.session?.state)
        assertNull(tags.prompt.value)

        tap(TagContent.Ours(uuidA))
        tags.end()
        assertNull(active())
    }

    @Test fun tapOnAnotherPairAsksToSwitch() = runTest {
        tap(TagContent.Ours(uuidA))
        tap(TagContent.Ours(uuidB))
        assertEquals(TagPrompt.Switch("Shoe A", "B", "Shoe B"), tags.prompt.value)
        assertEquals("nothing changes until confirmed", "A", active()?.session?.shoeId)

        tags.dismissPrompt()
        assertEquals("A", active()?.session?.shoeId)

        tap(TagContent.Ours(uuidB))
        tags.confirmSwitch()
        assertEquals("B", active()?.session?.shoeId)
        assertEquals(1, sessionStore.sessions.values.count { it.state != SessionState.ENDED })
    }

    @Test fun doubleReadWithinTwoSecondsIsOneTap() = runTest {
        tap(TagContent.Ours(uuidA))
        clock.advanceBy(500)
        tags.onTag(TappedTag(uuidA, TagContent.Ours(uuidA), FakeHandle()))
        assertNull("no Pause/End question from the repeat", tags.prompt.value)
        assertEquals(1, sessionStore.sessions.size)
    }

    @Test fun blankUnknownAndOrphanTagsAskToLink() = runTest {
        for (content in listOf(TagContent.Blank, TagContent.Foreign, TagContent.Ours("dddddddd-dddd-4ddd-8ddd-dddddddddddd"))) {
            tap(content)
            assertEquals(content.toString(), TagPrompt.NewTag, tags.prompt.value)
            tags.dismissPrompt()
        }
        assertNull("no session is started by an unknown tag", active())
    }

    @Test fun brokenOrUnsupportedTagsAreIgnored() = runTest {
        tap(TagContent.Malformed)
        tap(TagContent.Unsupported, handle = null)
        assertNull(tags.prompt.value)
        assertNull(tags.linking.value)
        assertNull(active())
    }

    @Test fun archivedPairsTagExplainsAndStartsNothing() = runTest {
        tap(TagContent.Ours(uuidZ))
        assertEquals(TagPrompt.Archived("Shoe Z"), tags.prompt.value)
        assertNull(active())
    }

    // --- where a tap lands (product owner: always the same place) ---------------------------------

    @Test fun unlinkedTagsAlwaysGoHome() = runTest {
        tap(TagContent.Blank)
        tap(TagContent.Foreign)
        tap(TagContent.Ours("dddddddd-dddd-4ddd-8ddd-dddddddddddd"))
        assertEquals(List(3) { TagEvent.OpenHome }, allEvents())
        assertEquals(TagPrompt.NewTag, tags.prompt.value)
    }

    @Test fun linkedTagsAlwaysOpenTheirPairEvenWhenAsking() = runTest {
        tap(TagContent.Ours(uuidA)) // starts A
        tap(TagContent.Ours(uuidA)) // asks Pause / End / Cancel
        tags.dismissPrompt()
        tap(TagContent.Ours(uuidB)) // asks to switch
        tags.dismissPrompt()
        tap(TagContent.Ours(uuidZ)) // archived
        assertEquals(
            listOf(
                TagEvent.OpenShoe("A", confirm = true),
                TagEvent.OpenShoe("A", confirm = false),
                TagEvent.OpenShoe("B", confirm = false),
                TagEvent.OpenShoe("Z", confirm = false),
            ),
            allEvents(),
        )
    }

    @Test fun aTapThatLeavesTheAddFormForgetsThePendingLink() = runTest {
        tap(TagContent.Blank)
        tags.addNewPair()
        tap(TagContent.Ours(uuidA))
        assertFalse(tags.consumeLinkAfterAdd())
    }

    // --- new tag: link to existing pair or add a new one ------------------------------------------

    @Test fun newTagLinkedToAnExistingPair() = runTest {
        tagStore.setTag("A", null)
        tap(TagContent.Blank)
        assertEquals(TagEvent.OpenHome, nextEvent())
        tags.linkToExistingPair()
        assertEquals(TagPrompt.PickPair, tags.prompt.value)

        tags.pickPair("A")
        assertNull(tags.prompt.value)
        assertEquals(LinkRequest("A", "Shoe A"), tags.linking.value)
        assertEquals(TagEvent.OpenShoe("A", confirm = false), nextEvent())

        val handle = FakeHandle()
        tap(TagContent.Blank, handle = handle)
        val written = handle.writes.single()
        assertTrue(TagFormat.isValidUuid(written.first))
        assertEquals(written.first, tagStore.shoes.getValue("A").tagUuid)
        assertNull(tags.linking.value)
        assertEquals(LinkedNotice("Shoe A", lockFailed = false), tags.notice.value)
        assertNull("linking never starts a session", active())
    }

    @Test fun newTagForANewPairLinksAfterTheFormIsSaved() = runTest {
        tap(TagContent.Blank)
        tags.addNewPair()
        assertEquals(listOf(TagEvent.OpenHome, TagEvent.AddShoe), allEvents())
        assertTrue(tags.consumeLinkAfterAdd())
        assertFalse("only once", tags.consumeLinkAfterAdd())

        tags.addNewPair()
        tags.onAddCancelled()
        assertFalse("leaving the form cancels it", tags.consumeLinkAfterAdd())
    }

    // --- linking --------------------------------------------------------------------------------

    @Test fun tagOfAnotherPairCannotBeLinked() = runTest {
        tags.startLinking("A")
        val handle = FakeHandle()
        tap(TagContent.Ours(uuidB), handle = handle)
        assertEquals(LinkProblem.InUse("Shoe B"), tags.linking.value?.problem)
        assertTrue("nothing written", handle.writes.isEmpty())
        assertEquals(uuidA, tagStore.shoes.getValue("A").tagUuid)
    }

    @Test fun failedWriteKeepsWaitingAndARetryWorks() = runTest {
        tags.startLinking("A")
        tap(TagContent.Blank, key = "hw-1", handle = FakeHandle(WriteResult.LOST))
        assertEquals(LinkProblem.Write(WriteResult.LOST), tags.linking.value?.problem)
        assertEquals(uuidA, tagStore.shoes.getValue("A").tagUuid)

        tap(TagContent.Blank, key = "hw-1", handle = FakeHandle())
        assertNull(tags.linking.value)
        assertTrue(tagStore.shoes.getValue("A").tagUuid != uuidA)
    }

    @Test fun lockIsPassedToTheTagAndAFailedLockStillLinks() = runTest {
        tags.startLinking("A")
        tags.setLock(true)
        val handle = FakeHandle(WriteResult.LOCK_FAILED)
        tap(TagContent.Foreign, handle = handle)
        assertTrue(handle.writes.single().second)
        assertEquals(LinkedNotice("Shoe A", lockFailed = true), tags.notice.value)
    }

    @Test fun lockedLeftoverTagIsReusedWithItsOwnCode() = runTest {
        val leftover = "eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee"
        tags.startLinking("A")
        tap(TagContent.Ours(leftover), handle = FakeHandle(WriteResult.READ_ONLY))
        assertEquals(leftover, tagStore.shoes.getValue("A").tagUuid)
        assertNull(tags.linking.value)
    }

    @Test fun lockedForeignTagCannotBeLinked() = runTest {
        tags.startLinking("A")
        tap(TagContent.Foreign, handle = FakeHandle(WriteResult.READ_ONLY))
        assertEquals(LinkProblem.Write(WriteResult.READ_ONLY), tags.linking.value?.problem)
    }

    @Test fun brokenTagCannotBeLinked() = runTest {
        tags.startLinking("A")
        tap(TagContent.Malformed)
        assertEquals(LinkProblem.CantUse, tags.linking.value?.problem)
        tap(TagContent.Unsupported, handle = null)
        assertEquals(LinkProblem.CantUse, tags.linking.value?.problem)
    }

    @Test fun phoneRestingOnAJustLinkedTagDoesNotStartASession() = runTest {
        tags.startLinking("A")
        tap(TagContent.Blank, key = "hw-1")
        val code = tagStore.shoes.getValue("A").tagUuid!!
        clock.advanceBy(1_000) // same tag (same hardware id) read again, now holding our code
        tags.onTag(TappedTag("hw-1", TagContent.Ours(code), FakeHandle()))
        assertNull(active())
    }

    @Test fun archivedPairCannotStartLinkingAndCancelStops() = runTest {
        assertFalse(tags.startLinking("Z"))
        assertNull(tags.linking.value)
        assertTrue(tags.startLinking("A"))
        tags.cancelLinking()
        assertNull(tags.linking.value)
    }

    @Test fun unlinkRemovesTheTag() = runTest {
        tags.unlink("A")
        assertNull(tagStore.shoes.getValue("A").tagUuid)
        tap(TagContent.Ours(uuidA))
        assertEquals("its old tag is now a new tag", TagPrompt.NewTag, tags.prompt.value)
    }
}

/** Records writes; answers [result]. */
class FakeHandle(private val result: WriteResult = WriteResult.OK) : TagHandle {
    val writes = mutableListOf<Pair<String, Boolean>>()

    override suspend fun write(uuid: String, lock: Boolean): WriteResult {
        writes += uuid to lock
        return result
    }
}

/** [TagStore] over the same shoe map the FakeSessionStore uses, with the unique-tag rule. */
class FakeTagStore(val shoes: MutableMap<String, ShoeEntity>) : TagStore {
    fun setTag(id: String, uuid: String?) {
        shoes[id] = shoes.getValue(id).copy(tagUuid = uuid)
    }

    override suspend fun findShoe(id: String) = shoes[id]

    override suspend fun shoeForTag(uuid: String) = shoes.values.firstOrNull { it.tagUuid == uuid }

    override suspend fun link(shoeId: String, uuid: String): LinkResult {
        val owner = shoeForTag(uuid)
        if (owner != null && owner.id != shoeId) return LinkResult.InUse(owner.name)
        if (shoeId !in shoes) return LinkResult.NotFound
        setTag(shoeId, uuid)
        return LinkResult.Linked
    }

    override suspend fun unlink(shoeId: String): Boolean {
        if (shoeId !in shoes) return false
        setTag(shoeId, null)
        return true
    }
}
