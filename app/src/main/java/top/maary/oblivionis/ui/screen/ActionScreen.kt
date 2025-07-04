package top.maary.oblivionis.ui.screen

import android.annotation.SuppressLint
import android.content.Intent
import android.provider.MediaStore
import android.util.Log
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgeDefaults
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarDefaults.topAppBarColors
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import coil3.ImageLoader
import coil3.video.VideoFrameDecoder
import kotlinx.coroutines.coroutineScope
import top.maary.oblivionis.R
import top.maary.oblivionis.data.MediaEntity
import top.maary.oblivionis.ui.ActionRow
import top.maary.oblivionis.ui.Dialog
import top.maary.oblivionis.ui.MediaPlayer
import top.maary.oblivionis.ui.PlaceHolder
import top.maary.oblivionis.viewmodel.ActionViewModel
import kotlin.math.absoluteValue

@SuppressLint("ConfigurationScreenWidthHeight")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ActionScreen(
    viewModel: ActionViewModel = viewModel(),
    onNextButtonClicked: () -> Unit,
    onBackButtonClicked: () -> Unit,
) {

    // 从ViewModel获取PagingData流并转换为LazyPagingItems
    val lazyPagingItems: LazyPagingItems<MediaEntity> =
        viewModel.imagePagingDataFlow.collectAsLazyPagingItems()

    LaunchedEffect(lazyPagingItems.itemCount) {
        Log.e("PAGING_DEBUG", "UI Received PagingData update. Item count: ${lazyPagingItems.itemCount}")
    }

    val uiState by viewModel.uiState.collectAsState()

    val visuallyRemovedItems = remember { mutableStateOf(setOf<Long>()) }

    LaunchedEffect(lazyPagingItems.loadState) {
        if (lazyPagingItems.loadState.refresh is LoadState.NotLoading) {
            visuallyRemovedItems.value = setOf()
        }
        if (lazyPagingItems.loadState.refresh is LoadState.Error) {
            val error = (lazyPagingItems.loadState.refresh as LoadState.Error).error
            Log.e("PAGING_DEBUG", "ActionScreen caught Paging LoadState.Error:", error)
        }
    }

    val secondaryContainerColor = MaterialTheme.colorScheme.secondaryContainer
    val badgeDefaultsColor = BadgeDefaults.containerColor

    val badgeColor by remember(uiState.markedImageCount, secondaryContainerColor, badgeDefaultsColor) {
        derivedStateOf {
            if (uiState.markedImageCount == 0) {
                secondaryContainerColor
            } else {
                badgeDefaultsColor
            }
        }
    }

    val showRestore by remember(uiState.lastMarkedImage) {
        derivedStateOf { uiState.lastMarkedImage != null }
    }

    val pagerState = rememberPagerState(pageCount = { lazyPagingItems.itemCount })

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    val imageLoader = ImageLoader.Builder(LocalContext.current).components {
            add(VideoFrameDecoder.Factory())
        }.build()

    val context = LocalContext.current

    val openDialog = remember {
        mutableStateOf(false)
    }

    val openExcludeDialog = remember {
        mutableStateOf(false)
    }

    Scaffold(modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,

        topBar = {
            CenterAlignedTopAppBar(
                colors = topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0f),
                    titleContentColor = MaterialTheme.colorScheme.primary),
                title = { },
                navigationIcon = {
                    FilledTonalButton(
                        onClick = { onBackButtonClicked() },
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Icon(
                            modifier = Modifier.padding(end = 8.dp),
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.back)
                        )
                        Text(
                            text = uiState.albumTitle,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    BadgedBox(modifier = Modifier.padding(end = 8.dp),
                        badge = { Badge(containerColor = badgeColor) { Text(text = uiState.markedImageCount.toString()) } }) {
                        FilledTonalButton(
                            onClick = { onNextButtonClicked() }, enabled = uiState.markedImageCount > 0
                        ) {
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
            if (openDialog.value) {
                Dialog(
                    onDismissRequest = { openDialog.value = false },
                    onConfirmation = {
                        viewModel.markAllImages()
                        openDialog.value = false },
                    dialogText = stringResource(id = R.string.deleteAllConfirmation)
                )
            }
            ActionRow(
                modifier = Modifier.navigationBarsPadding(),
                delButtonClickable = lazyPagingItems.itemCount > 0,
                onDelButtonClicked = {
                    if (pagerState.currentPage < lazyPagingItems.itemCount) {
                        lazyPagingItems[pagerState.currentPage]?.let { image ->
                            visuallyRemovedItems.value += image.id // 【修改】立即在视觉上隐藏
                            viewModel.markImage(image)
                        }
                    }
                },
                onDelButtonLongClicked = {
                    openDialog.value = true
                },
                onRollBackButtonClicked = {
                    viewModel.unMarkLastImage()
                },
                onShareButtonClicked = {
                    if (pagerState.currentPage < lazyPagingItems.itemCount) {
                        lazyPagingItems[pagerState.currentPage]?.let { image ->
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_STREAM, image.contentUri)
                                type = if (image.contentUri.toString()
                                        .startsWith(MediaStore.Images.Media.EXTERNAL_CONTENT_URI.toString())
                                ) "image/*" else "video/*"
                            }
                            val shareIntent = Intent.createChooser(sendIntent, null)
                            context.startActivity(shareIntent)
                        }
                    }
                },
                showRestore = showRestore,
                showShare = lazyPagingItems.itemCount > 0,
                currentPage = if (lazyPagingItems.itemCount > 0) pagerState.currentPage + 1 else 0,
                pagesCount = uiState.displayableImageCount
            )
        }

    ) { innerPadding ->

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
            PlaceHolder(
                modifier = Modifier, stringResource = R.string.congratulations
            )
            return@Scaffold
        }

        var dragOffset by remember { mutableFloatStateOf(0f) }
        var swipeScale by remember { mutableFloatStateOf(1f) }
        val density = LocalDensity.current.density // 获取屏幕密度
        val configuration = LocalConfiguration.current

        HorizontalPager(
            modifier = Modifier.padding(innerPadding),
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 64.dp),
            verticalAlignment = Alignment.CenterVertically,
            pageSpacing = 16.dp,
            key = lazyPagingItems.itemKey {
                Log.v("PAGING_DEBUG", "HorizontalPager generating UI for key: ${it.id}")
                it.id }, // 提供稳定的Key
        ) { page ->
            val currentImage = lazyPagingItems[page] ?: return@HorizontalPager

            LaunchedEffect(currentImage.id) {
                dragOffset = 0f
                swipeScale = 1f
            }

            Box(modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Calculate the absolute offset for the current page from the
                    // scroll position. We use the absolute value which allows us to mirror
                    // any effects for both directions
                    val pageOffset =
                        ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue

                    // We animate the alpha, between 50% and 100%
                    alpha = lerp(
                        start = 0.4f, stop = 1f, fraction = 1f - pageOffset.coerceIn(0f, 1f)
                    )
                    val scale = 1f - (pageOffset * .1f)
                    scaleX = scale
                    scaleY = scale

                    scaleX = if (pageOffset != 0f) {
                        scale
                    } else if (pagerState.currentPage == page) {
                        swipeScale
                    } else {
                        1f
                    }
                    scaleY = if (pageOffset != 0f) {
                        scale
                    } else if (pagerState.currentPage == page) {
                        swipeScale
                    } else {
                        1f
                    }

                    if (pagerState.currentPage == page) {
                        translationY = dragOffset
                    }
                }
                .draggable(orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta ->
                        // Update drag offset
                        dragOffset += delta
                        if (dragOffset > 100f) dragOffset = 100f

                        // Calculate the scale based on drag distance
                        swipeScale = (1f - ((-dragOffset) / 2000f).coerceIn(0f, 1f))

                    },
                    onDragStopped = { velocity ->
                        // If dragged far enough, dismiss the card
                        if (dragOffset < -300f || velocity < -1000f) { // Threshold for dismissal
                            // Call the function to remove the item
                            coroutineScope {
                                val screenHeight =
                                    configuration.screenHeightDp.dp.value * density.absoluteValue
                                val targetValue = -screenHeight // 将目标值设置为屏幕上边缘
                                animate(
                                    initialValue = dragOffset,
                                    targetValue = targetValue,
                                    animationSpec = tween(durationMillis = 400)
                                ) { value, _ ->
                                    dragOffset = value
                                    swipeScale = (1f - ((-dragOffset) / 2000f).coerceIn(0f, 1f))
                                }

                                if (currentImage.isExcluded) {
                                    openExcludeDialog.value = true
                                } else {
                                    visuallyRemovedItems.value += currentImage.id
                                    viewModel.markImage(currentImage)
                                }
                            }
                        } else {
                            if (dragOffset == 100f) {
                                if (currentImage.isExcluded) {
                                    viewModel.includeMedia(currentImage)
                                } else {
                                    viewModel.excludeMedia(currentImage)
                                }
                            }
                            // Reset position and scale
                            dragOffset = 0f
                            swipeScale = 1f
                        }
                    })
                .padding(top = 16.dp), contentAlignment = Alignment.Center) {

                if (openExcludeDialog.value) {

                    Dialog(onDismissRequest = {
                        openExcludeDialog.value = false
                        dragOffset = 0f
                        swipeScale = 1f
                    }, onConfirmation = {
                        dragOffset = 0f
                        swipeScale = 1f
                        viewModel.markImage(currentImage)
                        openExcludeDialog.value = false
                    }, dialogText = stringResource(R.string.delete_excluded)
                    )
                }

                val uri = currentImage.contentUri
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = uri
                }

                Box(modifier = Modifier.wrapContentSize(), contentAlignment = Alignment.TopStart) {
                    MediaPlayer(modifier = Modifier.fillMaxWidth(),
                        uri = uri,
                        imageLoader = imageLoader,
                        onMediaClick = {
                            Log.v("OBLIVIONIS", "IMAGE CLICK")
                            context.startActivity(
                                Intent.createChooser(
                                    intent, context.getString(R.string.choose_app)
                                )
                            )
                        }
                    )
                    if (currentImage.isExcluded) {
                        IconButton(
                            onClick = {}, colors = IconButtonColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                                disabledContentColor = MaterialTheme.colorScheme.onSurface
                            ), modifier = Modifier.padding(8.dp)
                        ) {
                            Icon(
                                tint = MaterialTheme.colorScheme.tertiary,
                                painter = painterResource(R.drawable.ic_star),
                                contentDescription = stringResource(R.string.is_excluded)
                            )
                        }
                    }
                }
            }
        }
    }
}