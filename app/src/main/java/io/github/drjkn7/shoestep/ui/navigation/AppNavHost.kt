package io.github.drjkn7.shoestep.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.drjkn7.shoestep.ui.placeholder.AddEditShoePlaceholder
import io.github.drjkn7.shoestep.ui.placeholder.HomePlaceholder
import io.github.drjkn7.shoestep.ui.placeholder.SettingsPlaceholder
import io.github.drjkn7.shoestep.ui.placeholder.ShoeDetailPlaceholder

/** Example id used only by the Phase 1 placeholder; removed when real shoes exist (Phase 3). */
private const val EXAMPLE_SHOE_ID = "example"

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val shoeIdArg = listOf(navArgument(Routes.ARG_SHOE_ID) { type = NavType.StringType })
    val goBack: () -> Unit = { navController.popBackStack() }

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomePlaceholder(
                onAddShoe = { navController.navigate(Routes.ADD_SHOE) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenExampleShoe = { navController.navigate(Routes.shoeDetail(EXAMPLE_SHOE_ID)) },
            )
        }
        composable(Routes.ADD_SHOE) {
            AddEditShoePlaceholder(isEdit = false, onBack = goBack)
        }
        composable(Routes.SHOE_DETAIL, arguments = shoeIdArg) { entry ->
            val shoeId = entry.arguments?.getString(Routes.ARG_SHOE_ID).orEmpty()
            ShoeDetailPlaceholder(
                onBack = goBack,
                onEdit = { navController.navigate(Routes.editShoe(shoeId)) },
            )
        }
        composable(Routes.EDIT_SHOE, arguments = shoeIdArg) {
            AddEditShoePlaceholder(isEdit = true, onBack = goBack)
        }
        composable(Routes.SETTINGS) {
            SettingsPlaceholder(onBack = goBack)
        }
    }
}
