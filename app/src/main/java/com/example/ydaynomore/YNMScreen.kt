package com.example.ydaynomore

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ydaynomore.ui.ActionScreen
import com.example.ydaynomore.ui.RecycleScreen

enum class YNMScreen() {
    Action,
    Recycle
}

@Composable
fun YNMApp(navController: NavHostController = rememberNavController()) {
    // Get current back stack entry
    val backStackEntry by navController.currentBackStackEntryAsState()
    // Get the name of the current screen
    val currentScreen = YNMScreen.valueOf(
        backStackEntry?.destination?.route ?: YNMScreen.Action.name
    )
    NavHost(
        navController = navController,
        startDestination = YNMScreen.Action.name,
    ){
        composable (route = YNMScreen.Action.name) {
            ActionScreen ( onNextButtonClicked = {navController.navigate(YNMScreen.Recycle.name)},
                onBackButtonClicked = {navController.navigateUp()})
        }
        composable (route = YNMScreen.Recycle.name) {
            RecycleScreen(onBackButtonClicked = {navController.navigateUp()})
        }
    }
}