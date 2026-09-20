package com.masterisk_f.xwarm

import com.masterisk_f.xwarm.auth.TokenStore
import com.masterisk_f.xwarm.data.CheckInResult
import com.masterisk_f.xwarm.data.FoursquareApiException
import com.masterisk_f.xwarm.data.Spot
import com.masterisk_f.xwarm.data.SpotRepository
import com.masterisk_f.xwarm.location.Coordinates
import com.masterisk_f.xwarm.location.LocationProvider
import com.masterisk_f.xwarm.ui.SpotListEvent
import com.masterisk_f.xwarm.ui.SpotListUiState
import com.masterisk_f.xwarm.ui.SpotListViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SpotListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var fakeTokenStore: FakeTokenStore
    private lateinit var fakeLocationProvider: FakeLocationProvider
    private lateinit var fakeRepository: FakeSpotRepository
    private lateinit var viewModel: SpotListViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeTokenStore = FakeTokenStore(token = "VALID_TOKEN")
        fakeLocationProvider = FakeLocationProvider(
            hasPerm = true,
            coords = Coordinates(35.6812, 139.7671)
        )
        fakeRepository = FakeSpotRepository()
        viewModel = SpotListViewModel(
            repository = fakeRepository,
            locationProvider = fakeLocationProvider,
            tokenStore = fakeTokenStore
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadSpots_emitsNeedsAuth_whenNoToken() = testScope.runTest {
        fakeTokenStore.token = null
        viewModel.loadSpots()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is SpotListUiState.NeedsAuth)
    }

    @Test
    fun loadSpots_emitsSuccess_withSpotsList() = testScope.runTest {
        val sampleSpots = listOf(
            Spot(id = "1", name = "Spot A", distanceMeters = 50),
            Spot(id = "2", name = "Spot B", distanceMeters = 120)
        )
        fakeRepository.nearbyResult = Result.success(sampleSpots)

        viewModel.loadSpots()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is SpotListUiState.Success)
        assertEquals(2, (state as SpotListUiState.Success).spots.size)
    }

    @Test
    fun checkIn_repeatableOnSuccess_unlocksImmediately_R7() = testScope.runTest {
        val spot1 = Spot(id = "s1", name = "Spot One", city = "Tokyo", state = "Tokyo")
        val spot2 = Spot(id = "s2", name = "Spot Two", city = "Osaka", state = "Osaka")

        fakeRepository.checkInResult = Result.success(
            CheckInResult(checkinId = "c1", checkinShortUrl = "https://swarmapp.com/c/c1", venueName = "Spot One")
        )

        val events = mutableListOf<SpotListEvent>()
        val job = launch { viewModel.events.collect { events.add(it) } }

        // 1st Check-in
        viewModel.checkIn(spot1)
        advanceUntilIdle()

        assertNull("In-flight lock must be released on success (R7)", viewModel.checkingInVenueId.value)
        assertEquals(1, events.size)
        assertTrue(events.last() is SpotListEvent.OpenX)
        val tweet1 = (events.last() as SpotListEvent.OpenX).tweetText
        assertEquals("I'm at Spot One in Tokyo, Tokyo\nhttps://swarmapp.com/c/c1", tweet1)

        // 2nd Check-in (R7: immediately repeatable for another spot without reloading)
        fakeRepository.checkInResult = Result.success(
            CheckInResult(checkinId = "c2", checkinShortUrl = "https://swarmapp.com/c/c2", venueName = "Spot Two")
        )
        viewModel.checkIn(spot2)
        advanceUntilIdle()

        assertNull("In-flight lock must be released on 2nd check-in (R7)", viewModel.checkingInVenueId.value)
        assertEquals(2, events.size)
        val tweet2 = (events.last() as SpotListEvent.OpenX).tweetText
        assertEquals("I'm at Spot Two in Osaka, Osaka\nhttps://swarmapp.com/c/c2", tweet2)

        job.cancel()
    }

    @Test
    fun checkIn_unlocksOnFailure_allowingSubsequentCheckIn_R7() = testScope.runTest {
        val spot = Spot(id = "s1", name = "Spot One")
        fakeRepository.checkInResult = Result.failure(FoursquareApiException.ServerError(500, "Server Down"))

        val events = mutableListOf<SpotListEvent>()
        val job = launch { viewModel.events.collect { events.add(it) } }

        viewModel.checkIn(spot)
        advanceUntilIdle()

        assertNull("Lock MUST be released even on failure (R7)", viewModel.checkingInVenueId.value)
        assertEquals(1, events.size)
        assertTrue(events.last() is SpotListEvent.ShowToast)

        // Verify can retry the same spot after failure
        fakeRepository.checkInResult = Result.success(
            CheckInResult(checkinId = "c1_retry", checkinShortUrl = "https://swarmapp.com/c/c1", venueName = "Spot One")
        )
        viewModel.checkIn(spot)
        advanceUntilIdle()

        assertNull(viewModel.checkingInVenueId.value)
        assertEquals(2, events.size)
        assertTrue(events.last() is SpotListEvent.OpenX)

        job.cancel()
    }
}

// --- Test Fakes ---

class FakeTokenStore(var token: String? = "TEST_TOKEN") : TokenStore {
    override fun getOAuthToken(): String? = token
    override fun saveOAuthToken(token: String) { this.token = token }
    override fun clearOAuthToken() { this.token = null }
    override fun getClientId(): String? = null
    override fun saveClientId(clientId: String) {}
    override fun getClientSecret(): String? = null
    override fun saveClientSecret(clientSecret: String) {}
    override fun getRedirectUri(): String? = null
    override fun saveRedirectUri(uri: String) {}
}

class FakeLocationProvider(var hasPerm: Boolean = true, var coords: Coordinates? = null) : LocationProvider {
    override fun hasPermission(): Boolean = hasPerm
    override suspend fun getCurrentLocation(): Coordinates? = coords
}

class FakeSpotRepository : SpotRepository(
    api = com.masterisk_f.xwarm.data.FoursquareApi(),
    tokenStore = FakeTokenStore()
) {
    var nearbyResult: Result<List<Spot>> = Result.success(emptyList())
    var checkInResult: Result<CheckInResult> = Result.success(
        CheckInResult("id", "url", "venue")
    )

    override suspend fun getNearbySpots(lat: Double, lng: Double): Result<List<Spot>> = nearbyResult

    override suspend fun checkIn(spot: Spot, lat: Double?, lng: Double?): Result<CheckInResult> = checkInResult
}
