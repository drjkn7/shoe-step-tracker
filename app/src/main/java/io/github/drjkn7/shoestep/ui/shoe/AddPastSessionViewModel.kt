package io.github.drjkn7.shoestep.ui.shoe

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.drjkn7.shoestep.core.Clock
import io.github.drjkn7.shoestep.data.ShoeRepository
import io.github.drjkn7.shoestep.data.db.SessionWithShoeName
import io.github.drjkn7.shoestep.session.AddPastResult
import io.github.drjkn7.shoestep.session.PastTimeProblem
import io.github.drjkn7.shoestep.session.SessionManager
import io.github.drjkn7.shoestep.steps.DistanceRead
import io.github.drjkn7.shoestep.steps.StepReconciler
import io.github.drjkn7.shoestep.steps.StepsAccess
import io.github.drjkn7.shoestep.steps.StepsRead
import io.github.drjkn7.shoestep.steps.StepsSource
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The form's choice: a day and two times of day (minutes after midnight), in the phone's time zone. */
data class PastTimes(val epochDay: Long, val startMinute: Int, val endMinute: Int)

/** What stops Save, shown under the times (PNG 13). */
sealed interface PastProblem {
    data class Time(val problem: PastTimeProblem) : PastProblem
    data class Overlap(val other: SessionWithShoeName) : PastProblem
}

/** The preview card (PNG 12): what Health Connect has for the chosen time. Nothing here is saved. */
sealed interface PastPreview {
    data object Loading : PastPreview
    data class Known(val steps: Long, val meters: Double?) : PastPreview
    data object NoData : PastPreview

    /** Steps can't be read now (not allowed, or no answer); they are filled in later as usual. */
    data object Unavailable : PastPreview
}

sealed interface PastStatus {
    data object Loading : PastStatus
    data object Editing : PastStatus
    data object Saving : PastStatus
    data object Saved : PastStatus

    /** The pair was deleted or archived meanwhile. */
    data object NotFound : PastStatus
}

/**
 * Add past session (M2 Phase 6) for one pair. Problems and the preview follow the chosen times live;
 * [SessionManager.addPast] checks everything again when saving. The times are mirrored into
 * [SavedStateHandle] so they survive rotation and the app being killed in the background.
 */
class AddPastSessionViewModel(
    private val repository: ShoeRepository,
    private val sessions: SessionManager,
    private val stepsSource: StepsSource,
    private val reconciler: StepReconciler,
    private val clock: Clock,
    private val savedState: SavedStateHandle,
    val shoeId: String,
) : ViewModel() {

    var shoeName by mutableStateOf("")
        private set

    var times by mutableStateOf(restore() ?: defaultTimes())
        private set

    var problem by mutableStateOf<PastProblem?>(null)
        private set

    var preview by mutableStateOf<PastPreview>(PastPreview.Loading)
        private set

    var status by mutableStateOf<PastStatus>(PastStatus.Loading)
        private set

    /** True while the latest times are being checked for problems (not while the preview loads). */
    private var checking by mutableStateOf(true)

    private var checkJob: Job? = null

    init {
        viewModelScope.launch {
            val shoe = repository.findShoe(shoeId)
            if (shoe == null || shoe.archived) {
                status = PastStatus.NotFound
            } else {
                shoeName = shoe.name
                status = PastStatus.Editing
                check()
            }
        }
    }

    fun update(newTimes: PastTimes) {
        if (status != PastStatus.Editing) return
        times = newTimes
        savedState[KEY_DAY] = newTimes.epochDay
        savedState[KEY_START] = newTimes.startMinute
        savedState[KEY_END] = newTimes.endMinute
        check()
    }

    /** True when Save can be tapped: the times were checked and are fine, and nothing else is happening. */
    val canSave: Boolean get() = status == PastStatus.Editing && !checking && problem == null

    fun save() {
        if (!canSave) return // ignore double taps
        status = PastStatus.Saving
        val (start, end) = utcRange(times)
        viewModelScope.launch {
            when (val result = sessions.addPast(shoeId, start, end)) {
                is AddPastResult.Added -> {
                    // Fill in the steps now, so the pair's page shows them on return.
                    reconciler.reconcile()
                    status = PastStatus.Saved
                }
                is AddPastResult.Invalid -> {
                    problem = PastProblem.Time(result.problem)
                    status = PastStatus.Editing
                }
                is AddPastResult.Overlaps -> {
                    problem = PastProblem.Overlap(result.other)
                    status = PastStatus.Editing
                }
                AddPastResult.ShoeUnavailable -> status = PastStatus.NotFound
            }
        }
    }

    /** Re-checks the times and, if they are fine, reads the preview (after a short pause while picking). */
    private fun check() {
        checkJob?.cancel()
        checking = true
        val (start, end) = utcRange(times)
        checkJob = viewModelScope.launch {
            val timeProblem = SessionManager.pastTimeProblem(start, end, clock.nowUtcMillis())
            problem = timeProblem?.let { PastProblem.Time(it) }
                ?: sessions.firstOverlapping(start, end)?.let { PastProblem.Overlap(it) }
            checking = false
            if (problem != null) return@launch
            preview = PastPreview.Loading
            delay(PREVIEW_DELAY_MILLIS)
            preview = readPreview(start, end)
        }
    }

    private suspend fun readPreview(start: Long, end: Long): PastPreview {
        if (stepsSource.access() != StepsAccess.READY) return PastPreview.Unavailable
        return when (val steps = stepsSource.read(start, end)) {
            is StepsRead.Count -> {
                val meters = if (stepsSource.distanceAllowed()) (stepsSource.readDistance(start, end) as? DistanceRead.Meters)?.meters else null
                PastPreview.Known(steps.steps, meters)
            }
            StepsRead.NoData -> PastPreview.NoData
            StepsRead.Failed -> PastPreview.Unavailable
        }
    }

    /** The chosen times as UTC millis in the phone's zone (a time skipped by a clock change moves forward). */
    private fun utcRange(t: PastTimes): Pair<Long, Long> {
        val day = LocalDate.ofEpochDay(t.epochDay)
        fun at(minute: Int) = day.atTime(LocalTime.of(minute / 60, minute % 60)).atZone(clock.zoneId()).toInstant().toEpochMilli()
        return at(t.startMinute) to at(t.endMinute)
    }

    /** Today, from half an hour ago to now (rounded down to 5 minutes), kept within today. */
    private fun defaultTimes(): PastTimes {
        val now = Instant.ofEpochMilli(clock.nowUtcMillis()).atZone(clock.zoneId())
        val end = (now.hour * 60 + now.minute) / 5 * 5
        return PastTimes(now.toLocalDate().toEpochDay(), (end - 30).coerceAtLeast(0), end)
    }

    private fun restore(): PastTimes? {
        val day = savedState.get<Long>(KEY_DAY) ?: return null
        val start = savedState.get<Int>(KEY_START) ?: return null
        val end = savedState.get<Int>(KEY_END) ?: return null
        return PastTimes(day, start, end)
    }

    private companion object {
        const val KEY_DAY = "past_day"
        const val KEY_START = "past_start"
        const val KEY_END = "past_end"

        /** Waits this long after a change before asking Health Connect, so quick edits don't each read. */
        const val PREVIEW_DELAY_MILLIS = 300L
    }
}
