package com.photocleanup.api

import com.photocleanup.model.CleanupResult
import com.photocleanup.model.DetectionCategory
import com.photocleanup.model.PhotoItem
import com.photocleanup.model.ReviewState
import com.photocleanup.model.ScanResult
import com.photocleanup.utils.ImageKeywordDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PhotosRepository(private val api: GooglePhotosApi = PhotosApiClient.api) {

    /**
     * Scan Google Photos for good-morning / festive images.
     *
     * @param accessToken  OAuth2 bearer token obtained via Google Sign-In
     * @param pageToken    pagination token from a previous scan (null = start fresh)
     * @param maxPages     how many API pages to fetch in one call (100 items/page)
     * @param onProgress   callback: (scannedSoFar, detectedSoFar)
     */
    suspend fun scanPhotos(
        accessToken: String,
        pageToken: String? = null,
        maxPages: Int = 5,
        onProgress: ((Int, Int) -> Unit)? = null
    ): Result<ScanResult> = withContext(Dispatchers.IO) {
        try {
            val detected = mutableListOf<PhotoItem>()
            var currentToken = pageToken
            var totalScanned = 0
            var pagesLeft = maxPages
            val authHeader = "Bearer $accessToken"

            while (pagesLeft-- > 0) {
                val response = api.listMediaItems(
                    authHeader = authHeader,
                    pageSize = 100,
                    pageToken = currentToken
                )

                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("API error ${response.code()}: ${response.errorBody()?.string()}")
                    )
                }

                val body = response.body() ?: break
                val items = body.mediaItems ?: break

                for (item in items) {
                    totalScanned++
                    // Skip non-photos
                    if (item.mimeType?.startsWith("image/") == false) continue

                    val detection = ImageKeywordDetector.detect(
                        filename = item.filename,
                        description = item.description
                    )

                    if (detection != null) {
                        val category = detection.toCategory() ?: continue
                        detected += PhotoItem(
                            id = item.id,
                            baseUrl = item.baseUrl,
                            filename = item.filename,
                            description = item.description,
                            creationTime = item.mediaMetadata?.creationTime ?: "",
                            detectionCategory = category,
                            detectionConfidence = detection.confidence,
                            matchedKeywords = detection.matchedKeywords,
                            reviewState = ReviewState.PENDING
                        )
                    }
                }

                onProgress?.invoke(totalScanned, detected.size)
                currentToken = body.nextPageToken
                if (currentToken == null) break   // no more pages
            }

            Result.success(
                ScanResult(
                    totalScanned = totalScanned,
                    detected = detected,
                    nextPageToken = currentToken
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete all photos whose [ReviewState] is [ReviewState.DELETE].
     * Google Photos API allows max 50 IDs per batch request.
     */
    suspend fun deleteApprovedPhotos(
        accessToken: String,
        photos: List<PhotoItem>
    ): Result<CleanupResult> = withContext(Dispatchers.IO) {
        val toDelete = photos.filter { it.reviewState == ReviewState.DELETE }
        if (toDelete.isEmpty()) {
            return@withContext Result.success(CleanupResult(0, 0, emptyList()))
        }

        val authHeader = "Bearer $accessToken"
        val failedIds = mutableListOf<String>()
        var succeeded = 0

        // Batch in chunks of 50 (API limit)
        toDelete.chunked(50).forEach { chunk ->
            try {
                val response = api.batchDeleteMediaItems(
                    authHeader = authHeader,
                    request = BatchDeleteRequest(chunk.map { it.id })
                )

                if (response.isSuccessful) {
                    // Check per-item status if returned
                    val statuses = response.body()?.status
                    if (statuses != null) {
                        chunk.forEachIndexed { index, photo ->
                            val status = statuses.getOrNull(index)
                            if (status?.code != null && status.code != 0) {
                                failedIds += photo.id
                            } else {
                                succeeded++
                            }
                        }
                    } else {
                        succeeded += chunk.size
                    }
                } else {
                    failedIds += chunk.map { it.id }
                }
            } catch (e: Exception) {
                failedIds += chunk.map { it.id }
            }
        }

        Result.success(
            CleanupResult(
                attempted = toDelete.size,
                succeeded = succeeded,
                failed = failedIds
            )
        )
    }
}
