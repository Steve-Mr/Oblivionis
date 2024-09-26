package top.maary.oblivionis.ui

import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import top.maary.oblivionis.R

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun WelcomeScreen(onPermissionFinished: () -> Unit) {
    val context = LocalContext.current

    val storagePermissionState =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            rememberMultiplePermissionsState(permissions = listOf(android.Manifest.permission.READ_MEDIA_IMAGES, android.Manifest.permission.READ_MEDIA_VIDEO))
        } else {
            rememberMultiplePermissionsState(permissions = listOf(android.Manifest.permission.READ_EXTERNAL_STORAGE))
        }

    val manageMediaPermissionState = rememberCanManageMediaState()

    var permissionHandled by remember { mutableStateOf(false) }

    Scaffold (
        topBar = {
            LargeTopAppBar(
                modifier = Modifier.fillMaxHeight(0.3f).shadow(10.dp),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
                title = {
                    Box (modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.BottomStart) {
                        Text(text = stringResource(R.string.welcome))
                    }
                },
                scrollBehavior = null
            )
        },
        floatingActionButton = {
            if (storagePermissionState.allPermissionsGranted) {
                FloatingActionButton(onClick = { onPermissionFinished() }) {
                    Icon(painter = painterResource(R.drawable.ic_continue),
                        contentDescription = stringResource(R.string.finish_setting))
                }
            }
        }
    ) { innerPadding ->

        if (!permissionHandled) {
            if (storagePermissionState.allPermissionsGranted and manageMediaPermissionState.value) {
                permissionHandled = true
                onPermissionFinished()
            } else {
                LazyColumn (modifier = Modifier.fillMaxWidth().padding(innerPadding),
                    horizontalAlignment = Alignment.CenterHorizontally){
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    item {
                        PermissionBlock(
                            title = stringResource(R.string.permission_media),
                            onClick = { storagePermissionState.launchMultiplePermissionRequest() },
                            details = stringResource(R.string.permission_media_detail),
                            isOptional = false,
                            granted = storagePermissionState.allPermissionsGranted
                        )
                    }
                    item {
                        PermissionBlock(
                            title = stringResource(R.string.permission_manage_media),
                            onClick = {
                                val intent = Intent(Settings.ACTION_REQUEST_MANAGE_MEDIA)
                                context.startActivity(intent)
                            },
                            details = stringResource(R.string.permission_manage_media_detail),
                            isOptional = true,
                            granted = manageMediaPermissionState.value
                        )
                    }

                }
            }
        }
    }
}

@Composable
fun rememberCanManageMediaState(): State<Boolean> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()
    var canManageMedia by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        canManageMedia = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // 检查应用是否可以管理媒体
            MediaStore.canManageMedia(context)
        } else {
            true // 如果版本低于 Android 12，默认返回 true
        }
    }

    return rememberUpdatedState(newValue = canManageMedia)
}