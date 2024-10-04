package top.maary.oblivionis.ui

import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BadgeDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.ImageLoader
import coil3.compose.AsyncImage
import io.sanghun.compose.video.RepeatMode
import io.sanghun.compose.video.VideoPlayer
import io.sanghun.compose.video.controller.VideoPlayerControllerConfig
import io.sanghun.compose.video.uri.VideoPlayerMediaItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import top.maary.oblivionis.R

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaPlayer(
    modifier: Modifier,
    uri: Uri,
    imageLoader: ImageLoader,
    isMultiSelectionState: Boolean = false,
    isSelected: Boolean = false, // 用于表示当前项是否被选中
    onMediaClick: () -> Unit = {},
    onLongPress: () -> Unit = {} // 长按事件
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .wrapContentSize()
            .combinedClickable(
                onLongClick = { onLongPress() },
                onClick = { onMediaClick() }
            )
    ) {
        when {
            // 处理图片显示
            uri.toString().startsWith(MediaStore.Images.Media.EXTERNAL_CONTENT_URI.toString()) -> {
                AsyncImage(
                    model = uri,
                    contentDescription = "",
                    modifier = modifier.clip(RoundedCornerShape(8.dp))
                )
            }

            // 处理视频显示
            uri.toString().startsWith(MediaStore.Video.Media.EXTERNAL_CONTENT_URI.toString()) -> {
                VideoViewAlt(
                    modifier = modifier.clip(RoundedCornerShape(8.dp)),
                    uri = uri,
                    imageLoader = imageLoader
                )
            }
        }

        if (isMultiSelectionState) {
            // 显示选择框的图标
            Icon(
                imageVector = Icons.Default.Circle,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(48.dp)
                    .padding(8.dp)
            )
            Icon(
                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = if (isSelected) "Selected" else "Not Selected",
                modifier = Modifier
                    .align(Alignment.TopEnd) // 选择框显示在右上角
                    .size(48.dp)
                    .padding(8.dp),
                tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray // 设置选中的颜色
            )
        }

    }
}


@Composable
fun ActionRow(
    modifier: Modifier,
    delButtonClickable: Boolean = true,
    onDelButtonLongClicked: () -> Unit,
    onDelButtonClicked: () -> Unit,
    onRollBackButtonClicked: () -> Unit,
    onShareButtonClicked: () -> Unit,
    showRestore: Boolean,
    currentPage: Int,
    pagesCount: Int
) {
    val interactionSource = remember { MutableInteractionSource() }

    val viewConfiguration = LocalViewConfiguration.current

    var currentProgress by remember { mutableFloatStateOf(0f) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope() // Create a coroutine scope

    LaunchedEffect(interactionSource) {
        var isLongClick = false
        var longClicked = false
        interactionSource.interactions.collectLatest { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    delay(500)
                    isLongClick = false
                    longClicked = true
                    loading = true
                    currentProgress = 0f  // Reset progress

                    // Launch a coroutine to update the progress over time
                    scope.launch {
                        val steps = 100  // The number of steps to reach full progress
                        val stepDelay =
                            viewConfiguration.longPressTimeoutMillis / steps  // Time per step

                        repeat(steps) {
                            delay(stepDelay)  // Wait for the next step
                            currentProgress += 1f / steps  // Update progress

                            // If the press was released, stop updating progress
                            if (!loading) {
                                currentProgress = 0f  // Reset progress
                                return@launch
                            }
                        }

                        // Once long press is recognized
                        isLongClick = true
                        longClicked = false
                        loading = false
                        currentProgress = 1f  // Complete the progress
                        onDelButtonLongClicked()  // Trigger long click action
                    }
                }

                is PressInteraction.Release -> {
                    if (isLongClick.not()) {
                        loading = false
                        currentProgress = 0f  // Reset progress when the press is released early
                        if (!longClicked) onDelButtonClicked()
                        else longClicked = false
                    }
                    isLongClick = false
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp), contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .wrapContentWidth()
        ) {
            OutlinedButton(
                onClick = {},
                modifier = Modifier.height(48.dp),
                enabled = (pagesCount != 0)
            ) {
                Text(
                    modifier = Modifier.padding(start = 8.dp, end = 32.dp),
                    text = stringResource(R.string.pager_count,
                        if (pagesCount == 0) 0 else currentPage, pagesCount)
                )
            }
            if (loading) {
                LinearProgressIndicator(
                    progress = { currentProgress },
                    modifier = Modifier
                        .height(48.dp)
                        .matchParentSize()
                )
            }
            Button(
                onClick = { },
                enabled = delButtonClickable,
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.CenterEnd),
                colors = ButtonDefaults.buttonColors(containerColor = BadgeDefaults.containerColor),
                elevation = ButtonDefaults.buttonElevation(10.dp),
                interactionSource = interactionSource
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(id = R.string.mark_this_to_delete),
                )
            }
        }

        Button(
            onClick = onShareButtonClicked,
            enabled = true,
            shape = CircleShape,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier
                .size(48.dp)
                .align(Alignment.CenterEnd),
            colors = ButtonDefaults.outlinedButtonColors()
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_share),
                contentDescription = stringResource(R.string.share_media)
            )
        }

        if (showRestore) {
            Button(
                onClick = onRollBackButtonClicked,
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(48.dp),
                colors = ButtonDefaults.filledTonalButtonColors(),
                elevation = ButtonDefaults.buttonElevation(10.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_restore),
                    contentDescription = stringResource(
                        id = R.string.restore_last_deleted
                    )
                )
            }
        }

    }
}

@Composable
fun ExoPlayerView(
    modifier: Modifier = Modifier,
    uri: Uri, onClick: () -> Unit
) {
    // Get the current context
    val context = LocalContext.current

    // Initialize ExoPlayer
    val exoPlayer = ExoPlayer.Builder(context).build()

    // Create a MediaSource
    val mediaSource = remember(uri) {
        MediaItem.fromUri(uri)
    }

    // Set MediaSource to ExoPlayer
    LaunchedEffect(mediaSource) {
        exoPlayer.setMediaItem(mediaSource)
        exoPlayer.prepare()
    }

    // Manage lifecycle events
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    // Use AndroidView to embed an Android View (PlayerView) into Compose
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
            }
        },
        modifier = modifier
            .clickable { onClick() }
    )
}

@Composable
fun VideoView(
    modifier: Modifier,
    uri: Uri
) {
    VideoPlayer(
        mediaItems = listOf(
            VideoPlayerMediaItem.StorageMediaItem(
                storageUri = uri
            )
        ),
        handleLifecycle = true,
        autoPlay = false,
        usePlayerController = true,
        enablePip = false,
        handleAudioFocus = true,
        controllerConfig = VideoPlayerControllerConfig(
            showSpeedAndPitchOverlay = false,
            showSubtitleButton = false,
            showCurrentTimeAndTotalTime = false,
            showBufferingProgress = false,
            showForwardIncrementButton = false,
            showBackwardIncrementButton = false,
            showBackTrackButton = false,
            showNextTrackButton = false,
            showRepeatModeButton = false,
            controllerShowTimeMilliSeconds = 5_000,
            controllerAutoShow = false,
            showFullScreenButton = false
        ),
        volume = 0.5f,  // volume 0.0f to 1.0f
        repeatMode = RepeatMode.NONE,       // or RepeatMode.ALL, RepeatMode.ONE
        modifier = modifier.fillMaxSize(),
    )
}

@Composable
fun VideoViewAlt(
    modifier: Modifier,
    uri: Uri,
    imageLoader: ImageLoader,
    onClick: () -> Unit = {}
) {
    Box(
        modifier = modifier, contentAlignment = Alignment.BottomEnd
    ) {
        AsyncImage(
            model = uri,
            contentDescription = "",
            imageLoader = imageLoader,
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
        )
        IconButton(
            onClick = {},
            colors = IconButtonColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                disabledContentColor = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier.padding(8.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_play),
                contentDescription = stringResource(
                    id = R.string.choose_app
                )
            )

        }
    }


}

@Composable
fun Dialog(
    onDismissRequest: () -> Unit,
    onConfirmation: () -> Unit,
    dialogText: String,
) {
    AlertDialog(
        text = {
            Text(text = dialogText)
        },
        onDismissRequest = {
            onDismissRequest()
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirmation()
                }
            ) {
                Text(stringResource(id = R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onDismissRequest()
                }
            ) {
                Text(stringResource(id = R.string.cancel))
            }
        }
    )
}

@Composable
fun PlaceHolder(
    modifier: Modifier,
    stringResource: Int
) {
    Box(
        modifier = modifier
            .fillMaxSize(), contentAlignment = Alignment.Center
    ) {

        ElevatedCard(
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .fillMaxHeight(0.3f)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center // Center the Text inside the Box
            ) {
                Text(
                    text = stringResource(id = stringResource),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}