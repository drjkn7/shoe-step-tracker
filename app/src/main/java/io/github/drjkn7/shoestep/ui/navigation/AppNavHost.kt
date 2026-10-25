package io.github.drjkn7.shoestep.ui.navigation

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.drjkn7.shoestep.AppContainer
import io.github.drjkn7.shoestep.core.Money
import io.github.drjkn7.shoestep.nfc.NfcState
import io.github.drjkn7.shoestep.nfc.TagEvent
import io.github.drjkn7.shoestep.ui.nfc.TagPromptHost
import io.github.drjkn7.shoestep.ui.session.NotificationPromptHost
import io.github.drjkn7.shoestep.ui.common.FilterTouchesWhenObscured
import io.github.drjkn7.shoestep.ui.common.LocalAppContainer
import io.github.drjkn7.shoestep.ui.common.LocalDistanceUnit
import io.github.drjkn7.shoestep.settings.DistanceUnit
import io.github.drjkn7.shoestep.ui.home.HomeScreen
import io.github.drjkn7.shoestep.ui.home.HomeViewModel
import io.github.drjkn7.shoestep.ui.settings.LicencesScreen
import io.github.drjkn7.shoestep.ui.settings.PrivacyScreen
import io.github.drjkn7.shoestep.ui.settings.SettingsScreen
import io.github.drjkn7.shoestep.ui.settings.SettingsViewModel
import io.github.drjkn7.shoestep.ui.shoe.AddPastSessionScreen
import io.github.drjkn7.shoestep.ui.shoe.AddPastSessionViewModel
import io.github.drjkn7.shoestep.ui.shoe.ShoeDetailScreen
import io.github.drjkn7.shoestep.ui.shoe.ShoeDetailViewModel
import io.github.drjkn7.shoestep.ui.shoe.ShoeFormScreen
import io.github.drjkn7.shoestep.ui.shoe.ShoeFormViewModel
import kotlinx.coroutines.launch

@Composable
fun AppNavHost(container: AppContainer) {
    FilterTouchesWhenObscured() // SEC-11 for the main window
    val locale = LocalConfiguration.current.locales[0]
    // Settings → Units; until chosen, the phone's region decides. A change redraws every screen.
    val chosenUnit by container.settings.distanceUnit.collectAsStateWithLifecycle(initialValue = null)
    CompositionLocalProvider(
        LocalAppContainer provides container,
        LocalDistanceUnit provides (chosenUnit ?: DistanceUnit.defaultFor(locale)),
    ) {
        val navController = rememberNavController()
        val shoeIdArg = remember { listOf(navArgument(Routes.ARG_SHOE_ID) { type = NavType.StringType }) }
        val goBack: () -> Unit = { navController.popBackStack() }
        val tags = container.tagCoordinator
        val scope = rememberCoroutineScope()
        val view = LocalView.current
        val hasNfc = remember { container.nfcState() != NfcState.UNSUPPORTED }

        // Tag taps land in the same place every time: a linked tag on its pair's page (opened fresh on
        // top of Home unless it is already showing), an unlinked tag on Home. Buzz when a session changed (T06).
        LaunchedEffect(tags) {
            tags.events.collect { event ->
                when (event) {
                    is TagEvent.OpenShoe -> {
                        if (event.confirm) view.performHapticFeedback(confirmHaptic())
                        val current = navController.currentBackStackEntry
                        val alreadyOpen = current?.destination?.route == Routes.SHOE_DETAIL &&
                            current.arguments?.getString(Routes.ARG_SHOE_ID) == event.shoeId
                        if (!alreadyOpen) navController.navigate(Routes.shoeDetail(event.shoeId)) { popUpTo(Routes.HOME) }
                    }
                    TagEvent.OpenHome -> navController.popBackStack(Routes.HOME, inclusive = false)
                    TagEvent.AddShoe -> navController.navigate(Routes.ADD_SHOE) { popUpTo(Routes.HOME) }
                }
            }
        }

        NavHost(navController = navController, startDestination = Routes.HOME) {
            composable(Routes.HOME) {
                HomeScreen(
                    viewModel = viewModel { HomeViewModel(
                            container.shoeRepository,
                            container.sessionManager,
                            container.stepsSource,
                            container.stepReconciler,
                            container.settings,
                        ) },
                    onAddShoe = {
                        tags.onAddCancelled() // a plain "Add" never links a tag afterwards
                        navController.navigate(Routes.ADD_SHOE)
                    },
                    onOpenShoe = { id -> navController.navigate(Routes.shoeDetail(id)) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.ADD_SHOE) {
                val form = viewModel {
                    ShoeFormViewModel(
                        repository = container.shoeRepository,
                        clock = container.clock,
                        savedState = createSavedStateHandle(),
                        shoeId = null,
                        defaultCurrencyCode = Money.defaultCurrencyCode(locale),
                    )
                }
                // The default currency chosen in Settings (if any) replaces the region's guess.
                val savedCurrency by container.settings.defaultCurrency.collectAsStateWithLifecycle(initialValue = null)
                LaunchedEffect(savedCurrency) { savedCurrency?.let(form::applyDefaultCurrency) }
                ShoeFormScreen(
                    viewModel = form,
                    onBack = {
                        tags.onAddCancelled()
                        goBack()
                    },
                    onSaved = { newShoeId ->
                        // Added from a new-tag tap: link that tag to the new pair next.
                        if (tags.consumeLinkAfterAdd()) scope.launch { tags.startLinking(newShoeId) }
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
                    viewModel = viewModel { ShoeDetailViewModel(container.shoeRepository, container.sessionManager, shoeId, container.stepReconciler) },
                    onBack = goBack,
                    onEdit = { navController.navigate(Routes.editShoe(shoeId)) },
                    hasNfc = hasNfc,
                    isNfcOn = { container.nfcState() == NfcState.ON },
                    onLinkTag = { scope.launch { tags.startLinking(shoeId) } },
                    onUnlinkTag = { scope.launch { tags.unlink(shoeId) } },
                    onAddPastSession = { navController.navigate(Routes.addPastSession(shoeId)) },
                )
            }
            composable(Routes.ADD_PAST_SESSION, arguments = shoeIdArg) { entry ->
                val shoeId = entry.arguments?.getString(Routes.ARG_SHOE_ID).orEmpty()
                AddPastSessionScreen(
                    viewModel = viewModel {
                        AddPastSessionViewModel(
                            repository = container.shoeRepository,
                            sessions = container.sessionManager,
                            stepsSource = container.stepsSource,
                            reconciler = container.stepReconciler,
                            clock = container.clock,
                            savedState = createSavedStateHandle(),
                            shoeId = shoeId,
                        )
                    },
                    onBack = goBack,
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
                SettingsScreen(
                    viewModel = viewModel {
                        SettingsViewModel(container.settings, container.stepsSource, container.exporter, container.clock, container.systemNightMode, container.stepReconciler, container.notificationAccess, container.sessionNotifier)
                    },
                    onBack = goBack,
                    onOpenPrivacy = { navController.navigate(Routes.PRIVACY) },
                    onOpenLicences = { navController.navigate(Routes.LICENCES) },
                )
            }
            composable(Routes.PRIVACY) { PrivacyScreen(onBack = goBack) }
            composable(Routes.LICENCES) { LicencesScreen(onBack = goBack) }
        }

        // The tag pop-ups collect their own list of pairs, so a change to any pair never redraws this screen map.
        TagPromptHost(tags, remember { container.shoeRepository.observePairs() })
        NotificationPromptHost(container)
    }
}

/** A short "done" buzz; CONFIRM exists from Android 11, older phones get the long-press buzz. No permission needed. */
private fun confirmHaptic(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS
