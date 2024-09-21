package com.example.ydaynomore.ui

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.example.ydaynomore.viewmodel.ActionViewModel
import com.example.ydaynomore.viewmodel.RecycleViewModel
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
                        "Centered Top App Bar",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onBackButtonClicked() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Localized description"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { onNextButtonClicked() }) {
                        Icon(
                            imageVector = Icons.Filled.Menu,
                            contentDescription = "Localized description"
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            ActionRow(
                modifier = Modifier.navigationBarsPadding(),
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

        HorizontalPager(
            modifier = Modifier.padding(innerPadding),
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 64.dp),
            verticalAlignment = Alignment.CenterVertically,
            pageSpacing = 16.dp
        ) { page ->

            Box(modifier = Modifier
                .fillMaxSize()
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
                }
                .padding(top = 16.dp),
                contentAlignment = Alignment.Center) {
                var elevation = 0.dp
                if (page == pagerState.currentPage) {
                    elevation = 20.dp
                }

                    AsyncImage(
                        model = images.value[page].contentUri, contentDescription = "",
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .shadow(elevation = elevation)
                    )
                    // TODO: placeholder for empty folder

            }

        }
    }
}

@Composable
fun ActionImage(
    modifier: Modifier,
    uri: Uri,
    onClick: () -> Unit = {}) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .clickable {
                    onClick()
                },
            alignment = Alignment.Center,
            model = uri,
            contentDescription = ""
        )
    }
}

@Composable
fun ActionRow(
    modifier: Modifier,
    onDelButtonClicked: () -> Unit,
    onRollBackButtonClicked: () -> Unit,
    showRestore: Boolean
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier.weight(2f),
            contentAlignment = Alignment.CenterStart
        ) {
            if (showRestore) {
            Button(onClick = { onRollBackButtonClicked() }) {
                Text("RB")
            }
            }
        }

        Box(modifier = Modifier.weight(3f)) {
            Button(onClick = { onDelButtonClicked() }) {
                Text("DEL")
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