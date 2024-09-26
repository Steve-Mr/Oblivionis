package top.maary.oblivionis.ui.screen

import androidx.compose.material3.Button
import androidx.compose.runtime.Composable

@Composable
fun SettingsScreen(
    onReWelcomeClick: () -> Unit,
    onBackButtonClicked: () -> Unit
) {
    Button(onReWelcomeClick) { }
//    onReWelcomeClick()

}