package com.example.ydaynomore.ui

import android.content.res.Resources
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgeDefaults
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.example.ydaynomore.R
import com.example.ydaynomore.viewmodel.ActionViewModel
import kotlin.math.absoluteValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionScreen(
    viewModel: ActionViewModel = viewModel(),
    onNextButtonClicked: () -> Unit,
    onBackButtonClicked: () -> Unit,
) {

    val images = viewModel.unmarkedImages.collectAsState(initial = emptyList())

    val lastMarked = viewModel.lastMarked.collectAsState()

    val marked = viewModel.markedImages.collectAsState(initial = emptyList())

    val pagerState = rememberPagerState(pageCount = { images.value.size })

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),

        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
                title = {
                    Text(
                        viewModel.albumPath.toString().substringAfterLast("/"),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onBackButtonClicked() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.back)
                        )
                    }
                },
                actions = {
                        val badgeColor = if (marked.value.isEmpty()) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            BadgeDefaults.containerColor
                        }
                        BadgedBox(
                            modifier = Modifier.padding(end = 8.dp),
                            badge = { Badge (containerColor = badgeColor) { Text(text = marked.value.size.toString())}}) {
                            IconButton(onClick = { onNextButtonClicked() }, enabled = marked.value.isNotEmpty()) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_recycle),
                                    contentDescription = stringResource(id = R.string.go_to_recycle_screen)
                                )
                            }
                        }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            ActionRow(
                modifier = Modifier.navigationBarsPadding(),
                delButtonClickable = images.value.isNotEmpty(),
                onDelButtonClicked = {
                    if (images.value.isNotEmpty()) {
                        viewModel.markImage(pagerState.currentPage)
                    }
                },
                onRollBackButtonClicked = {
                    viewModel.unMarkLastImage()
                },
                showRestore = (lastMarked.value != null))
        }

    ) { innerPadding ->

        if (images.value.isEmpty()) {

            PlaceHolder(modifier = Modifier.padding(innerPadding), stringResource = R.string.congratulations)
            return@Scaffold
        }

            HorizontalPager(
                modifier = Modifier.padding(innerPadding),
                state = pagerState,
                contentPadding = PaddingValues(horizontal = 64.dp),
                verticalAlignment = Alignment.CenterVertically,
                pageSpacing = 16.dp
            ) { page ->

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 16.dp),
                    contentAlignment = Alignment.Center
                ) {

                    val uri = images.value[page].contentUri

                    MediaPlayer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                // Calculate the absolute offset for the current page from the
                                // scroll position. We use the absolute value which allows us to mirror
                                // any effects for both directions
                                val pageOffset = (
                                        (pagerState.currentPage - page)
                                                + pagerState.currentPageOffsetFraction
                                        ).absoluteValue

                                // We animate the alpha, between 50% and 100%
                                alpha = lerp(
                                    start = 0.5f,
                                    stop = 1f,
                                    fraction = 1f - pageOffset.coerceIn(0f, 1f)
                                )

                                shadowElevation = lerp(
                                    start = 0f,
                                    stop = 10f,
                                    fraction = 1f - pageOffset.coerceIn(0f, 1f)
                                )

                                // 设置圆角效果 (应用于阴影的边缘)
                                shape = RoundedCornerShape(8.dp)  // 圆角半径
                                clip = true  // 确保视图被剪切到圆角形状
                            }, uri = uri
                    )

                    // TODO: placeholder for empty folder

                }

            }
    }
}

@Composable
fun MediaPlayer(modifier: Modifier, uri: Uri,
                onClick: () -> Unit = {}) {
    when {
        uri.toString().startsWith(MediaStore.Images.Media.EXTERNAL_CONTENT_URI.toString()) -> {
            // 处理图片变化
            AsyncImage(
                model = uri,
                contentDescription = "",
                modifier = modifier
                    .clickable { onClick() }
                    .clip(RoundedCornerShape(8.dp))
            )
        }
        uri.toString().startsWith(MediaStore.Video.Media.EXTERNAL_CONTENT_URI.toString()) -> {
            // 处理视频变化
            ExoPlayerView(modifier, uri, onClick)
        }
    }
}

@Composable
fun ActionRow(
    modifier: Modifier,
    delButtonClickable: Boolean = true,
    onDelButtonClicked: () -> Unit,
    onRollBackButtonClicked: () -> Unit,
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
                    modifier = Modifier.size(48.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    elevation = ButtonDefaults.buttonElevation(10.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_restore),
                        contentDescription = stringResource(
                            id = R.string.restore_last_deleted
                        ))
                }
            }
        }

        Box(modifier = modifier
            .fillMaxWidth()
            .padding(16.dp), contentAlignment = Alignment.Center) {
            Button(
                onClick = onDelButtonClicked,
                enabled = delButtonClickable,
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.size(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BadgeDefaults.containerColor),
                elevation = ButtonDefaults.buttonElevation(10.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_close),
                    contentDescription = stringResource(id = R.string.mark_this_to_delete),
                    )
            }
        }
}

@Composable
fun ExoPlayerView(
    modifier: Modifier = Modifier,
    uri: Uri, onClick: () -> Unit) {
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
    stringResource: Int) {
    Box(modifier = modifier
        .fillMaxSize(), contentAlignment = Alignment.Center) {

        ElevatedCard(elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .fillMaxHeight(0.3f)) {
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

@Preview(
    showBackground = true,
    showSystemUi = true
)
@Composable
fun PreviewActionRow() {
//    ActionRow()
//    ActionScreen(onNextButtonClicked = {}, onBackButtonClicked = {})
}