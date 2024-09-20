package com.example.ydaynomore

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ydaynomore.ui.ActionScreen
import com.example.ydaynomore.ui.RecycleScreen
import com.example.ydaynomore.ui.WelcomeScreen
import com.example.ydaynomore.ui.rememberCanManageMediaState
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

enum class YNMScreen() { Welcome, Action, Recycle }

@Composable
fun YNMApp(navController: NavHostController = rememberNavController()) {

    NavHost(
        navController = navController,
        startDestination = YNMScreen.Welcome.name,
    ){
        composable (route = YNMScreen.Welcome.name) {
            WelcomeScreen (onPermissionFinished = {
                navController.navigate(YNMScreen.Action.name) {
                    popUpTo(YNMScreen.Welcome.name) {
                        inclusive = true // 将 WelcomeScreen 从栈中移除
                    }
            } })
        }
        composable (route = YNMScreen.Action.name) {
            ActionScreen ( onNextButtonClicked = { navController.navigate(YNMScreen.Recycle.name)},
                onBackButtonClicked = { /* TODO */ })
        }
        composable (route = YNMScreen.Recycle.name) {
            RecycleScreen(onBackButtonClicked = {navController.navigateUp()})
        }
    }
}