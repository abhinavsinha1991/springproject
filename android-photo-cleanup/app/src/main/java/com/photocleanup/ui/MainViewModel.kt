package com.photocleanup.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photocleanup.api.PhotosRepository
import com.photocleanup.model.CleanupResult
import com.photocleanup.model.PhotoItem
import com.photocleanup.model.ReviewState
import com.photocleanup.model.ScanResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(
    private val repository: PhotosRepository = PhotosRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // All detected photos for the current session
    private val _detectedPhotos = MutableStateFlow<List<PhotoItem>>(emptyList())
    val detectedPhotos: StateFlow<List<PhotoItem>> = _detectedPhotos.asStateFlow()

    private var accessToken: String? = null
    private var nextPageToken: String? = null

    fun setAccessToken(token: String) {
        accessToken = token
    }

    fun startScan(loadMore: Boolean = false) {
        val token = accessToken ?: run {
            _uiState.value = UiState.Error("Not signed in")
            return
        }

        viewModelScope.launch {
            _uiState.value = UiState.Scanning(scanned = 0, detected = 0)

            if (!loadMore) {
                _detectedPhotos.value = emptyList()
                nextPageToken = null
            }

            val result = repository.scanPhotos(
                accessToken = token,
                pageToken = if (loadMore) nextPageToken else null,
                maxPages = 5,
                onProgress = { scanned, detected ->
                    _uiState.value = UiState.Scanning(scanned, detected)
                }
            )

            result.fold(
                onSuccess = { scanResult ->
                    nextPageToken = scanResult.nextPageToken
                    val combined = if (loadMore) {
                        _detectedPhotos.value + scanResult.detected
                    } else {
                        scanResult.detected
                    }
                    _detectedPhotos.value = combined
                    _uiState.value = UiState.ScanComplete(
                        totalScanned = scanResult.totalScanned,
                        detected = combined.size,
                        hasMore = nextPageToken != null
                    )
                },
                onFailure = { error ->
                    _uiState.value = UiState.Error(error.message ?: "Unknown error", error)
                }
            )
        }
    }

    fun updatePhotoReviewState(photoId: String, state: ReviewState) {
        _detectedPhotos.value = _detectedPhotos.value.map { photo ->
            if (photo.id == photoId) photo.copy(reviewState = state) else photo
        }
    }

    fun markAllForDeletion() {
        _detectedPhotos.value = _detectedPhotos.value.map {
            it.copy(reviewState = ReviewState.DELETE)
        }
    }

    fun keepAll() {
        _detectedPhotos.value = _detectedPhotos.value.map {
            it.copy(reviewState = ReviewState.KEEP)
        }
    }

    fun executeCleanup() {
        val token = accessToken ?: run {
            _uiState.value = UiState.Error("Not signed in")
            return
        }
        val photos = _detectedPhotos.value
        val toDelete = photos.count { it.reviewState == ReviewState.DELETE }
        if (toDelete == 0) {
            _uiState.value = UiState.Error("No photos selected for deletion")
            return
        }

        viewModelScope.launch {
            _uiState.value = UiState.Deleting(total = toDelete, done = 0)

            val result = repository.deleteApprovedPhotos(token, photos)

            result.fold(
                onSuccess = { cleanupResult ->
                    // Remove successfully deleted photos from the list
                    val failedIds = cleanupResult.failed.toSet()
                    _detectedPhotos.value = _detectedPhotos.value.filter { photo ->
                        photo.reviewState != ReviewState.DELETE || photo.id in failedIds
                    }
                    _uiState.value = UiState.CleanupComplete(cleanupResult)
                },
                onFailure = { error ->
                    _uiState.value = UiState.Error(error.message ?: "Deletion failed", error)
                }
            )
        }
    }

    val pendingCount get() = _detectedPhotos.value.count { it.reviewState == ReviewState.PENDING }
    val toDeleteCount get() = _detectedPhotos.value.count { it.reviewState == ReviewState.DELETE }
    val toKeepCount  get() = _detectedPhotos.value.count { it.reviewState == ReviewState.KEEP }

    sealed class UiState {
        object Idle : UiState()
        data class Scanning(val scanned: Int, val detected: Int) : UiState()
        data class ScanComplete(val totalScanned: Int, val detected: Int, val hasMore: Boolean) : UiState()
        data class Deleting(val total: Int, val done: Int) : UiState()
        data class CleanupComplete(val result: CleanupResult) : UiState()
        data class Error(val message: String, val cause: Throwable? = null) : UiState()
    }
}
