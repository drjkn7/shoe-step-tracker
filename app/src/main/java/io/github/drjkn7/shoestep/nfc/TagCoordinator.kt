package io.github.drjkn7.shoestep.nfc

import io.github.drjkn7.shoestep.core.Debouncer
import io.github.drjkn7.shoestep.data.db.ShoeEntity
import io.github.drjkn7.shoestep.data.model.SessionState
import io.github.drjkn7.shoestep.session.SessionManager
import io.github.drjkn7.shoestep.session.SessionResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A question shown after a tap. Tapping outside any of them cancels and changes nothing. */
sealed interface TagPrompt {
    /** Blank or unknown tag: "Link to an existing pair" or "Add a new pair" (T14). */
    data object NewTag : TagPrompt

    /** Choosing which pair a new tag belongs to. */
    data object PickPair : TagPrompt

    /** Tap on the pair whose session is running: Pause / End / Cancel (T11). */
    data class RunningTap(val sessionId: String, val shoeName: String) : TagPrompt

    /** Tap on another pair while a session is in progress (T12). */
    data class Switch(val activeShoeName: String, val newShoeId: String, val newShoeName: String) : TagPrompt

    /** The tag belongs to an archived pair. */
    data class Archived(val shoeName: String) : TagPrompt
}

/** "Hold your phone against the tag" for [shoeName]; [problem] explains why the last try failed. */
data class LinkRequest(
    val shoeId: String,
    val shoeName: String,
    val lock: Boolean = false,
    val problem: LinkProblem? = null,
)

sealed interface LinkProblem {
    data class Write(val result: WriteResult) : LinkProblem

    /** The tag already belongs to another pair (T29). */
    data class InUse(val shoeName: String) : LinkProblem

    /** Not a tag this app can use (wrong type, or broken data on it). */
    data object CantUse : LinkProblem
}

/** One-off confirmation after linking. */
data class LinkedNotice(val shoeName: String, val lockFailed: Boolean)

sealed interface TagEvent {
    /** Show this pair; [confirm] = a session just started, resumed or switched (buzz). */
    data class OpenShoe(val shoeId: String, val confirm: Boolean) : TagEvent

    /** Go back to Home (an unlinked tag's pop-up is always shown there). */
    data object OpenHome : TagEvent

    /** Open the "Add shoe" form; the tag is linked to the new pair once it is saved. */
    data object AddShoe : TagEvent
}

/**
 * Decides what a tag tap does. Every tap goes through the [Debouncer] (T13) and then, for session
 * changes, through SessionManager, the only code that changes session state.
 *
 * Where a tap lands is always the same (product owner): a linked tag opens its pair's page, an
 * unlinked tag opens Home; any question is then shown on that screen.
 * - Our tag, idle → start (T06/T07). Same pair running → Pause / End / Cancel (T11).
 *   Same pair paused → resume straight away (product owner). Another pair → switch prompt (T12).
 * - Blank, unknown or orphaned tag → "New tag" pop-up (T14). Broken or unsupported → ignored (T15).
 * - While [linking] is set, the next tap writes the tag and links it to that pair (T04/T05/T29).
 */
class TagCoordinator(
    private val sessions: SessionManager,
    private val store: TagStore,
    private val debouncer: Debouncer,
) {
    private val mutex = Mutex()

    private val _prompt = MutableStateFlow<TagPrompt?>(null)
    val prompt: StateFlow<TagPrompt?> = _prompt.asStateFlow()

    private val _linking = MutableStateFlow<LinkRequest?>(null)
    val linking: StateFlow<LinkRequest?> = _linking.asStateFlow()

    private val _notice = MutableStateFlow<LinkedNotice?>(null)
    val notice: StateFlow<LinkedNotice?> = _notice.asStateFlow()

    /** Buffered, so a tap that opened the app is not lost before the screen is ready. */
    private val _events = Channel<TagEvent>(Channel.BUFFERED)
    val events: Flow<TagEvent> = _events.receiveAsFlow()

    private var linkAfterAdd = false

    suspend fun onTag(tag: TappedTag) = mutex.withLock {
        if (!debouncer.accept(tag.key)) return@withLock
        val request = _linking.value
        if (request != null) link(request, tag) else route(tag)
    }

    // --- taps -----------------------------------------------------------------------------------

    private suspend fun route(tag: TappedTag) {
        when (val content = tag.content) {
            is TagContent.Ours -> {
                val shoe = store.shoeForTag(content.uuid)
                // A tag left over from a deleted pair is treated like a new one.
                if (shoe == null) newTag() else tapShoe(shoe)
            }
            TagContent.Blank, TagContent.Foreign -> newTag()
            TagContent.Malformed, TagContent.Unsupported -> Unit // ignored safely (T15)
        }
    }

    private suspend fun newTag() {
        linkAfterAdd = false // any open "Add shoe" form is left
        _events.send(TagEvent.OpenHome)
        _prompt.value = TagPrompt.NewTag
    }

    private suspend fun tapShoe(shoe: ShoeEntity) {
        linkAfterAdd = false // any open "Add shoe" form is left
        if (shoe.archived) return ask(shoe.id, TagPrompt.Archived(shoe.name))
        val active = sessions.activeSession.first()
        when {
            active == null -> when (sessions.start(shoe.id)) {
                SessionResult.Done -> open(shoe.id, confirm = true)
                SessionResult.ShoeUnavailable -> ask(shoe.id, TagPrompt.Archived(shoe.name))
                is SessionResult.NeedsSwitch -> sessions.activeSession.first()
                    ?.let { ask(shoe.id, TagPrompt.Switch(it.shoeName, shoe.id, shoe.name)) }
                    ?: open(shoe.id, confirm = false)
                SessionResult.NoChange -> open(shoe.id, confirm = false)
            }
            active.session.shoeId != shoe.id ->
                ask(shoe.id, TagPrompt.Switch(active.shoeName, shoe.id, shoe.name))
            active.session.state == SessionState.PAUSED -> {
                val result = sessions.resume(active.session.id)
                open(shoe.id, confirm = result == SessionResult.Done)
            }
            else -> ask(shoe.id, TagPrompt.RunningTap(active.session.id, shoe.name))
        }
    }

    private suspend fun open(shoeId: String, confirm: Boolean) {
        _prompt.value = null
        _events.send(TagEvent.OpenShoe(shoeId, confirm))
    }

    /** Opens the tapped pair's page, then asks [prompt] there. */
    private suspend fun ask(shoeId: String, prompt: TagPrompt) {
        _events.send(TagEvent.OpenShoe(shoeId, confirm = false))
        _prompt.value = prompt
    }

    // --- answers to prompts ---------------------------------------------------------------------

    /** Cancel, Back or a tap outside the pop-up: nothing changes. */
    fun dismissPrompt() {
        _prompt.value = null
    }

    suspend fun pause() {
        val prompt = _prompt.value as? TagPrompt.RunningTap ?: return
        _prompt.value = null
        sessions.pause(prompt.sessionId)
    }

    suspend fun end() {
        val prompt = _prompt.value as? TagPrompt.RunningTap ?: return
        _prompt.value = null
        sessions.end(prompt.sessionId)
    }

    suspend fun confirmSwitch() {
        val prompt = _prompt.value as? TagPrompt.Switch ?: return
        val result = sessions.switchTo(prompt.newShoeId)
        open(prompt.newShoeId, confirm = result == SessionResult.Done)
    }

    fun linkToExistingPair() {
        if (_prompt.value == TagPrompt.NewTag) _prompt.value = TagPrompt.PickPair
    }

    suspend fun pickPair(shoeId: String) {
        _prompt.value = null
        if (startLinking(shoeId)) _events.send(TagEvent.OpenShoe(shoeId, confirm = false))
    }

    suspend fun addNewPair() {
        _prompt.value = null
        linkAfterAdd = true
        _events.send(TagEvent.AddShoe)
    }

    /**
     * Called (synchronously) when a new pair is saved: true if it was added from a tag tap, and
     * the caller should then [startLinking] it. Clears the request either way.
     */
    fun consumeLinkAfterAdd(): Boolean = linkAfterAdd.also { linkAfterAdd = false }

    /** The "Add shoe" form was left without saving. */
    fun onAddCancelled() {
        linkAfterAdd = false
    }

    // --- linking --------------------------------------------------------------------------------

    /** Waits for the next tap and links it to this pair. False if the pair can't take a tag. */
    suspend fun startLinking(shoeId: String): Boolean {
        val shoe = store.findShoe(shoeId)?.takeIf { !it.archived } ?: return false
        _linking.value = LinkRequest(shoe.id, shoe.name)
        return true
    }

    fun setLock(lock: Boolean) {
        _linking.value = _linking.value?.copy(lock = lock)
    }

    fun cancelLinking() {
        _linking.value = null
    }

    suspend fun unlink(shoeId: String) {
        store.unlink(shoeId)
    }

    fun dismissNotice() {
        _notice.value = null
    }

    private suspend fun link(request: LinkRequest, tag: TappedTag) {
        fun fail(problem: LinkProblem) {
            _linking.value = _linking.value?.copy(problem = problem)
        }

        val content = tag.content
        val uuid = when (content) {
            is TagContent.Ours -> {
                val owner = store.shoeForTag(content.uuid)
                if (owner != null && owner.id != request.shoeId) return fail(LinkProblem.InUse(owner.name))
                content.uuid // keep the tag's own code; a locked leftover tag can still be reused
            }
            TagContent.Blank, TagContent.Foreign -> TagFormat.newUuid()
            TagContent.Malformed, TagContent.Unsupported -> return fail(LinkProblem.CantUse)
        }
        val handle = tag.handle ?: return fail(LinkProblem.CantUse)

        val written = handle.write(uuid, request.lock)
        val alreadyHoldsIt = content is TagContent.Ours && written == WriteResult.READ_ONLY
        if (written != WriteResult.OK && written != WriteResult.LOCK_FAILED && !alreadyHoldsIt) {
            return fail(LinkProblem.Write(written))
        }
        when (val result = store.link(request.shoeId, uuid)) {
            LinkResult.Linked -> {
                _linking.value = null
                _notice.value = LinkedNotice(request.shoeName, lockFailed = written == WriteResult.LOCK_FAILED)
            }
            is LinkResult.InUse -> fail(LinkProblem.InUse(result.otherShoeName))
            LinkResult.NotFound -> _linking.value = null
        }
    }
}
