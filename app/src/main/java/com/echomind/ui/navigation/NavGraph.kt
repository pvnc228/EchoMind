package com.echomind.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.echomind.ui.detail.DetailScreen
import com.echomind.ui.home.HomeScreen
import com.echomind.ui.qa.QaScreen
import com.echomind.ui.record.RecordScreen
import com.echomind.ui.search.SearchScreen
import com.echomind.ui.settings.SettingsScreen

@Composable
fun EchoMindNavGraph(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
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
