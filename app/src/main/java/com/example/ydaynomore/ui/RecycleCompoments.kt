package com.example.ydaynomore.ui

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ydaynomore.YNMApplication
import com.example.ydaynomore.data.MediaStoreImage
import com.example.ydaynomore.viewmodel.ActionViewModel
import com.example.ydaynomore.viewmodel.RecycleViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecycleScreen(
    recycleViewModel: RecycleViewModel = viewModel(
        factory = RecycleViewModel.Factory
    ),
    actionViewModel: ActionViewModel = viewModel(),
    onBackButtonClicked: () -> Unit) {

    val images = actionViewModel.markedImages.collectAsState(initial = emptyList())

    LaunchedEffect(images) {
        Log.v("YDNM", "RECYCLE EFFECT ${images.value.size}")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
                navigationIcon = {
                    IconButton(onClick = { onBackButtonClicked() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Localized description"
                        )
                    }
                },
                title = {
                    Text("Recycle")
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                actionViewModel.deleteImages(images.value)
                recycleViewModel.removeAll()
            }) {
                
            }
        }
    ) { innerPadding ->
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(2),
            verticalItemSpacing = 4.dp,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            content = {
                Log.v("YDNM", "RECYCLE ${images.value.size}")
                items(images.value.size) { index ->
                    ActionImage(modifier = Modifier
                        .fillMaxWidth(fraction = 0.5f),
                        uri = images.value[index].contentUri,
                        onClick = {
                            actionViewModel.unMarkImage(images.value[index])
                        })
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun RecycleScreenPreview() {
    RecycleScreen(onBackButtonClicked = {})
}