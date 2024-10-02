package top.maary.oblivionis.ui

import android.net.Uri
import android.provider.MediaStore
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
import top.maary.oblivionis.R

@Composable
fun MediaPlayer(
    modifier: Modifier,
    uri: Uri,
    imageLoader: ImageLoader,
    isMultiSelectionState: Boolean = false,
    isSelected: Boolean = false, // 用于表示当前项是否被选中
    onImageClick: () -> Unit = {},
    onVideoClick: () -> Unit = {},
    onLongPress: () -> Unit = {} // 长按事件
) {
    Box(
        modifier = Modifier
            .wrapContentSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = {
                        onLongPress() // 处理长按事件
                    },
                    onTap = {
                        if (uri
                                .toString()
                                .startsWith(MediaStore.Images.Media.EXTERNAL_CONTENT_URI.toString())
                        ) {
                            onImageClick()
                        } else if (uri
                                .toString()
                                .startsWith(MediaStore.Video.Media.EXTERNAL_CONTENT_URI.toString())
                        ) {
                            onVideoClick()
                        }
                    }
                )
            }
    ) {
        when {
            // 处理图片显示
            uri.toString().startsWith(MediaStore.Images.Media.EXTERNAL_CONTENT_URI.toString()) -> {
                AsyncImage(
                    model = uri,
                    contentDescription = "",
                    modifier = modifier
                        .clip(RoundedCornerShape(8.dp))
                )
            }

            // 处理视频显示
            uri.toString().startsWith(MediaStore.Video.Media.EXTERNAL_CONTENT_URI.toString()) -> {
                VideoViewAlt(
                    modifier = modifier,
                    uri = uri,
                    imageLoader = imageLoader
                )
            }
        }

        if (isMultiSelectionState) {
            // 显示选择框的图标
            Icon(
                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = if (isSelected) "Selected" else "Not Selected",
                modifier = Modifier
                    .align(Alignment.TopEnd) // 选择框显示在右上角
                    .size(48.dp)
                    .padding(8.dp),
                tint = if (isSelected) Color.Blue else Color.Gray // 设置选中的颜色
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
    showRestore: Boolean
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (showRestore) {
            Button(
                onClick = onRollBackButtonClicked,
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier
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


    val interactionSource = remember { MutableInteractionSource() }

    val viewConfiguration = LocalViewConfiguration.current


    LaunchedEffect(interactionSource) {
        var isLongClick = false

        interactionSource.interactions.collectLatest { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    isLongClick = false
                    delay(viewConfiguration.longPressTimeoutMillis)
                    isLongClick = true
                    onDelButtonLongClicked()
                }

                is PressInteraction.Release -> {
                    if (isLongClick.not()) {
                        onDelButtonClicked()
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp), contentAlignment = Alignment.Center
    ) {
        Button(
            onClick = { },
            enabled = delButtonClickable,
            shape = CircleShape,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(48.dp),
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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp), contentAlignment = Alignment.CenterEnd
    ) {
        Button(
            onClick = onShareButtonClicked,
            enabled = true,
            shape = CircleShape,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(48.dp),
            colors = ButtonDefaults.outlinedButtonColors()
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_share),
                contentDescription = stringResource(R.string.share_media)
            )
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
        modifier = modifier
            .clickable { onClick() }, contentAlignment = Alignment.BottomEnd
    ) {
        AsyncImage(
            model = uri,
            contentDescription = "",
            imageLoader = imageLoader,
            modifier = modifier
                .clickable { onClick() }
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