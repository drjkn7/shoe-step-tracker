package io.github.drjkn7.shoestep.ui.navigation

/**
 * All navigation destinations. Route strings live only here so no screen builds them by hand.
 */
object Routes {
    const val ARG_SHOE_ID = "shoeId"

    const val HOME = "home"
    const val SETTINGS = "settings"
    const val ADD_SHOE = "shoe/new"
    const val SHOE_DETAIL = "shoe/{$ARG_SHOE_ID}"
    const val EDIT_SHOE = "shoe/{$ARG_SHOE_ID}/edit"

    fun shoeDetail(shoeId: String): String = "shoe/${requireSafeId(shoeId)}"

    fun editShoe(shoeId: String): String = "shoe/${requireSafeId(shoeId)}/edit"

    private val SAFE_ID = Regex("^[A-Za-z0-9-]{1,64}$")

    /** Shoe ids are UUIDs; anything else must never end up inside a route. */
    private fun requireSafeId(id: String): String {
        require(SAFE_ID.matches(id)) { "Invalid shoe id" }
        return id
    }
}
