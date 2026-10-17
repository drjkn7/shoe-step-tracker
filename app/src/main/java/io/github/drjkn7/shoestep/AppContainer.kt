package io.github.drjkn7.shoestep

import io.github.drjkn7.shoestep.core.Clock
import io.github.drjkn7.shoestep.core.SystemWallClock

/**
 * Manual dependency injection: the single place where app-wide objects are created.
 * Everything is lazy so nothing heavy runs at app start. Later phases add the database,
 * repositories, SessionManager and the Health Connect StepsSource here.
 */
class AppContainer {
    val clock: Clock by lazy { SystemWallClock }
}
