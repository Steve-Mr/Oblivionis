package com.example.ydaynomore.ui

import android.app.Activity
import android.content.IntentSender
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ydaynomore.R
import com.example.ydaynomore.YNMApplication
import com.example.ydaynomore.data.MediaStoreImage
import com.example.ydaynomore.viewmodel.ActionViewModel
import com.example.ydaynomore.viewmodel.RecycleViewModel
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecycleScreen(
    actionViewModel: ActionViewModel = viewModel(),
    onBackButtonClicked: () -> Unit) {

    val images = actionViewModel.markedImages.collectAsState(initial = emptyList())

    val intentSender = actionViewModel.pendingDeleteIntentSender.collectAsState(initial = null)

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
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    scrolledContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
                navigationIcon = {
                    FilledTonalButton(onClick = { onBackButtonClicked() },
                        modifier = Modifier.padding(start = 16.dp)) {
                        Icon(
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
                        modifier = Modifier.padding(end = 16.dp)) {
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

                        /* TODO: 顺序存在问题 */

                        MediaPlayer(
                            modifier = Modifier
                                .fillMaxWidth(fraction = 0.33f)
                                .padding(2.dp),
                            uri = images.value[index].contentUri,
                            onClick = {
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

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun RecycleScreenPreview() {
    RecycleScreen(onBackButtonClicked = {})
}