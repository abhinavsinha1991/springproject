package com.photocleanup.api

import com.photocleanup.model.DetectionCategory
import com.photocleanup.model.PhotoItem
import com.photocleanup.model.ReviewState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class PhotosRepositoryTest {

    private lateinit var api: GooglePhotosApi
    private lateinit var repository: PhotosRepository

    @Before
    fun setUp() {
        api = mockk()
        repository = PhotosRepository(api)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun mediaItem(
        id: String,
        filename: String,
        mimeType: String = "image/jpeg",
        description: String? = null
    ) = MediaItem(
        id = id,
        baseUrl = "http://base/$id",
        filename = filename,
        description = description,
        mediaMetadata = MediaMetadata("2024-01-01T00:00:00Z", "1080", "1920", null),
        mimeType = mimeType
    )

    private fun successPage(
        items: List<MediaItem>,
        nextToken: String? = null
    ): Response<MediaItemsResponse> =
        Response.success(MediaItemsResponse(mediaItems = items, nextPageToken = nextToken))

    @Suppress("SameParameterValue")
    private fun errorPage(code: Int): Response<MediaItemsResponse> =
        Response.error(code, ResponseBody.create(null, "Error"))

    private fun deletePhoto(id: String) = PhotoItem(
        id = id,
        baseUrl = "",
        filename = "photo_$id.jpg",
        description = null,
        creationTime = "2024-01-01T00:00:00Z",
        detectionCategory = DetectionCategory.GOOD_MORNING,
        detectionConfidence = 0.9f,
        matchedKeywords = listOf("good morning"),
        reviewState = ReviewState.DELETE
    )

    // ── scanPhotos ────────────────────────────────────────────────────────────

    @Test
    fun `scanPhotos returns detected photo when filename matches`() = runTest {
        coEvery { api.listMediaItems(any(), any(), any()) } returns
            successPage(listOf(mediaItem("1", "good morning sunshine.jpg")))

        val result = repository.scanPhotos("token", maxPages = 1)

        assertTrue(result.isSuccess)
        val scan = result.getOrThrow()
        assertEquals(1, scan.totalScanned)
        assertEquals(1, scan.detected.size)
        assertEquals("1", scan.detected[0].id)
        assertEquals(DetectionCategory.GOOD_MORNING, scan.detected[0].detectionCategory)
    }

    @Test
    fun `scanPhotos skips video mime type items from detection`() = runTest {
        coEvery { api.listMediaItems(any(), any(), any()) } returns
            successPage(listOf(
                mediaItem("1", "good morning.mp4", mimeType = "video/mp4"),
                mediaItem("2", "landscape.jpg")
            ))

        val result = repository.scanPhotos("token", maxPages = 1)

        val scan = result.getOrThrow()
        // Both items are counted, but video is skipped before detection
        assertEquals(2, scan.totalScanned)
        assertEquals(0, scan.detected.size)
    }

    @Test
    fun `scanPhotos returns failure on HTTP error`() = runTest {
        coEvery { api.listMediaItems(any(), any(), any()) } returns errorPage(401)

        val result = repository.scanPhotos("token", maxPages = 1)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("401"))
    }

    @Test
    fun `scanPhotos returns failure on network exception`() = runTest {
        coEvery { api.listMediaItems(any(), any(), any()) } throws
            java.io.IOException("connection timeout")

        val result = repository.scanPhotos("token", maxPages = 1)

        assertTrue(result.isFailure)
        assertEquals("connection timeout", result.exceptionOrNull()!!.message)
    }

    @Test
    fun `scanPhotos paginates using nextPageToken across two pages`() = runTest {
        coEvery { api.listMediaItems(any(), any(), pageToken = null) } returns
            successPage(listOf(mediaItem("1", "good morning.jpg")), nextToken = "tok2")
        coEvery { api.listMediaItems(any(), any(), pageToken = "tok2") } returns
            successPage(listOf(mediaItem("2", "happy diwali.jpg")))

        val result = repository.scanPhotos("token", maxPages = 5)

        val scan = result.getOrThrow()
        assertEquals(2, scan.totalScanned)
        assertEquals(2, scan.detected.size)
        assertNull(scan.nextPageToken)
    }

    @Test
    fun `scanPhotos stops at maxPages even when nextPageToken is always present`() = runTest {
        coEvery { api.listMediaItems(any(), any(), any()) } returns
            successPage(listOf(mediaItem("1", "photo.jpg")), nextToken = "always-more")

        val result = repository.scanPhotos("token", maxPages = 2)

        coVerify(exactly = 2) { api.listMediaItems(any(), any(), any()) }
        assertEquals("always-more", result.getOrThrow().nextPageToken)
    }

    @Test
    fun `scanPhotos stops early when no nextPageToken returned`() = runTest {
        coEvery { api.listMediaItems(any(), any(), any()) } returns
            successPage(listOf(mediaItem("1", "photo.jpg")), nextToken = null)

        repository.scanPhotos("token", maxPages = 5)

        coVerify(exactly = 1) { api.listMediaItems(any(), any(), any()) }
    }

    @Test
    fun `scanPhotos invokes onProgress callback once per page`() = runTest {
        coEvery { api.listMediaItems(any(), any(), pageToken = null) } returns
            successPage(listOf(mediaItem("1", "good morning.jpg")), nextToken = "p2")
        coEvery { api.listMediaItems(any(), any(), pageToken = "p2") } returns
            successPage(listOf(mediaItem("2", "landscape.jpg")))

        val progressCalls = mutableListOf<Pair<Int, Int>>()
        repository.scanPhotos("token", maxPages = 5) { scanned, detected ->
            progressCalls += scanned to detected
        }

        assertEquals(2, progressCalls.size)
        assertEquals(1 to 1, progressCalls[0])  // after page 1: 1 scanned, 1 detected
        assertEquals(2 to 1, progressCalls[1])  // after page 2: 2 scanned, still 1 detected
    }

    // ── deleteApprovedPhotos ──────────────────────────────────────────────────

    @Test
    fun `deleteApprovedPhotos skips when no photos marked DELETE`() = runTest {
        val photos = listOf(
            deletePhoto("1").copy(reviewState = ReviewState.KEEP),
            deletePhoto("2").copy(reviewState = ReviewState.PENDING)
        )

        val result = repository.deleteApprovedPhotos("token", photos)

        val cleanup = result.getOrThrow()
        assertEquals(0, cleanup.attempted)
        assertEquals(0, cleanup.succeeded)
        assertTrue(cleanup.failed.isEmpty())
        coVerify(exactly = 0) { api.batchDeleteMediaItems(any(), any()) }
    }

    @Test
    fun `deleteApprovedPhotos sends 51 photos as two batch requests`() = runTest {
        val photos = (1..51).map { deletePhoto("id$it") }
        coEvery { api.batchDeleteMediaItems(any(), any()) } returns
            Response.success(BatchDeleteResponse(status = null))

        repository.deleteApprovedPhotos("token", photos)

        // 50 in first batch, 1 in second
        coVerify(exactly = 2) { api.batchDeleteMediaItems(any(), any()) }
    }

    @Test
    fun `deleteApprovedPhotos tracks per-item failure from non-zero status codes`() = runTest {
        val photos = listOf(deletePhoto("a"), deletePhoto("b"), deletePhoto("c"))
        val statuses = listOf(
            DeleteStatus(code = 0, message = null),       // a: success
            DeleteStatus(code = 5, message = "Not found"), // b: failure
            DeleteStatus(code = 0, message = null)        // c: success
        )
        coEvery { api.batchDeleteMediaItems(any(), any()) } returns
            Response.success(BatchDeleteResponse(status = statuses))

        val cleanup = repository.deleteApprovedPhotos("token", photos).getOrThrow()

        assertEquals(3, cleanup.attempted)
        assertEquals(2, cleanup.succeeded)
        assertEquals(listOf("b"), cleanup.failed)
    }

    @Test
    fun `deleteApprovedPhotos marks entire chunk failed on HTTP error response`() = runTest {
        val photos = listOf(deletePhoto("x"), deletePhoto("y"))
        coEvery { api.batchDeleteMediaItems(any(), any()) } returns
            Response.error(500, ResponseBody.create(null, "Server error"))

        val cleanup = repository.deleteApprovedPhotos("token", photos).getOrThrow()

        assertEquals(2, cleanup.attempted)
        assertEquals(0, cleanup.succeeded)
        assertEquals(listOf("x", "y"), cleanup.failed)
    }

    @Test
    fun `deleteApprovedPhotos marks entire chunk failed on network exception`() = runTest {
        val photos = listOf(deletePhoto("m"), deletePhoto("n"))
        coEvery { api.batchDeleteMediaItems(any(), any()) } throws
            java.io.IOException("network unreachable")

        val cleanup = repository.deleteApprovedPhotos("token", photos).getOrThrow()

        assertEquals(0, cleanup.succeeded)
        assertEquals(listOf("m", "n"), cleanup.failed)
    }
}
