package com.photocleanup.ui

import com.photocleanup.api.PhotosRepository
import com.photocleanup.model.PhotoItem
import com.photocleanup.model.ScanResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * JVM unit tests for [MainViewModel] sign-in error handling.
 *
 * These tests cover the cases that would have caught the sign-in code 10
 * (DEVELOPER_ERROR) issue — specifically that:
 *   1. Actions attempted before sign-in produce a clear "Not signed in" error.
 *   2. A successful token set unblocks those actions.
 *
 * The gap that allowed code 10 to surface silently: MainActivity swallows
 * ApiException.statusCode as a raw integer in the user-visible error string
 * ("Sign-in failed: 10"). A unit test on MainViewModel alone cannot cover
 * that presentation layer, but it proves the ViewModel's sign-in gate works.
 * The emulator tests in MainActivitySignInTest cover the UI layer.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelSignInTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: PhotosRepository
    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk()
        viewModel = MainViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── startScan without token ────────────────────────────────────────────────

    @Test
    fun startScan_withoutToken_emitsNotSignedInError() = runTest {
        viewModel.startScan()

        val state = viewModel.uiState.first()
        assertTrue(
            "Expected UiState.Error when no access token is set",
            state is MainViewModel.UiState.Error
        )
        assertEquals(
            "Not signed in",
            (state as MainViewModel.UiState.Error).message
        )
    }

    @Test
    fun startScan_withoutToken_doesNotCallRepository() = runTest {
        // repository should never be reached if there's no token
        viewModel.startScan()

        testDispatcher.scheduler.advanceUntilIdle()
        // If repository.scanPhotos() were called without a mock it would throw;
        // the absence of any exception proves it was never invoked.
    }

    // ── executeCleanup without token ───────────────────────────────────────────

    @Test
    fun executeCleanup_withoutToken_emitsNotSignedInError() = runTest {
        viewModel.executeCleanup()

        val state = viewModel.uiState.first()
        assertTrue(
            "Expected UiState.Error when no access token is set",
            state is MainViewModel.UiState.Error
        )
        assertEquals(
            "Not signed in",
            (state as MainViewModel.UiState.Error).message
        )
    }

    // ── setAccessToken unblocks startScan ─────────────────────────────────────

    @Test
    fun startScan_afterTokenSet_doesNotEmitNotSignedInError() = runTest {
        coEvery {
            repository.scanPhotos(
                accessToken = any(),
                pageToken = any(),
                maxPages = any(),
                onProgress = any()
            )
        } returns Result.success(ScanResult(emptyList<PhotoItem>(), 0, null))

        viewModel.setAccessToken("fake-token")
        viewModel.startScan()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(
            "Expected ScanComplete after token is set, got: $state",
            state is MainViewModel.UiState.ScanComplete
        )
    }

    // ── UiState.Error preserves cause ─────────────────────────────────────────

    @Test
    fun uiStateError_whenRepositoryFails_containsExceptionMessage() = runTest {
        val cause = RuntimeException("network timeout")
        coEvery {
            repository.scanPhotos(
                accessToken = any(),
                pageToken = any(),
                maxPages = any(),
                onProgress = any()
            )
        } returns Result.failure(cause)

        viewModel.setAccessToken("fake-token")
        viewModel.startScan()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is MainViewModel.UiState.Error)
        val error = state as MainViewModel.UiState.Error
        assertEquals("network timeout", error.message)
        assertEquals(cause, error.cause)
    }
}
