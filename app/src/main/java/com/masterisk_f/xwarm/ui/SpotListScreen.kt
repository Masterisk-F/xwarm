package com.masterisk_f.xwarm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.masterisk_f.xwarm.data.Spot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotListScreen(
    uiState: SpotListUiState,
    checkingInVenueId: String?,
    onRefresh: () -> Unit,
    onCheckIn: (Spot) -> Unit,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onLogout: () -> Unit,
) {
    val pullToRefreshState = rememberPullToRefreshState()
    val isRefreshing = (uiState as? SpotListUiState.Success)?.isRefreshing ?: false

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Xwarm",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    TextButton(onClick = onRefresh) {
                        Text("再取得", color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    TextButton(onClick = onLogout) {
                        Text("ログアウト", color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (uiState) {
                is SpotListUiState.Loading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("現在地からスポットを取得中...", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                is SpotListUiState.NeedsPermission -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "位置情報の許可が必要です",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (uiState.permanentlyDenied) {
                                "設定アプリから位置情報の利用を許可してください。"
                            } else {
                                "近くのスポット一覧を表示するために位置情報を使用します。"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        if (uiState.permanentlyDenied) {
                            Button(onClick = onOpenSettings) {
                                Text("設定を開く")
                            }
                        } else {
                            Button(onClick = onRequestPermission) {
                                Text("許可する")
                            }
                        }
                    }
                }

                is SpotListUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "読み込みエラー",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = uiState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onRefresh) {
                            Text("再試行")
                        }
                    }
                }

                is SpotListUiState.Success -> {
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = onRefresh,
                        state = pullToRefreshState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (uiState.spots.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "近くにスポットが見つかりませんでした",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "下にスワイプして再読み込みしてください",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 16.dp)
                            ) {
                                items(
                                    items = uiState.spots,
                                    key = { it.id }
                                ) { spot ->
                                    SpotRow(
                                        spot = spot,
                                        isCheckingIn = checkingInVenueId == spot.id,
                                        onLongClick = { onCheckIn(spot) }
                                    )
                                }
                            }
                        }
                    }
                }

                is SpotListUiState.NeedsAuth -> {
                    // Handled at parent level (XwarmApp)
                }
            }
        }
    }
}
