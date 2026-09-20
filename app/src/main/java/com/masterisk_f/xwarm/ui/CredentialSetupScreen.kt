package com.masterisk_f.xwarm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun CredentialSetupScreen(
    hasBuiltInCredentials: Boolean,
    isProcessing: Boolean,
    errorMessage: String?,
    onStartOAuth: (clientId: String, redirectUri: String) -> Unit,
    onSubmitCode: (codeOrUrl: String, clientId: String, clientSecret: String, redirectUri: String) -> Unit,
    onDirectSaveToken: (token: String) -> Unit,
    defaultClientId: String = "",
    defaultClientSecret: String = "",
    defaultRedirectUri: String = "xwarm://oauth/callback",
) {
    var pastedCodeOrUrl by remember { mutableStateOf("") }
    var customClientId by remember { mutableStateOf(defaultClientId) }
    var customClientSecret by remember { mutableStateOf(defaultClientSecret) }
    var customRedirectUri by remember { mutableStateOf(defaultRedirectUri) }
    var directToken by remember { mutableStateOf("") }
    var showAdvanced by remember { mutableStateOf(!hasBuiltInCredentials) }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Xwarm",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Swarm チェックイン & X 連携",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (errorMessage != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (isProcessing) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "認証中...", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(24.dp))
        }

        // --- Main Action: OAuth login ---
        val effectiveClientId = customClientId.ifBlank { defaultClientId }
        val effectiveClientSecret = customClientSecret.ifBlank { defaultClientSecret }
        val effectiveRedirectUri = customRedirectUri.ifBlank { defaultRedirectUri }

        Button(
            onClick = {
                onStartOAuth(effectiveClientId, effectiveRedirectUri)
            },
            enabled = !isProcessing && effectiveClientId.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text(text = "Swarm でログイン", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- Route B fallback: Paste code or redirected URL ---
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "ブラウザから戻らない場合 (手動貼付)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "認証後に開いた URL 全体、または code= の値を貼り付けてください。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = pastedCodeOrUrl,
                    onValueChange = { pastedCodeOrUrl = it },
                    label = { Text("リダイレクト先 URL または code") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        onSubmitCode(
                            pastedCodeOrUrl,
                            effectiveClientId,
                            effectiveClientSecret,
                            effectiveRedirectUri
                        )
                    },
                    enabled = !isProcessing && pastedCodeOrUrl.isNotBlank() && effectiveClientSecret.isNotBlank(),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("コードを送信して完了")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- Advanced settings / BYO-credentials (D-12c) ---
        TextButton(onClick = { showAdvanced = !showAdvanced }) {
            Text(if (showAdvanced) "▲ 詳細設定を閉じる" else "▼ 詳細設定（カスタム認証情報 / トークン直接入力）")
        }

        if (showAdvanced) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "自前の Foursquare 認証情報",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customClientId,
                        onValueChange = { customClientId = it },
                        label = { Text("Client ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customClientSecret,
                        onValueChange = { customClientSecret = it },
                        label = { Text("Client Secret") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customRedirectUri,
                        onValueChange = { customRedirectUri = it },
                        label = { Text("Redirect URI") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "または OAuth トークンを直接入力",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = directToken,
                        onValueChange = { directToken = it },
                        label = { Text("OAuth Token") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { onDirectSaveToken(directToken) },
                        enabled = !isProcessing && directToken.isNotBlank(),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("トークンを保存")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
