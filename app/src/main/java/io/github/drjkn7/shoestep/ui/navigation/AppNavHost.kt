package io.github.drjkn7.shoestep.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.drjkn7.shoestep.AppContainer
import io.github.drjkn7.shoestep.core.Money
import io.github.drjkn7.shoestep.ui.common.FilterTouchesWhenObscured
import io.github.drjkn7.shoestep.ui.common.LocalAppContainer
import io.github.drjkn7.shoestep.ui.home.HomeScreen
import io.github.drjkn7.shoestep.ui.home.HomeViewModel
import io.github.drjkn7.shoestep.ui.settings.SettingsScreen
import io.github.drjkn7.shoestep.ui.shoe.ShoeDetailScreen
import io.github.drjkn7.shoestep.ui.shoe.ShoeDetailViewModel
import io.github.drjkn7.shoestep.ui.shoe.ShoeFormScreen
import io.github.drjkn7.shoestep.ui.shoe.ShoeFormViewModel

@Composable
fun AppNavHost(container: AppContainer) {
    FilterTouchesWhenObscured() // SEC-11 for the main window
    CompositionLocalProvider(LocalAppContainer provides container) {
        val navController = rememberNavController()
        val shoeIdArg = listOf(navArgument(Routes.ARG_SHOE_ID) { type = NavType.StringType })
        val locale = LocalConfiguration.current.locales[0]
        val goBack: () -> Unit = { navController.popBackStack() }

        NavHost(navController = navController, startDestination = Routes.HOME) {
            composable(Routes.HOME) {
                HomeScreen(
                    viewModel = viewModel { HomeViewModel(container.shoeRepository, container.sessionManager) },
                    onAddShoe = { navController.navigate(Routes.ADD_SHOE) },
                    onOpenShoe = { id -> navController.navigate(Routes.shoeDetail(id)) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.ADD_SHOE) {
                ShoeFormScreen(
                    viewModel = viewModel {
                        ShoeFormViewModel(
                            repository = container.shoeRepository,
                            clock = container.clock,
                            savedState = createSavedStateHandle(),
                            shoeId = null,
                            defaultCurrencyCode = Money.defaultCurrencyCode(locale),
                        )
                    },
                    onBack = goBack,
                    onSaved = { newShoeId ->
                        // Product decision: open the new shoe (next step there will be tagging it);
                        // Back from the detail then returns to the list, not the form.
                        navController.navigate(Routes.shoeDetail(newShoeId)) {
                            popUpTo(Routes.HOME)
                        }
                    },
                )
            }
            composable(Routes.SHOE_DETAIL, arguments = shoeIdArg) { entry ->
                val shoeId = entry.arguments?.getString(Routes.ARG_SHOE_ID).orEmpty()
                ShoeDetailScreen(
                    viewModel = viewModel { ShoeDetailViewModel(container.shoeRepository, container.sessionManager, shoeId) },
                    onBack = goBack,
                    onEdit = { navController.navigate(Routes.editShoe(shoeId)) },
                )
            }
            composable(Routes.EDIT_SHOE, arguments = shoeIdArg) { entry ->
                val shoeId = entry.arguments?.getString(Routes.ARG_SHOE_ID).orEmpty()
                ShoeFormScreen(
                    viewModel = viewModel {
                        ShoeFormViewModel(
                            repository = container.shoeRepository,
                            clock = container.clock,
                            savedState = createSavedStateHandle(),
                            shoeId = shoeId,
                            defaultCurrencyCode = Money.defaultCurrencyCode(locale),
                        )
                    },
                    onBack = goBack,
                    // After editing, return to the shoe's detail screen, which updates live.
                    onSaved = { goBack() },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(onBack = goBack)
            }
        }
    }
}
