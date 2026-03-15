package com.photocleanup.utils

import com.photocleanup.model.DetectionCategory

/**
 * Detects "Good Morning" and festive message images by matching keywords
 * against the image filename and description metadata returned by Google Photos.
 *
 * Detection strategy (no ML required, works offline):
 *  1. Filename keyword matching
 *  2. Description/caption keyword matching
 *  3. Score is the fraction of matched keyword groups
 */
object ImageKeywordDetector {

    // ── Good Morning keyword groups ────────────────────────────────────────────
    private val goodMorningGroups: List<List<String>> = listOf(
        listOf("good", "morning", "gm"),
        listOf("subah", "suprabhat", "shubh"),         // Hindi
        listOf("காலை", "வணக்கம்"),                      // Tamil
        listOf(" శుభోదయం", "మంచి ఉదయం"),                // Telugu
        listOf("ശുഭ", "പ്രഭാതം"),                       // Malayalam
        listOf("সুপ্রভাত"),                              // Bengali
        listOf("नमस्ते", "प्रभात", "सुप्रभात"),           // Hindi/Sanskrit
        listOf("صباح الخير"),                           // Arabic
        listOf("bonjour", "buongiorno", "buenos dias"), // European
        listOf("rise", "shine", "sunrise", "dawn"),
        listOf("blessings", "blessed morning"),
        listOf("have a great day", "wonderful day", "lovely day")
    )

    // ── Festive keyword groups ─────────────────────────────────────────────────
    private val festiveGroups: List<List<String>> = listOf(
        listOf("happy", "diwali", "deepavali"),
        listOf("happy", "holi", "rangwali"),
        listOf("eid", "mubarak"),
        listOf("merry", "christmas", "xmas"),
        listOf("happy", "new year", "nye"),
        listOf("navratri", "durga", "puja"),
        listOf("ganesh", "chaturthi", "ganapati"),
        listOf("raksha", "bandhan", "rakshabandhan"),
        listOf("janmashtami", "krishna", "jayanti"),
        listOf("onam", "pongal", "ugadi", "vishu"),
        listOf("baisakhi", "lohri", "makar", "sankranti"),
        listOf("independence day", "republic day"),
        listOf("thanksgiving", "halloween", "easter"),
        listOf("muharram", "navroze", "guru nanak"),
        listOf("wish you", "best wishes", "warm wishes"),
        listOf("greetings", "festive", "celebration", "celebrate"),
        listOf("happy birthday", "many more", "congrats"),
        listOf("anniversary", "wedding"),
        listOf("शुभकामनाएं", "बधाई"),                   // Hindi
        listOf("வாழ்த்துக்கள்"),                         // Tamil
        listOf("శుభాకాంక్షలు"),                          // Telugu
        listOf("ആശംസകൾ"),                               // Malayalam
        listOf("শুভেচ্ছা")                               // Bengali
    )

    data class DetectionResult(
        val isGoodMorning: Boolean,
        val isFestive: Boolean,
        val matchedKeywords: List<String>,
        val confidence: Float
    ) {
        fun toCategory(): DetectionCategory? = when {
            isGoodMorning && isFestive -> DetectionCategory.BOTH
            isGoodMorning -> DetectionCategory.GOOD_MORNING
            isFestive -> DetectionCategory.FESTIVE
            else -> null
        }
    }

    /**
     * Analyse a photo's filename and description for greeting/festive content.
     * Returns null when no match is found.
     */
    fun detect(filename: String, description: String?): DetectionResult? {
        val text = buildString {
            append(filename.lowercase())
            append(" ")
            append(description?.lowercase() ?: "")
        }

        val goodMorningMatches = matchGroups(text, goodMorningGroups)
        val festiveMatches = matchGroups(text, festiveGroups)

        val allMatches = goodMorningMatches + festiveMatches
        if (allMatches.isEmpty()) return null

        // Confidence: proportion of matched groups (capped at 1.0)
        val gmScore = if (goodMorningMatches.isNotEmpty())
            goodMorningMatches.size.toFloat() / goodMorningGroups.size else 0f
        val festiveScore = if (festiveMatches.isNotEmpty())
            festiveMatches.size.toFloat() / festiveGroups.size else 0f
        val confidence = minOf(1.0f, (gmScore + festiveScore) * 3f) // scale up for UX

        return DetectionResult(
            isGoodMorning = goodMorningMatches.isNotEmpty(),
            isFestive = festiveMatches.isNotEmpty(),
            matchedKeywords = allMatches,
            confidence = confidence.coerceAtLeast(0.3f) // floor for readability
        )
    }

    private fun matchGroups(text: String, groups: List<List<String>>): List<String> {
        val matched = mutableListOf<String>()
        for (group in groups) {
            // A group matches if ALL words in it appear somewhere in the text
            if (group.all { keyword -> text.contains(keyword, ignoreCase = true) }) {
                matched.addAll(group)
            }
        }
        return matched.distinct()
    }
}
