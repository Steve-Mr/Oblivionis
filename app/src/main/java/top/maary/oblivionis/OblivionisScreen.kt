package top.maary.oblivionis

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import top.maary.oblivionis.ui.ActionScreen
import top.maary.oblivionis.ui.EntryScreen
import top.maary.oblivionis.ui.RecycleScreen
import top.maary.oblivionis.ui.WelcomeScreen
import top.maary.oblivionis.ui.screen.SettingsScreen
import top.maary.oblivionis.viewmodel.ActionViewModel

enum class OblivionisScreen() { Welcome, Entry, Action, Recycle, Settings }

@Composable
fun OblivionisApp(
    navController: NavHostController = rememberNavController(),
    actionViewModel: ActionViewModel = viewModel(
        factory = ActionViewModel.Factory
    )
) {

    val welcomeScreenNextDest = remember { mutableStateOf(OblivionisScreen.Entry.name) }

    NavHost(
        navController = navController,
        startDestination = OblivionisScreen.Welcome.name,
    ){
        composable (route = OblivionisScreen.Welcome.name) {
            WelcomeScreen (onPermissionFinished = {
                actionViewModel.loadAlbums()
                navController.navigate(welcomeScreenNextDest.value) {
                    popUpTo(OblivionisScreen.Welcome.name) {
                        inclusive = true // 将 WelcomeScreen 从栈中移除
                    }
            } })
        }
        composable (route = OblivionisScreen.Entry.name) {
            EntryScreen(
                viewModel = actionViewModel,
                onAlbumClick = {
                    actionViewModel.loadImages()
                    navController.navigate(OblivionisScreen.Action.name) },
                onSettingsClick = { navController.navigate(OblivionisScreen.Settings.name) }
            )
        }
        composable (route = OblivionisScreen.Action.name) {
            ActionScreen (
                viewModel = actionViewModel,
                onNextButtonClicked = { navController.navigate(OblivionisScreen.Recycle.name)},
                onBackButtonClicked = { navController.popBackStack() })
        }
        composable (route = OblivionisScreen.Recycle.name) {
            RecycleScreen(
                actionViewModel = actionViewModel,
                onBackButtonClicked = {navController.popBackStack()})
        }
        composable (route = OblivionisScreen.Settings.name) {
            SettingsScreen(
                onReWelcomeClick = {
                    welcomeScreenNextDest.value = OblivionisScreen.Settings.name
                    navController.navigate(OblivionisScreen.Welcome.name) },
                onBackButtonClicked = { navController.popBackStack() }
            )
        }
    }
}