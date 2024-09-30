package top.maary.oblivionis

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import top.maary.oblivionis.data.PreferenceRepository
import top.maary.oblivionis.ui.ActionScreen
import top.maary.oblivionis.ui.EntryScreen
import top.maary.oblivionis.ui.RecycleScreen
import top.maary.oblivionis.ui.WelcomeScreen
import top.maary.oblivionis.ui.screen.SettingsScreen
import top.maary.oblivionis.viewmodel.ActionViewModel
import top.maary.oblivionis.viewmodel.NotificationViewModel

enum class OblivionisScreen() { Welcome, Entry, Action, Recycle, Settings }

@Composable
fun OblivionisApp(
    navController: NavHostController = rememberNavController(),
    actionViewModel: ActionViewModel = viewModel(
        factory = ActionViewModel.Factory
    ),
    notificationViewModel: NotificationViewModel = viewModel()
) {

    val context = LocalContext.current
    val dataStore = PreferenceRepository(context)
    val scope = rememberCoroutineScope()

    val permissionGranted = runBlocking { dataStore.permissionGranted.first() }

    val isReWelcome = dataStore.isReWelcome.collectAsState(initial = false)

    val welcomeScreenNextDest = remember { mutableStateOf(OblivionisScreen.Entry.name) }

    fun welcomePermissionBasicLogic() {
        scope.launch { dataStore.setPermissionGranted(true) }
        actionViewModel.loadAlbums()
        Log.v("OBLIVIONIS", "PERMISSION ${runBlocking { dataStore.permissionGranted.first() }}")
        navController.navigate(welcomeScreenNextDest.value) {
            popUpTo(OblivionisScreen.Welcome.name) {
                inclusive = true // 将 WelcomeScreen 从栈中移除
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination =
        if (runBlocking { dataStore.permissionGranted.first() }) {
            Log.v("OBLIVIONIS", "GO TO ENTRY")

            OblivionisScreen.Entry.name
        }
                else OblivionisScreen.Welcome.name,
    ){
        composable (route = OblivionisScreen.Welcome.name) {
            Log.v("OBLIVIONIS", "PERMISSION 2")

            WelcomeScreen (onPermissionFinished = {
                if (isReWelcome.value) return@WelcomeScreen
                welcomePermissionBasicLogic()
            } , onFabClicked = {
                welcomePermissionBasicLogic()
            })
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
                notificationViewModel = notificationViewModel,
                onBackButtonClicked = {navController.popBackStack()})
        }
        composable (route = OblivionisScreen.Settings.name) {
            SettingsScreen(
                onReWelcomeClick = {
                    scope.launch { dataStore.setReWelcome(true) }
                    welcomeScreenNextDest.value = OblivionisScreen.Settings.name
                    navController.navigate(OblivionisScreen.Welcome.name) },
                onBackButtonClicked = { navController.popBackStack() },
                notificationViewModel = notificationViewModel
            )
        }
    }
}