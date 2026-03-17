package com.photocleanup.ui

import com.photocleanup.api.PhotosRepository
import com.photocleanup.model.CleanupResult
import com.photocleanup.model.DetectionCategory
import com.photocleanup.model.PhotoItem
import com.photocleanup.model.ReviewState
import com.photocleanup.model.ScanResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelScanTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: PhotosRepository
    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk()
        viewModel = MainViewModel(repository)
        viewModel.setAccessToken("test-token")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun photoItem(id: String, state: ReviewState = ReviewState.PENDING) = PhotoItem(
        id = id,
        baseUrl = "",
        filename = "good_morning_$id.jpg",
        description = null,
        creationTime = "2024-01-01T00:00:00Z",
        detectionCategory = DetectionCategory.GOOD_MORNING,
        detectionConfidence = 0.9f,
        matchedKeywords = listOf("good morning"),
        reviewState = state
    )

    private fun scanResult(
        items: List<PhotoItem> = emptyList(),
        token: String? = null
    ) = ScanResult(totalScanned = items.size + 10, detected = items, nextPageToken = token)

    // ── startScan ─────────────────────────────────────────────────────────────

    @Test
    fun `startScan emits Scanning then ScanComplete with correct count`() = runTest {
        coEvery { repository.scanPhotos(any(), any(), any(), any()) } returns
            Result.success(scanResult(listOf(photoItem("1"), photoItem("2"))))

        viewModel.startScan()
        assertTrue(viewModel.uiState.value is MainViewModel.UiState.Scanning)

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is MainViewModel.UiState.ScanComplete)
        assertEquals(2, (state as MainViewModel.UiState.ScanComplete).detected)
    }

    @Test
    fun `startScan fresh scan replaces previously detected photos`() = runTest {
        coEvery { repository.scanPhotos(any(), any(), any(), any()) } returns
            Result.success(scanResult(listOf(photoItem("1"), photoItem("2"))))
        viewModel.startScan()
        testDispatcher.scheduler.advanceUntilIdle()

        coEvery { repository.scanPhotos(any(), any(), any(), any()) } returns
            Result.success(scanResult(listOf(photoItem("3"))))
        viewModel.startScan()
        testDispatcher.scheduler.advanceUntilIdle()

        val photos = viewModel.detectedPhotos.value
        assertEquals(1, photos.size)
        assertEquals("3", photos[0].id)
    }

    @Test
    fun `startScan loadMore appends new photos to existing list`() = runTest {
        coEvery { repository.scanPhotos(any(), pageToken = null, any(), any()) } returns
            Result.success(scanResult(listOf(photoItem("1"), photoItem("2")), token = "page2"))
        viewModel.startScan()
        testDispatcher.scheduler.advanceUntilIdle()

        coEvery { repository.scanPhotos(any(), pageToken = "page2", any(), any()) } returns
            Result.success(scanResult(listOf(photoItem("3"))))
        viewModel.startScan(loadMore = true)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(3, viewModel.detectedPhotos.value.size)
    }

    @Test
    fun `startScan sets hasMore true when nextPageToken is present`() = runTest {
        coEvery { repository.scanPhotos(any(), any(), any(), any()) } returns
            Result.success(scanResult(token = "more-pages"))

        viewModel.startScan()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value as MainViewModel.UiState.ScanComplete
        assertTrue(state.hasMore)
    }

    @Test
    fun `startScan repository failure emits Error state with message`() = runTest {
        coEvery { repository.scanPhotos(any(), any(), any(), any()) } returns
            Result.failure(RuntimeException("API quota exceeded"))

        viewModel.startScan()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is MainViewModel.UiState.Error)
        assertEquals("API quota exceeded", (state as MainViewModel.UiState.Error).message)
    }

    // ── executeCleanup ────────────────────────────────────────────────────────

    @Test
    fun `executeCleanup emits error when no photos are marked for deletion`() = runTest {
        coEvery { repository.scanPhotos(any(), any(), any(), any()) } returns
            Result.success(scanResult(listOf(
                photoItem("1", ReviewState.KEEP),
                photoItem("2", ReviewState.PENDING)
            )))
        viewModel.startScan()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.executeCleanup()

        val state = viewModel.uiState.value
        assertTrue(state is MainViewModel.UiState.Error)
        assertTrue((state as MainViewModel.UiState.Error).message.contains("No photos selected"))
    }

    @Test
    fun `executeCleanup success removes deleted photos from detected list`() = runTest {
        coEvery { repository.scanPhotos(any(), any(), any(), any()) } returns
            Result.success(scanResult(listOf(photoItem("a"), photoItem("b"))))
        viewModel.startScan()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.updatePhotoReviewState("a", ReviewState.DELETE)
        viewModel.updatePhotoReviewState("b", ReviewState.KEEP)

        coEvery { repository.deleteApprovedPhotos(any(), any()) } returns
            Result.success(CleanupResult(attempted = 1, succeeded = 1, failed = emptyList()))
        viewModel.executeCleanup()
        testDispatcher.scheduler.advanceUntilIdle()

        val photos = viewModel.detectedPhotos.value
        assertEquals(1, photos.size)
        assertEquals("b", photos[0].id)
    }

    @Test
    fun `executeCleanup partial failure keeps failed photos in detected list`() = runTest {
        coEvery { repository.scanPhotos(any(), any(), any(), any()) } returns
            Result.success(scanResult(listOf(photoItem("p"), photoItem("q"))))
        viewModel.startScan()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.markAllForDeletion()

        coEvery { repository.deleteApprovedPhotos(any(), any()) } returns
            Result.success(CleanupResult(attempted = 2, succeeded = 1, failed = listOf("q")))
        viewModel.executeCleanup()
        testDispatcher.scheduler.advanceUntilIdle()

        val photos = viewModel.detectedPhotos.value
        assertEquals(1, photos.size)
        assertEquals("q", photos[0].id)
    }

    // ── review state helpers ──────────────────────────────────────────────────

    @Test
    fun `markAllForDeletion and keepAll correctly flip all photo states`() = runTest {
        coEvery { repository.scanPhotos(any(), any(), any(), any()) } returns
            Result.success(scanResult(listOf(photoItem("1"), photoItem("2"), photoItem("3"))))
        viewModel.startScan()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.markAllForDeletion()
        assertEquals(3, viewModel.toDeleteCount)
        assertEquals(0, viewModel.toKeepCount)
        assertEquals(0, viewModel.pendingCount)

        viewModel.keepAll()
        assertEquals(0, viewModel.toDeleteCount)
        assertEquals(3, viewModel.toKeepCount)
    }
}
