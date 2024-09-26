package top.maary.oblivionis.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.maary.oblivionis.R

@Composable
fun PermissionBlock(
    title: String,
    onClick: () -> Unit,
    details: String,
    isOptional: Boolean,
    granted: Boolean
) {
    Card(modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row (horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(text = title, style = MaterialTheme.typography.titleLarge)
                    Text(text = stringResource(
                        if (isOptional) R.string.is_optional
                        else R.string.not_optional
                    ), fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.weight(1f))
                Button(onClick = onClick, enabled = !granted) {
                    Text(text = stringResource(
                        if (granted) R.string.granted
                        else R.string.grant
                    ))
                }
            }

            Text(text = details, modifier = Modifier.padding(top = 8.dp))

        }
    }
}