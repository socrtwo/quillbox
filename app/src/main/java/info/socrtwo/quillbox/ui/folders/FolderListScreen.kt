package info.socrtwo.quillbox.ui.folders

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import info.socrtwo.quillbox.data.local.entity.FolderEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderListScreen(
    onOpenFolder: (Long, String) -> Unit,
    onCompose: () -> Unit,
    onManageRules: () -> Unit,
    viewModel: FolderViewModel = hiltViewModel()
) {
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val account by viewModel.account.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(account?.displayName ?: "Quillbox") },
                actions = {
                    IconButton(onClick = onManageRules) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Rules")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = onCompose) {
                Icon(Icons.Filled.Email, contentDescription = "Compose")
            }
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(folders, key = { it.id }) { folder ->
                    FolderRow(folder) { onOpenFolder(folder.id, folder.name) }
                }
            }
        }
    }
}

@Composable
private fun FolderRow(folder: FolderEntity, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(folder.name) },
        leadingContent = { Icon(Icons.Filled.Folder, contentDescription = null) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    )
}
