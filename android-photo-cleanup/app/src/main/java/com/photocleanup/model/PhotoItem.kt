package com.photocleanup.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class PhotoItem(
    val id: String,
    val baseUrl: String,          // Google Photos base URL (expires in ~60 min)
    val filename: String,
    val description: String?,
    val creationTime: String,
    val detectionCategory: DetectionCategory,
    val detectionConfidence: Float,
    val matchedKeywords: List<String>,
    var reviewState: ReviewState = ReviewState.PENDING
) : Parcelable

enum class DetectionCategory {
    GOOD_MORNING,
    FESTIVE,
    BOTH;

    fun displayLabel(): String = when (this) {
        GOOD_MORNING -> "Good Morning"
        FESTIVE -> "Festive"
        BOTH -> "Good Morning + Festive"
    }
}

enum class ReviewState {
    PENDING,    // Not yet reviewed
    KEEP,       // User wants to keep this photo
    DELETE      // User approved deletion
}
