package top.maary.oblivionis.ui.screen

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import coil3.ImageLoader
import coil3.video.VideoFrameDecoder
import top.maary.oblivionis.R
import top.maary.oblivionis.data.MediaEntity
import top.maary.oblivionis.data.PreferenceRepository
import top.maary.oblivionis.ui.Dialog
import top.maary.oblivionis.ui.MediaPlayer
import top.maary.oblivionis.ui.PlaceHolder
import top.maary.oblivionis.viewmodel.ActionViewModel
import top.maary.oblivionis.viewmodel.NotificationViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RecycleScreen(
    actionViewModel: ActionViewModel,
    notificationViewModel: NotificationViewModel,
    onBackButtonClicked: () -> Unit) {

    val context = LocalContext.current
    val dataStore = PreferenceRepository(context)

    val lazyPagingItems: LazyPagingItems<MediaEntity> =
        actionViewModel.markedImagePagingDataFlow.collectAsLazyPagingItems()

    // --- 状态管理 ---
    // 1. 是否进入了“多选模式”
    val selectionModeActive = rememberSaveable { mutableStateOf(false) }
    // 2. "全选模式"是否激活
    val selectAllActive = rememberSaveable { mutableStateOf(false) }
    // 3. 在非全选模式下，用户手动选择的项 (存ID)
    val individuallySelectedIds = remember { mutableStateOf(setOf<Long>()) }
    // 4. 在全选模式下，用户反向取消选择的项 (存ID)
    val deselectedInSelectAllMode = remember { mutableStateOf(setOf<Long>()) }

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
            actionViewModel.onDeletionCompleted()
        }
    }

    LaunchedEffect(intentSender.value) {
        intentSender.value?.let { intentSender ->
            deleteLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
        }
    }

    BackHandler(enabled = selectionModeActive.value) {
        // 3. 在这里定义返回键被拦截后的行为：退出选择模式
        selectionModeActive.value = false
        selectAllActive.value = false
        individuallySelectedIds.value = setOf()
        deselectedInSelectAllMode.value = setOf()
    }

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
                                    dataStore,
                                    notificationViewModel
                                )
                                openDialog.value = false
                            },
                            dialogText = stringResource(id = R.string.deleteAllConfirmation)
                        )
                    }
                    if (selectionModeActive.value) {
                        // 进入选择模式后，显示“全部选择”或“取消全选”
                        FilledTonalButton(
                            onClick = {
                                selectAllActive.value = !selectAllActive.value
                                // 切换模式时，清空所有单选/反选记录
                                individuallySelectedIds.value = setOf()
                                deselectedInSelectAllMode.value = setOf()
                            },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(stringResource(if (selectAllActive.value) R.string.deselect_all else R.string.select_all))
                        }
                    } else {
                        FilledTonalButton(
                            enabled = lazyPagingItems.itemCount > 0,
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
            if (selectionModeActive.value) {
                FloatingActionButton(onClick = { openDialog.value = true }) {
                    if (openDialog.value) {
                        Dialog(
                            onDismissRequest = { openDialog.value = false },
                            onConfirmation = {
                                if (selectAllActive.value) {
                                    actionViewModel.unmarkAllImages(excludeIds = deselectedInSelectAllMode.value)
                                } else {
                                    val itemsToRestore = mutableListOf<MediaEntity>()
                                    for (i in 0 until lazyPagingItems.itemCount) {
                                        val item = lazyPagingItems.peek(i)
                                        if (item != null && item.id in individuallySelectedIds.value) {
                                            itemsToRestore.add(item)
                                        }
                                    }
                                    itemsToRestore.forEach { actionViewModel.unMarkImage(it) }
                                }
                                // 操作完成后，重置所有状态
                                selectionModeActive.value = false
                                selectAllActive.value = false
                                individuallySelectedIds.value = setOf()
                                deselectedInSelectAllMode.value = setOf()
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

        if (lazyPagingItems.loadState.refresh is LoadState.Loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }
            return@Scaffold // 提前返回，下方的代码不会执行
        }

        if (lazyPagingItems.loadState.refresh is LoadState.Error) {
            PlaceHolder(modifier = Modifier, stringResource = R.string.error_loading)
            return@Scaffold // 提前返回
        }

        if (lazyPagingItems.itemCount == 0) {
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
                items(
                    count = lazyPagingItems.itemCount,
                    key = lazyPagingItems.itemKey { it.id }
                ) { index ->
                    val image = lazyPagingItems[index] ?: return@items

                    if (openDialog.value) {
                        Dialog(
                            onDismissRequest = { openDialog.value = false },
                            onConfirmation = {
                                actionViewModel.unMarkImage(image)
                                clickedIndex.intValue = -1
                                openDialog.value = false
                            },
                            dialogText = stringResource(id = R.string.restoreConfirmation)
                        )
                    }


                    val isSelected = if (selectAllActive.value) {
                        image.id !in deselectedInSelectAllMode.value
                    } else {
                        image.id in individuallySelectedIds.value
                    }

                    MediaPlayer(
                        modifier = Modifier
                            .padding(2.dp)
                            .background(Color.Transparent),
                        uri = image.contentUri,
                        isMultiSelectionState = selectionModeActive.value,
                        isSelected = isSelected,
                        imageLoader = imageLoader,
                        onMediaClick = {
                            if (!selectionModeActive.value) {
                                clickedIndex.intValue = index
                                openDialog.value = true
                                return@MediaPlayer
                            }
                            if (selectAllActive.value) {
                                val newDeselected = deselectedInSelectAllMode.value.toMutableSet()
                                if (!newDeselected.add(image.id)) newDeselected.remove(image.id)
                                deselectedInSelectAllMode.value = newDeselected
                            } else {
                                val newSelected = individuallySelectedIds.value.toMutableSet()
                                if (!newSelected.add(image.id)) newSelected.remove(image.id)
                                individuallySelectedIds.value = newSelected
                            }

                        },
                        onLongPress = {
                            if (!selectionModeActive.value) {
                                selectionModeActive.value = true
                            }
                            val newSelected = individuallySelectedIds.value.toMutableSet()
                            newSelected.add(image.id)
                            individuallySelectedIds.value = newSelected
                            // 进入单选模式时，确保全选模式是关闭的
                            selectAllActive.value = false
                            deselectedInSelectAllMode.value = setOf()
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