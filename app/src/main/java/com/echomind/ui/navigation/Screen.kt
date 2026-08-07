package com.echomind.ui.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Record : Screen("record")
    data object Search : Screen("search")
    data object Settings : Screen("settings")
    data object Detail : Screen("detail/{entryId}") {
        fun createRoute(entryId: Long) = "detail/$entryId"
    }
    data object Qa : Screen("qa")
    data object Onboarding : Screen("onboarding")
    data object Themes : Screen("themes")
    data object ThemeDetail : Screen("theme/{themeId}") {
        fun createRoute(themeId: Long) = "theme/$themeId"
    }
    data object Decisions : Screen("decisions")
}
