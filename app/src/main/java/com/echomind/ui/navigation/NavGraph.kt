package com.echomind.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.echomind.ui.detail.DetailScreen
import com.echomind.ui.home.HomeScreen
import com.echomind.ui.onboarding.OnboardingManager
import com.echomind.ui.onboarding.OnboardingScreen
import com.echomind.ui.qa.QaScreen
import com.echomind.ui.record.RecordScreen
import com.echomind.ui.search.SearchScreen
import com.echomind.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

@Composable
fun EchoMindNavGraph(
    navController: NavHostController = rememberNavController()
) {
    val context = LocalContext.current
    val onboardingManager = remember { OnboardingManager(context) }
    val onboardingCompleted by onboardingManager.isOnboardingCompleted.collectAsState(initial = null)
    val scope = rememberCoroutineScope()

    val startDestination = if (onboardingCompleted == true) {
        Screen.Home.route
    } else {
        Screen.Onboarding.route
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onComplete = {
                    scope.launch {
                        onboardingManager.completeOnboarding()
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Onboarding.route) { inclusive = true }
                        }
                    }
                }
            )
        }
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToRecord = { navController.navigate(Screen.Record.route) },
                onNavigateToSearch = { navController.navigate(Screen.Search.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateToDetail = { entryId -> navController.navigate(Screen.Detail.createRoute(entryId)) },
                onNavigateToQa = { navController.navigate(Screen.Qa.route) }
            )
        }
        composable(Screen.Qa.route) {
            QaScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDetail = { entryId -> navController.navigate(Screen.Detail.createRoute(entryId)) }
            )
        }
        composable(Screen.Record.route) {
            RecordScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Search.route) {
            SearchScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDetail = { entryId -> navController.navigate(Screen.Detail.createRoute(entryId)) }
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(
            route = Screen.Detail.route,
            arguments = listOf(navArgument("entryId") { type = NavType.LongType })
        ) { backStackEntry ->
            val entryId = backStackEntry.arguments?.getLong("entryId") ?: return@composable
            DetailScreen(
                entryId = entryId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
