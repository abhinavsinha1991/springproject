package com.photocleanup.api

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.*

// ── Retrofit interface for Google Photos Library REST API ──────────────────────

interface GooglePhotosApi {

    /** List media items with optional page token */
    @GET("v1/mediaItems")
    suspend fun listMediaItems(
        @Header("Authorization") authHeader: String,
        @Query("pageSize") pageSize: Int = 100,
        @Query("pageToken") pageToken: String? = null
    ): Response<MediaItemsResponse>

    /** Search media items by filters (date range, categories, etc.) */
    @POST("v1/mediaItems:search")
    suspend fun searchMediaItems(
        @Header("Authorization") authHeader: String,
        @Body request: SearchRequest
    ): Response<MediaItemsResponse>

    /** Batch delete — requires write scope; uses the batchDelete endpoint */
    @POST("v1/mediaItems:batchDelete")
    suspend fun batchDeleteMediaItems(
        @Header("Authorization") authHeader: String,
        @Body request: BatchDeleteRequest
    ): Response<BatchDeleteResponse>
}

// ── Request / Response models ──────────────────────────────────────────────────

data class SearchRequest(
    val filters: Filters? = null,
    val pageSize: Int = 100,
    val pageToken: String? = null
)

data class Filters(
    val contentFilter: ContentFilter? = null,
    val mediaTypeFilter: MediaTypeFilter? = null
)

data class ContentFilter(
    val includedContentCategories: List<String> = emptyList()
)

data class MediaTypeFilter(
    val mediaTypes: List<String> = listOf("PHOTO")
)

data class MediaItemsResponse(
    @SerializedName("mediaItems") val mediaItems: List<MediaItem>?,
    @SerializedName("nextPageToken") val nextPageToken: String?
)

data class MediaItem(
    @SerializedName("id") val id: String,
    @SerializedName("baseUrl") val baseUrl: String,
    @SerializedName("filename") val filename: String,
    @SerializedName("description") val description: String?,
    @SerializedName("mediaMetadata") val mediaMetadata: MediaMetadata?,
    @SerializedName("mimeType") val mimeType: String?
)

data class MediaMetadata(
    @SerializedName("creationTime") val creationTime: String?,
    @SerializedName("width") val width: String?,
    @SerializedName("height") val height: String?,
    @SerializedName("photo") val photo: PhotoMetadata?
)

data class PhotoMetadata(
    @SerializedName("cameraMake") val cameraMake: String?,
    @SerializedName("cameraModel") val cameraModel: String?
)

data class BatchDeleteRequest(
    @SerializedName("mediaItemIds") val mediaItemIds: List<String>
)

data class BatchDeleteResponse(
    @SerializedName("status") val status: List<DeleteStatus>?
)

data class DeleteStatus(
    @SerializedName("code") val code: Int?,
    @SerializedName("message") val message: String?
)
