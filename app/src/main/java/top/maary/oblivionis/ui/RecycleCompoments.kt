package top.maary.oblivionis.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.splineBasedDecay
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
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
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecycleScreen(
    actionViewModel: ActionViewModel = viewModel(),
    onBackButtonClicked: () -> Unit) {

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
                                actionViewModel.deleteImages(images.value)
                                openDialog.value = false },
                            dialogText = stringResource(id = R.string.deleteAllConfirmation)
                        )
                    }
                    FilledTonalButton(onClick = { openDialog.value = true },
                        modifier = Modifier.padding(end = 8.dp)) {
                        Text(stringResource(id = R.string.deleteAll))
                    }
                },
                title = {}
            )
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
                                .padding(2.dp),
                            uri = images.value[index].contentUri,
                            imageLoader = imageLoader,
                            onImageClick = {
                                clickedIndex.intValue = index
                                openDialog.value = true
                            },
                            onVideoClick = {
                                clickedIndex.intValue = index
                                openDialog.value = true
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

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun RecycleScreenPreview() {
    RecycleScreen(onBackButtonClicked = {})
}