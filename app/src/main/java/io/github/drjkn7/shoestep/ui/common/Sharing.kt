package io.github.drjkn7.shoestep.ui.common

import kotlinx.coroutines.flow.SharingStarted

/**
 * How screen data is shared from a ViewModel: kept while the screen is shown, and for 5 seconds after
 * (so a rotation or a quick trip to another app doesn't restart the database queries).
 */
val WhileScreenShown: SharingStarted = SharingStarted.WhileSubscribed(5_000)
