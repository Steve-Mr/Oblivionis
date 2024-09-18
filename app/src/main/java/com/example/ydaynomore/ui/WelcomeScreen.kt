package com.example.ydaynomore.ui

import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun WelcomeScreen(onPermissionFinished: () -> Unit) {

    Scaffold { innerPadding ->
        val context = LocalContext.current

        val storagePermissionState =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                rememberMultiplePermissionsState(permissions = listOf(android.Manifest.permission.READ_MEDIA_IMAGES, android.Manifest.permission.READ_MEDIA_VIDEO))
            } else {
                rememberMultiplePermissionsState(permissions = listOf(android.Manifest.permission.READ_EXTERNAL_STORAGE))
            }

        val manageMediaPermissionState = rememberCanManageMediaState()

        var permissionHandled by remember { mutableStateOf(false) }

        if (!permissionHandled) {
            if (storagePermissionState.allPermissionsGranted and manageMediaPermissionState.value) {
                permissionHandled = true
                onPermissionFinished()
            } else {
                Column (modifier = Modifier.fillMaxSize().padding(innerPadding),
                    verticalArrangement = Arrangement.SpaceEvenly,
                    horizontalAlignment = Alignment.CenterHorizontally){
                    if (!storagePermissionState.allPermissionsGranted) {
                        Button(onClick = { storagePermissionState.launchMultiplePermissionRequest() }) {
                            Text(text = "Read Images Permission")
                        }
                    }
                    if (!manageMediaPermissionState.value) {
                        Button(onClick = {
                            val intent = Intent(Settings.ACTION_REQUEST_MANAGE_MEDIA)
                            context.startActivity(intent)
                        }) {
                            Text(text = "Manage Images Permission")
                        }
                    }
                }
            }
        }
        Log.v("YDNM", "1")
    }



}

@Composable
fun rememberCanManageMediaState(): State<Boolean> {
    val context = LocalContext.current
    var canManageMedia by remember { mutableStateOf(false) }

    LaunchedEffect(context) {
        canManageMedia = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // 检查应用是否可以管理媒体
            MediaStore.canManageMedia(context)
        } else {
            true // 如果版本低于 Android 12，默认返回 true
        }
    }
    Log.v("YDNM", "2")

    return rememberUpdatedState(newValue = canManageMedia)
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun WelcomePreview() {
    WelcomeScreen {

    }
}