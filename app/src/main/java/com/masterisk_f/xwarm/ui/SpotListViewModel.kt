package com.masterisk_f.xwarm.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.masterisk_f.xwarm.auth.TokenStore
import com.masterisk_f.xwarm.data.CheckInResult
import com.masterisk_f.xwarm.data.FoursquareApiException
import com.masterisk_f.xwarm.data.Spot
import com.masterisk_f.xwarm.data.SpotRepository
import com.masterisk_f.xwarm.location.Coordinates
import com.masterisk_f.xwarm.location.LocationProvider
import com.masterisk_f.xwarm.tweet.TweetTextFormatter
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SpotListUiState {
    data object Loading : SpotListUiState
    data object NeedsAuth : SpotListUiState
    data class NeedsPermission(val permanentlyDenied: Boolean) : SpotListUiState
    data class Error(val message: String) : SpotListUiState
    data class Success(val spots: List<Spot>, val isRefreshing: Boolean = false) : SpotListUiState
}

sealed interface SpotListEvent {
    data class OpenX(val tweetText: String) : SpotListEvent
    data class ShowToast(val message: String) : SpotListEvent
}

class SpotListViewModel(
    private val repository: SpotRepository,
    private val locationProvider: LocationProvider,
    private val tokenStore: TokenStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow<SpotListUiState>(SpotListUiState.Loading)
    val uiState: StateFlow<SpotListUiState> = _uiState.asStateFlow()

    // In-flight check-in tracking (D-16). Null when idle.
    private val _checkingInVenueId = MutableStateFlow<String?>(null)
    val checkingInVenueId: StateFlow<String?> = _checkingInVenueId.asStateFlow()

    private val _events = MutableSharedFlow<SpotListEvent>()
    val events: SharedFlow<SpotListEvent> = _events.asSharedFlow()

    private var lastKnownCoords: Coordinates? = null
    private var isFetchingSpots = false

    fun loadSpots(isSwipeRefresh: Boolean = false) {
        if (isFetchingSpots) return

        if (tokenStore.getOAuthToken().isNullOrBlank()) {
            _uiState.value = SpotListUiState.NeedsAuth
            return
        }

        if (!locationProvider.hasPermission()) {
            _uiState.value = SpotListUiState.NeedsPermission(permanentlyDenied = false)
            return
        }

        val current = _uiState.value
        if (isSwipeRefresh && current is SpotListUiState.Success) {
            _uiState.value = current.copy(isRefreshing = true)
        } else if (current !is SpotListUiState.Success) {
            _uiState.value = SpotListUiState.Loading
        }

        isFetchingSpots = true
        viewModelScope.launch {
            try {
                val coords = locationProvider.getCurrentLocation() ?: lastKnownCoords
                if (coords == null) {
                    _uiState.value = SpotListUiState.Error("位置情報を取得できませんでした。GPSが有効か確認してください。")
                    return@launch
                }
                lastKnownCoords = coords

                val result = repository.getNearbySpots(coords.latitude, coords.longitude)
                result.onSuccess { spots ->
                    _uiState.value = SpotListUiState.Success(spots = spots, isRefreshing = false)
                }.onFailure { err ->
                    handleApiError(err)
                }
            } catch (e: Exception) {
                _uiState.value = SpotListUiState.Error(e.message ?: "エラーが発生しました")
            } finally {
                isFetchingSpots = false
                val cur = _uiState.value
                if (cur is SpotListUiState.Success && cur.isRefreshing) {
                    _uiState.value = cur.copy(isRefreshing = false)
                }
            }
        }
    }

    /**
     * Checks into a venue.
     *
     * Repeatable (R7, D-16): unlocks immediately on success or failure so subsequent
     * check-ins are always possible.
     */
    fun checkIn(spot: Spot) {
        if (_checkingInVenueId.value != null) return // In-flight debounce

        _checkingInVenueId.value = spot.id
        viewModelScope.launch {
            try {
                val coords = lastKnownCoords
                val result = repository.checkIn(
                    spot = spot,
                    lat = coords?.latitude,
                    lng = coords?.longitude
                )

                result.onSuccess { checkInRes ->
                    val tweetText = TweetTextFormatter.format(
                        venueName = checkInRes.venueName.ifEmpty { spot.name },
                        spot = spot,
                        shareUrl = checkInRes.checkinShortUrl
                    )
                    _events.emit(SpotListEvent.OpenX(tweetText))
                }.onFailure { err ->
                    val msg = resolveErrorMessage(err)
                    _events.emit(SpotListEvent.ShowToast("チェックイン失敗: $msg"))
                }
            } catch (e: Exception) {
                _events.emit(SpotListEvent.ShowToast("チェックイン失敗: ${e.message}"))
            } finally {
                // ALWAYS unlock so the user can check in again immediately (R7, D-16)
                _checkingInVenueId.value = null
            }
        }
    }

    fun onPermissionResult(isGranted: Boolean, shouldShowRationale: Boolean) {
        if (isGranted) {
            loadSpots()
        } else {
            val permanentlyDenied = !shouldShowRationale
            _uiState.value = SpotListUiState.NeedsPermission(permanentlyDenied = permanentlyDenied)
        }
    }

    fun logout() {
        tokenStore.clearOAuthToken()
        _uiState.value = SpotListUiState.NeedsAuth
    }

    private suspend fun handleApiError(err: Throwable) {
        if (err is FoursquareApiException.Unauthorized) {
            tokenStore.clearOAuthToken()
            _uiState.value = SpotListUiState.NeedsAuth
        } else {
            _uiState.value = SpotListUiState.Error(resolveErrorMessage(err))
        }
    }

    private fun resolveErrorMessage(err: Throwable): String = when (err) {
        is FoursquareApiException.Unauthorized -> "認証が無効です。再ログインしてください。"
        is FoursquareApiException.RateLimited -> "APIレート制限に達しました。しばらく待ってから再試行してください。"
        is FoursquareApiException.QuotaExhausted -> "API無料枠が上限に達しました (HTTP 429)。"
        else -> err.message ?: "通信エラーが発生しました"
    }

    companion object {
        fun provideFactory(
            repository: SpotRepository,
            locationProvider: LocationProvider,
            tokenStore: TokenStore,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SpotListViewModel(repository, locationProvider, tokenStore) as T
            }
        }
    }
}
