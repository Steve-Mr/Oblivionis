package com.example.ydaynomore

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ydaynomore.ui.ActionScreen
import com.example.ydaynomore.ui.EntryScreen
import com.example.ydaynomore.ui.RecycleScreen
import com.example.ydaynomore.ui.WelcomeScreen
import com.example.ydaynomore.ui.rememberCanManageMediaState
import com.example.ydaynomore.viewmodel.ActionViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

enum class YNMScreen() { Welcome, Entry, Action, Recycle }

@Composable
fun YNMApp(
    navController: NavHostController = rememberNavController(),
    actionViewModel: ActionViewModel = viewModel(
        factory = ActionViewModel.Factory
    )
) {

    NavHost(
        navController = navController,
        startDestination = YNMScreen.Welcome.name,
    ){
        composable (route = YNMScreen.Welcome.name) {
            WelcomeScreen (onPermissionFinished = {
                navController.navigate(YNMScreen.Entry.name) {
                    popUpTo(YNMScreen.Welcome.name) {
                        inclusive = true // 将 WelcomeScreen 从栈中移除
                    }
            } })
        }
        composable (route = YNMScreen.Entry.name) {
            EntryScreen(
                viewModel = actionViewModel,
                onClick = {
                    actionViewModel.loadImages()
                    navController.navigate(YNMScreen.Action.name) }
            )
        }
        composable (route = YNMScreen.Action.name) {
            ActionScreen (
                viewModel = actionViewModel,
                onNextButtonClicked = { navController.navigate(YNMScreen.Recycle.name)},
                onBackButtonClicked = { navController.navigateUp() })
        }
        composable (route = YNMScreen.Recycle.name) {
            RecycleScreen(
                actionViewModel = actionViewModel,
                onBackButtonClicked = {navController.navigateUp()})
        }
    }
}