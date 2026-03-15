package com.photocleanup.model

data class ScanResult(
    val totalScanned: Int,
    val detected: List<PhotoItem>,
    val nextPageToken: String? = null
)

data class CleanupResult(
    val attempted: Int,
    val succeeded: Int,
    val failed: List<String>   // list of failed photo IDs
)
