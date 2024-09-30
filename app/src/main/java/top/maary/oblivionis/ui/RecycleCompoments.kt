package top.maary.oblivionis.ui

import android.app.Activity
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.ImageLoader
import coil3.video.VideoFrameDecoder
import top.maary.oblivionis.R
import top.maary.oblivionis.viewmodel.ActionViewModel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import top.maary.oblivionis.data.PreferenceRepository
import top.maary.oblivionis.viewmodel.NotificationViewModel
import java.util.Calendar
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecycleScreen(
    actionViewModel: ActionViewModel,
    notificationViewModel: NotificationViewModel,
    onBackButtonClicked: () -> Unit) {

    val context = LocalContext.current
    val dataStore = PreferenceRepository(context)
    val scope = rememberCoroutineScope()

    val images = actionViewModel.markedImages.collectAsState(initial = emptyList())

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
                                if (runBlocking { !dataStore.intervalStartFixed.first() }){
                                    scope.launch {
                                        val calendar = Calendar.getInstance()
                                        val notificationTime = runBlocking { dataStore.notificationTime.first() }
                                        val timeParts = notificationTime.split(":").map { it.toInt() }
                                        dataStore.setIntervalStart(calendar.get(Calendar.DAY_OF_MONTH))
                                        notificationViewModel.scheduleNotification(
                                            date = calendar.get(Calendar.DAY_OF_MONTH),
                                            hour = timeParts[0],
                                            minute = timeParts[1],
                                            interval = runBlocking { dataStore.notificationInterval.first().toLong() }
                                        )
                                    }
                                }

                                actionViewModel.deleteImages(images.value)
                                openDialog.value = false },
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
            PlaceHolder(modifier = Modifier.padding(innerPadding), stringResource = R.string.nothing_to_do)
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
                        val isSelected by remember { derivedStateOf { selectedItems.value.contains(index) }}

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
                                .background(if (isSelected) Color.Gray else Color.Transparent),
                            uri = images.value[index].contentUri,
                            isMultiSelectionState = selectedItems.value.isNotEmpty(),
                            isSelected = selectedItems.value.contains(index),
                            imageLoader = imageLoader,
                            onImageClick = {
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
                            onVideoClick = {
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

fun Modifier.swipeUpToDismiss(
    onDismissed: () -> Unit
): Modifier = composed {
    val offsetY = remember { Animatable(0f) }
    pointerInput(Unit) {

        // Used to calculate fling decay.
        val decay = splineBasedDecay<Float>(this)
        // Use suspend functions for touch events and the Animatable.
        coroutineScope {
            while (true) {
                val velocityTracker = VelocityTracker()
                // Stop any ongoing animation.
                offsetY.stop()
                awaitPointerEventScope {
                    // Detect a touch down event.
                    val pointerId = awaitFirstDown().id

                    verticalDrag(pointerId) { change ->
                        launch {
                            offsetY.snapTo(
                                offsetY.value + change.positionChange().y
                            )
                        }
                        velocityTracker.addPosition(
                            change.uptimeMillis,
                            change.position
                        )
                    }
                }
                // No longer receiving touch events. Prepare the animation.
                val velocity = velocityTracker.calculateVelocity().y
                val targetOffsetY = decay.calculateTargetValue(
                    offsetY.value,
                    velocity
                )

                offsetY.updateBounds(
                    lowerBound = -size.height.toFloat(),
                    upperBound = size.height.toFloat()
                )
                launch {
                    if (targetOffsetY.absoluteValue <= size.height) {
                        offsetY.animateTo(
                            targetValue = 0f,
                            initialVelocity = velocity
                        )
                    } else {
                        offsetY.animateDecay(velocity, decay)
                        onDismissed()
                    }
                }
            }
        }
    }
        .offset { IntOffset(0, offsetY.value.roundToInt()) }
}

//@Preview(showBackground = true, showSystemUi = true)
//@Composable
//fun RecycleScreenPreview() {
//    RecycleScreen(onBackButtonClicked = {})
//}