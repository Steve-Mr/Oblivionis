package top.maary.oblivionis.ui.screen

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    onReWelcomeClick: () -> Unit,
    onBackButtonClicked: () -> Unit
) {
    Button( onClick = onReWelcomeClick, modifier = Modifier.padding(32.dp)) { }
//    onReWelcomeClick()

}