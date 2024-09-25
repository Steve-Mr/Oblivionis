package top.maary.oblivionis

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import top.maary.oblivionis.ui.ActionScreen
import top.maary.oblivionis.ui.EntryScreen
import top.maary.oblivionis.ui.RecycleScreen
import top.maary.oblivionis.ui.WelcomeScreen
import top.maary.oblivionis.viewmodel.ActionViewModel

enum class OblivionisScreen() { Welcome, Entry, Action, Recycle }

@Composable
fun OblivionisApp(
    navController: NavHostController = rememberNavController(),
    actionViewModel: ActionViewModel = viewModel(
        factory = ActionViewModel.Factory
    )
) {

    NavHost(
        navController = navController,
        startDestination = OblivionisScreen.Welcome.name,
    ){
        composable (route = OblivionisScreen.Welcome.name) {
            WelcomeScreen (onPermissionFinished = {
                actionViewModel.loadAlbums()
                navController.navigate(OblivionisScreen.Entry.name) {
                    popUpTo(OblivionisScreen.Welcome.name) {
                        inclusive = true // 将 WelcomeScreen 从栈中移除
                    }
            } })
        }
        composable (route = OblivionisScreen.Entry.name) {
            EntryScreen(
                viewModel = actionViewModel,
                onClick = {
                    actionViewModel.loadImages()
                    navController.navigate(OblivionisScreen.Action.name) }
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
    }
}