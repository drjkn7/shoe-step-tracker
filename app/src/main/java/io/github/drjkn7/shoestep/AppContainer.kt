package io.github.drjkn7.shoestep

import android.content.Context
import io.github.drjkn7.shoestep.core.Clock
import io.github.drjkn7.shoestep.core.Debouncer
import io.github.drjkn7.shoestep.core.SystemWallClock
import io.github.drjkn7.shoestep.data.ShoeRepository
import io.github.drjkn7.shoestep.data.db.ShoeStepDatabase
import io.github.drjkn7.shoestep.nfc.NfcState
import io.github.drjkn7.shoestep.nfc.RoomTagStore
import io.github.drjkn7.shoestep.nfc.TagCoordinator
import io.github.drjkn7.shoestep.nfc.deviceNfcState
import io.github.drjkn7.shoestep.session.RoomSessionStore
import io.github.drjkn7.shoestep.session.SessionManager
import io.github.drjkn7.shoestep.steps.HealthConnectStepsSource
import io.github.drjkn7.shoestep.steps.RoomStepStore
import io.github.drjkn7.shoestep.steps.StepReconciler
import io.github.drjkn7.shoestep.steps.StepsSource

/**
 * Manual dependency injection: the single place where app-wide objects are created.
 * Everything is lazy so nothing heavy runs at app start.
 */
class AppContainer(
    context: Context,
    /** Tests pass [ShoeStepDatabase.buildInMemory] so they never touch the real database. */
    databaseFactory: (Context) -> ShoeStepDatabase = ShoeStepDatabase::build,
    val clock: Clock = SystemWallClock,
    /** Tests pass a fake so they never depend on Health Connect being installed. */
    stepsSourceFactory: (Context) -> StepsSource = ::HealthConnectStepsSource,
    /** Tests pass a fixed answer so they don't depend on the phone's NFC setting. */
    private val nfcStateProvider: (Context) -> NfcState = ::deviceNfcState,
) {
    private val appContext = context.applicationContext

    fun nfcState(): NfcState = nfcStateProvider(appContext)

    /** Opened on first use; Room does the actual disk work off the main thread. */
    val database: ShoeStepDatabase by lazy { databaseFactory(appContext) }

    val shoeRepository: ShoeRepository by lazy { ShoeRepository(database.shoeDao(), clock) }

    /** The only object allowed to change session state; one per app so commands never overlap. */
    val sessionManager: SessionManager by lazy { SessionManager(RoomSessionStore(database), clock) }

    val stepsSource: StepsSource by lazy { stepsSourceFactory(appContext) }

    /** The only object that writes step counts; one per app so runs never overlap. */
    val stepReconciler: StepReconciler by lazy { StepReconciler(RoomStepStore(database), stepsSource, clock) }

    /** Decides what each tag tap does; one per app so its repeat guard sees every tap. */
    val tagCoordinator: TagCoordinator by lazy {
        TagCoordinator(sessionManager, RoomTagStore(database), Debouncer(clock))
    }
}
