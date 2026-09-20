package com.masterisk_f.xwarm.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.masterisk_f.xwarm.BuildConfig
import com.masterisk_f.xwarm.auth.EncryptedTokenStore
import com.masterisk_f.xwarm.auth.FoursquareOAuth
import com.masterisk_f.xwarm.auth.TokenStore
import com.masterisk_f.xwarm.data.FoursquareApi
import com.masterisk_f.xwarm.data.SpotRepository
import com.masterisk_f.xwarm.location.AndroidLocationProvider
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.net.URLEncoder

private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION
)

@Composable
fun XwarmApp(
    initialIntent: Intent? = null,
    tokenStore: TokenStore = rememberTokenStore(),
    api: FoursquareApi = remember { FoursquareApi() },
    repository: SpotRepository = remember(api, tokenStore) { SpotRepository(api, tokenStore) },
    locationProvider: AndroidLocationProvider = rememberLocationProvider(),
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val viewModel: SpotListViewModel = viewModel(
        factory = SpotListViewModel.provideFactory(repository, locationProvider, tokenStore)
    )

    val uiState by viewModel.uiState.collectAsState()
    val checkingInVenueId by viewModel.checkingInVenueId.collectAsState()

    var isAuthProcessing by remember { mutableStateOf(false) }
    var authErrorMessage by remember { mutableStateOf<String?>(null) }

    // --- Permission launcher ---
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        val activity = context as? android.app.Activity
        val shouldShowRationale = activity?.let {
            it.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) ||
                it.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)
        } ?: false
        viewModel.onPermissionResult(granted, shouldShowRationale)
    }

    // --- Unified OAuth code-to-token processor ---
    fun processOAuthCode(code: String, clientId: String, clientSecret: String, redirectUri: String) {
        if (clientId.isBlank() || clientSecret.isBlank()) {
            authErrorMessage = "Client ID / Secret が設定されていません。"
            return
        }

        isAuthProcessing = true
        authErrorMessage = null
        coroutineScope.launch {
            val res = FoursquareOAuth.exchangeCodeForToken(
                clientId = clientId,
                clientSecret = clientSecret,
                redirectUri = redirectUri,
                code = code
            )
            res.onSuccess { token ->
                repository.verifyAndSaveToken(token)
                    .onSuccess {
                        isAuthProcessing = false
                        viewModel.loadSpots()
                    }
                    .onFailure { err ->
                        isAuthProcessing = false
                        authErrorMessage = "トークン検証に失敗しました: ${err.message}"
                    }
            }.onFailure { err ->
                isAuthProcessing = false
                authErrorMessage = "認証に失敗しました: ${err.message}"
            }
        }
    }

    // --- Handle incoming OAuth deep link (Route A) ---
    fun handleIncomingIntent(intent: Intent?) {
        val data = intent?.data ?: return
        val code = FoursquareOAuth.extractCodeFromUrl(data.toString()) ?: return
        val clientId = tokenStore.getClientId()?.ifBlank { null } ?: BuildConfig.FSQ_CLIENT_ID
        val clientSecret = tokenStore.getClientSecret()?.ifBlank { null } ?: BuildConfig.FSQ_CLIENT_SECRET
        val redirectUri = tokenStore.getRedirectUri()?.ifBlank { null } ?: BuildConfig.FSQ_REDIRECT_URI.ifBlank { "xwarm://oauth/callback" }

        processOAuthCode(code, clientId, clientSecret, redirectUri)
    }

    LaunchedEffect(initialIntent) {
        handleIncomingIntent(initialIntent)
    }

    // --- Handle Events (Open X app / Toasts) ---
    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is SpotListEvent.OpenX -> {
                    val encoded = URLEncoder.encode(event.tweetText, "UTF-8")
                    val intentUrl = "https://x.com/intent/tweet?text=$encoded"
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(intentUrl))
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "X を開けませんでした: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }

                is SpotListEvent.ShowToast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // --- Initial load ---
    LaunchedEffect(Unit) {
        if (!locationProvider.hasPermission()) {
            permissionLauncher.launch(LOCATION_PERMISSIONS)
        } else {
            viewModel.loadSpots()
        }
    }

    // --- ON_RESUME Auto-refresh (R7, D-16) ---
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (tokenStore.getOAuthToken() != null && locationProvider.hasPermission()) {
                    viewModel.loadSpots(isSwipeRefresh = false)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // --- Screen Routing ---
    if (uiState is SpotListUiState.NeedsAuth) {
        val defaultClientId = BuildConfig.FSQ_CLIENT_ID
        val defaultClientSecret = BuildConfig.FSQ_CLIENT_SECRET
        val defaultRedirectUri = BuildConfig.FSQ_REDIRECT_URI.ifBlank { "xwarm://oauth/callback" }

        val resolvedClientId = tokenStore.getClientId()?.ifBlank { null } ?: defaultClientId
        val resolvedClientSecret = tokenStore.getClientSecret()?.ifBlank { null } ?: defaultClientSecret
        val resolvedRedirectUri = tokenStore.getRedirectUri()?.ifBlank { null } ?: defaultRedirectUri

        CredentialSetupScreen(
            hasBuiltInCredentials = defaultClientId.isNotBlank() && defaultClientSecret.isNotBlank(),
            isProcessing = isAuthProcessing,
            errorMessage = authErrorMessage,
            initialClientId = resolvedClientId,
            initialClientSecret = resolvedClientSecret,
            initialRedirectUri = resolvedRedirectUri,
            onStartOAuth = { clientId, redirectUri ->
                authErrorMessage = null
                tokenStore.saveClientId(clientId)
                tokenStore.saveRedirectUri(redirectUri)
                val url = FoursquareOAuth.buildAuthorizeUrl(clientId, redirectUri)
                FoursquareOAuth.launchCustomTab(context, url)
            },
            onSubmitCode = { codeOrUrl, clientId, clientSecret, redirectUri ->
                val code = FoursquareOAuth.extractCodeFromUrl(codeOrUrl)
                if (code.isNullOrBlank()) {
                    authErrorMessage = "入力された文字列から認可コードを見つけられませんでした。"
                    return@CredentialSetupScreen
                }
                processOAuthCode(code, clientId, clientSecret, redirectUri)
            },
            onDirectSaveToken = { token ->
                isAuthProcessing = true
                authErrorMessage = null
                coroutineScope.launch {
                    repository.verifyAndSaveToken(token)
                        .onSuccess {
                            isAuthProcessing = false
                            viewModel.loadSpots()
                        }
                        .onFailure { err ->
                            isAuthProcessing = false
                            authErrorMessage = "無効なトークンです: ${err.message}"
                        }
                }
            }
        )
    } else {
        SpotListScreen(
            uiState = uiState,
            checkingInVenueId = checkingInVenueId,
            onRefresh = { viewModel.loadSpots(isSwipeRefresh = true) },
            onCheckIn = { spot -> viewModel.checkIn(spot) },
            onRequestPermission = {
                permissionLauncher.launch(LOCATION_PERMISSIONS)
            },
            onOpenSettings = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            },
            onLogout = { viewModel.logout() }
        )
    }
}

@Composable
private fun rememberTokenStore(): TokenStore {
    val context = LocalContext.current
    return remember(context) { EncryptedTokenStore(context) }
}

@Composable
private fun rememberLocationProvider(): AndroidLocationProvider {
    val context = LocalContext.current
    return remember(context) { AndroidLocationProvider(context) }
}
