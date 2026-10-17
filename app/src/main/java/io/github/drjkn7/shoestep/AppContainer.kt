package io.github.drjkn7.shoestep

import android.content.Context
import io.github.drjkn7.shoestep.core.Clock
import io.github.drjkn7.shoestep.core.SystemWallClock
import io.github.drjkn7.shoestep.data.ShoeRepository
import io.github.drjkn7.shoestep.data.db.ShoeStepDatabase
import io.github.drjkn7.shoestep.session.RoomSessionStore
import io.github.drjkn7.shoestep.session.SessionManager

/**
 * Manual dependency injection: the single place where app-wide objects are created.
 * Everything is lazy so nothing heavy runs at app start. Phase 5 adds
 * the Health Connect StepsSource here.
 */
class AppContainer(
    context: Context,
    /** Tests pass [ShoeStepDatabase.buildInMemory] so they never touch the real database. */
    databaseFactory: (Context) -> ShoeStepDatabase = ShoeStepDatabase::build,
    val clock: Clock = SystemWallClock,
) {
    private val appContext = context.applicationContext

    /** Opened on first use; Room does the actual disk work off the main thread. */
    val database: ShoeStepDatabase by lazy { databaseFactory(appContext) }

    val shoeRepository: ShoeRepository by lazy { ShoeRepository(database.shoeDao(), clock) }

    /** The only object allowed to change session state; one per app so commands never overlap. */
    val sessionManager: SessionManager by lazy { SessionManager(RoomSessionStore(database), clock) }
}
