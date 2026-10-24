package io.github.drjkn7.shoestep

import io.github.drjkn7.shoestep.core.Clock
import io.github.drjkn7.shoestep.data.SaveResult
import io.github.drjkn7.shoestep.data.ShoeInput
import io.github.drjkn7.shoestep.data.model.ShoeType
import io.github.drjkn7.shoestep.session.RoomSessionStore
import io.github.drjkn7.shoestep.session.SessionManager
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId

/**
 * Debug build only: fills the test app ("Shoe Step (test)") with sample pairs and past sessions, so
 * every screen has something to show when checking the design on a phone. It never runs on its own
 * (Settings → Test build → Add sample pairs) and does not exist in release builds.
 *
 * Pairs go through [io.github.drjkn7.shoestep.data.ShoeRepository] and sessions through
 * [SessionManager], the same paths the app uses. Past sessions use a second SessionManager whose clock
 * is moved back to each session's time; their step counts come from Health Connect for those times,
 * like any other session, once the test app is allowed to read steps.
 */
object SampleData {

    enum class Outcome { ADDED, SESSION_ACTIVE }

    suspend fun add(container: AppContainer): Outcome {
        // Past sessions can't be added around a session in progress; ask to end it first.
        if (container.sessionManager.activeSession.first() != null) return Outcome.SESSION_ACTIVE
        val zone = container.clock.zoneId()
        val today = LocalDate.now(zone)
        val repo = container.shoeRepository

        suspend fun pair(name: String, brand: String, model: String, type: ShoeType, price: String, bought: LocalDate?, colour: Int): String {
            val input = ShoeInput(name, brand, model, type, price, "GBP", bought?.toEpochDay(), colour)
            return (repo.add(input) as SaveResult.Saved).shoeId
        }

        val trail = pair("Trail Runner 2", "Saucony", "Peregrine 15", ShoeType.TRAIL_RUNNING, "129.00", today.minusMonths(7), 4)
        val court = pair("Daily Court", "Asics", "Gel-Court", ShoeType.WALKING, "89.99", today.minusYears(1), 6)
        val hiker = pair("Peak Hiker", "", "", ShoeType.HIKING, "", null, 1)
        pair("A very long pair name to check how the screens wrap it", "", "", ShoeType.CASUAL, "45", null, 7)
        val old = pair("Old racers", "Brooks", "Hyperion", ShoeType.ROAD_RUNNING, "110", today.minusYears(3), 0)
        repo.setArchived(old, true)

        val clock = MovableClock(zone)
        val past = SessionManager(RoomSessionStore(container.database), clock)
        fun at(daysAgo: Long, hour: Int, minute: Int) {
            clock.now = today.minusDays(daysAgo).atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
        }
        fun later(minutes: Long) {
            clock.now += minutes * 60_000
        }
        suspend fun walk(shoeId: String, daysAgo: Long, hour: Int, minute: Int, minutes: Long) {
            at(daysAgo, hour, minute)
            past.start(shoeId)
            later(minutes)
            past.endForShoe(shoeId)
        }

        walk(trail, 9, 7, 55, 41)
        walk(trail, 4, 12, 30, 22)
        walk(court, 3, 18, 10, 72)
        walk(hiker, 6, 9, 15, 135)
        // Ended by switching pairs: Trail Runner 2 out, then Daily Court.
        at(2, 8, 5)
        past.start(trail)
        later(38)
        past.switchTo(court)
        later(25)
        past.endForShoe(court)
        // With a pause in the middle.
        at(1, 17, 40)
        past.start(trail)
        later(20)
        past.activeSession.first()?.let { past.pause(it.session.id) }
        later(15)
        past.activeSession.first()?.let { past.resume(it.session.id) }
        later(25)
        past.endForShoe(trail)

        // One session running now, through the app's own SessionManager.
        container.sessionManager.start(trail)
        return Outcome.ADDED
    }

    private class MovableClock(private val zone: ZoneId) : Clock {
        var now = 0L
        override fun nowUtcMillis() = now
        override fun zoneId() = zone
    }
}
