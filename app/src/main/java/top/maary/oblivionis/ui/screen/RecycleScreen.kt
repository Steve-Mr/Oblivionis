package top.maary.oblivionis.ui.screen

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.video.VideoFrameDecoder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import top.maary.oblivionis.R
import top.maary.oblivionis.data.PreferenceRepository
import top.maary.oblivionis.ui.Dialog
import top.maary.oblivionis.ui.MediaPlayer
import top.maary.oblivionis.ui.PlaceHolder
import top.maary.oblivionis.viewmodel.ActionViewModel
import top.maary.oblivionis.viewmodel.NotificationViewModel
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecycleScreen(
    actionViewModel: ActionViewModel,
    notificationViewModel: NotificationViewModel,
    onBackButtonClicked: () -> Unit) {

    val context = LocalContext.current
    val dataStore = PreferenceRepository(context)
    val scope = rememberCoroutineScope()

    val images = actionViewModel.uiState.map { it.markedImages }.collectAsState(initial = emptyList())

    val intentSender = actionViewModel.pendingDeleteIntentSender.collectAsState(initial = null)

    val imageLoader = ImageLoader.Builder(LocalContext.current)
        .components {
            add(VideoFrameDecoder.Factory())
        }
        .build()

    // 创建删除请求的 launcher
    val deleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->

        // 处理结果
        if (result.resultCode == Activity.RESULT_OK) {
            // 删除成功后的逻辑
            actionViewModel.deletePendingImage()
        }
    }

    LaunchedEffect(intentSender.value) {
        intentSender.value?.let { intentSender ->
            deleteLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
        }
    }

    val selectedItems = remember { mutableStateOf(mutableSetOf<Int>()) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,

        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    scrolledContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
                navigationIcon = {
                    FilledTonalButton(onClick = { onBackButtonClicked() },
                        modifier = Modifier.padding(start = 8.dp)) {
                        Icon(
                            modifier = Modifier.padding(end = 8.dp),
                            painter = painterResource(
                                id = R.drawable.ic_close),
                            contentDescription = stringResource(
                                id = R.string.close)
                        )
                        Text(stringResource(id = R.string.close))
                    }
                },
                actions = {
                    val openDialog = remember {
                        mutableStateOf(false)
                    }
                    if (openDialog.value) {
                        Dialog(
                            onDismissRequest = { openDialog.value = false },
                            onConfirmation = {
                                // 直接调用 ViewModel 的方法，移除 runBlocking 和 scope.launch
                                actionViewModel.deleteMarkedImagesAndRescheduleNotification(
                                    images.value,
                                    dataStore,
                                    notificationViewModel
                                )
                                openDialog.value = false
                            },
                            dialogText = stringResource(id = R.string.deleteAllConfirmation)
                        )
                    }
                    if (selectedItems.value.isNotEmpty()) {
                        FilledTonalButton(
                            onClick = {
                                if (selectedItems.value.size != images.value.size) {
                                    selectedItems.value = images.value.indices.toMutableSet()
                                }else{
                                    selectedItems.value = mutableSetOf()
                                } },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(stringResource(id = R.string.select_all))
                        }
                    } else {
                        FilledTonalButton(
                            enabled = images.value.isNotEmpty(),
                            onClick = { openDialog.value = true },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(stringResource(id = R.string.deleteAll))
                        }
                    }
                },
                title = {}
            )
        },
        floatingActionButton = {
            val openDialog = remember {
                mutableStateOf(false)
            }
            if (selectedItems.value.isNotEmpty()) {
                FloatingActionButton(onClick = { openDialog.value = true }) {
                    if (openDialog.value) {
                        Dialog(
                            onDismissRequest = { openDialog.value = false },
                            onConfirmation = {
                                selectedItems.value.forEach { index ->
                                    actionViewModel.unMarkImage(images.value[index])
                                }
                                selectedItems.value = mutableSetOf()
                                openDialog.value = false
                            },
                            dialogText = stringResource(id = R.string.restore_selected_confirmation)
                        )
                    }
                    Icon(
                        painter = painterResource(id = R.drawable.ic_restore_all),
                        contentDescription = stringResource(
                            id = R.string.restoreAllConfirmation
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        val openDialog = remember {
            mutableStateOf(false)
        }

        val clickedIndex = remember { mutableIntStateOf(-1) }

        if (images.value.isEmpty()) {
            PlaceHolder(modifier = Modifier, stringResource = R.string.nothing_to_do)
            return@Scaffold
        }

        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(3),
            content = {
                item(span = StaggeredGridItemSpan.FullLine ) {
                    Spacer(
                        Modifier.height(innerPadding.calculateTopPadding())
                    )
                }
                items(images.value.size) { index ->

                    if (openDialog.value) {
                        Dialog(
                            onDismissRequest = { openDialog.value = false },
                            onConfirmation = {
                                actionViewModel.unMarkImage(images.value[clickedIndex.intValue])
                                clickedIndex.intValue = -1
                                openDialog.value = false
                            },
                            dialogText = stringResource(id = R.string.restoreConfirmation)
                        )
                    }

                    MediaPlayer(
                        modifier = Modifier
                            .padding(2.dp)
                            .background(Color.Transparent),
                        uri = images.value[index].contentUri,
                        isMultiSelectionState = selectedItems.value.isNotEmpty(),
                        isSelected = selectedItems.value.contains(index),
                        imageLoader = imageLoader,
                        onMediaClick = {
                            if (selectedItems.value.isNotEmpty()) {
                                val newSet = selectedItems.value.toMutableSet() // 创建一个新集合
                                if (!newSet.add(index)) {
                                    newSet.remove(index) // 如果元素已存在，则移除
                                }
                                selectedItems.value = newSet // 更新 `selectedItems.value`，触发重组
                                return@MediaPlayer
                            }
                            clickedIndex.intValue = index
                            openDialog.value = true
                        },
                        onLongPress = {
                            val newSet = selectedItems.value.toMutableSet() // 创建一个新集合
                            if (!newSet.add(index)) {
                                newSet.remove(index) // 如果元素已存在，则移除
                            }
                            selectedItems.value = newSet // 更新 `selectedItems.value`，触发重组

                        })

                }
                item(span = StaggeredGridItemSpan.FullLine ) {
                    Spacer(
                        Modifier.windowInsetsBottomHeight(
                            WindowInsets.systemBars
                        )
                    )
                }
            },
            modifier = Modifier
                .fillMaxSize()
        )
    }
}