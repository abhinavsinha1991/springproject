package com.photocleanup

import kotlin.test.*
import org.junit.jupiter.api.Test

class ImageKeywordDetectorTest {

    // ── Helper ────────────────────────────────────────────────────────────────
    private fun detect(filename: String, desc: String? = null) =
        ImageKeywordDetector.detect(filename, desc)

    // ── Good Morning detection ────────────────────────────────────────────────

    @Test fun `detects 'good morning' in filename`() {
        val r = detect("Good_Morning_Flowers.jpg")
        assertNotNull(r)
        assertTrue(r.isGoodMorning, "should be good morning")
        assertFalse(r.isFestive,    "should not be festive")
        assertEquals(DetectionCategory.GOOD_MORNING, r.toCategory())
        println("  keywords: ${r.matchedKeywords}  confidence: ${"%.0f".format(r.confidence*100)}%")
    }

    @Test fun `detects suprabhat in description`() {
        val r = detect("IMG_20231001.jpg", "Suprabhat! Have a wonderful day")
        assertNotNull(r)
        assertTrue(r.isGoodMorning)
        assertEquals(DetectionCategory.GOOD_MORNING, r.toCategory())
        println("  keywords: ${r.matchedKeywords}")
    }

    @Test fun `detects GM shorthand in filename`() {
        val r = detect("gm_nature.jpg")
        assertNotNull(r)
        assertTrue(r.isGoodMorning)
        println("  keywords: ${r.matchedKeywords}")
    }

    @Test fun `detects 'rise and shine' in description`() {
        val r = detect("photo.jpg", "rise and shine beautiful morning")
        assertNotNull(r)
        assertTrue(r.isGoodMorning)
        println("  keywords: ${r.matchedKeywords}")
    }

    // ── Festive detection ─────────────────────────────────────────────────────

    @Test fun `detects Happy Diwali in filename`() {
        val r = detect("Happy_Diwali_2023.jpg")
        assertNotNull(r)
        assertFalse(r.isGoodMorning, "should not be good morning")
        assertTrue(r.isFestive)
        assertEquals(DetectionCategory.FESTIVE, r.toCategory())
        println("  keywords: ${r.matchedKeywords}  confidence: ${"%.0f".format(r.confidence*100)}%")
    }

    @Test fun `detects Eid Mubarak in description`() {
        val r = detect("greeting.png", "Eid Mubarak to you and your family!")
        assertNotNull(r)
        assertTrue(r.isFestive)
        println("  keywords: ${r.matchedKeywords}")
    }

    @Test fun `detects Merry Christmas in filename`() {
        val r = detect("Merry_Christmas_wishes.jpg")
        assertNotNull(r)
        assertTrue(r.isFestive)
        println("  keywords: ${r.matchedKeywords}")
    }

    @Test fun `detects Holi in filename`() {
        val r = detect("happy_holi_colors.jpg")
        assertNotNull(r)
        assertTrue(r.isFestive)
        println("  keywords: ${r.matchedKeywords}")
    }

    @Test fun `detects Happy New Year`() {
        val r = detect("Happy_New_Year_2024.jpg")
        assertNotNull(r)
        assertTrue(r.isFestive)
        println("  keywords: ${r.matchedKeywords}")
    }

    @Test fun `detects Onam`() {
        val r = detect("photo.jpg", "Happy Onam to everyone!")
        assertNotNull(r)
        assertTrue(r.isFestive)
        println("  keywords: ${r.matchedKeywords}")
    }

    @Test fun `detects warm wishes in description`() {
        val r = detect("IMG_001.jpg", "warm wishes to all")
        assertNotNull(r)
        assertTrue(r.isFestive)
        println("  keywords: ${r.matchedKeywords}")
    }

    @Test fun `detects happy birthday`() {
        val r = detect("happy birthday to you.jpg")
        assertNotNull(r)
        assertTrue(r.isFestive)
        println("  keywords: ${r.matchedKeywords}")
    }

    // ── BOTH category ─────────────────────────────────────────────────────────

    @Test fun `detects BOTH when good morning and festive present`() {
        val r = detect("good_morning_happy_diwali.jpg")
        assertNotNull(r)
        assertTrue(r.isGoodMorning)
        assertTrue(r.isFestive)
        assertEquals(DetectionCategory.BOTH, r.toCategory())
        println("  keywords: ${r.matchedKeywords}")
    }

    @Test fun `detects BOTH in description`() {
        val r = detect("photo.jpg", "Good morning! Happy Ganesh chaturthi")
        assertNotNull(r)
        assertTrue(r.isGoodMorning)
        assertTrue(r.isFestive)
        assertEquals(DetectionCategory.BOTH, r.toCategory())
        println("  keywords: ${r.matchedKeywords}")
    }

    // ── Non-greeting photos (should NOT be detected) ──────────────────────────

    @Test fun `no match for regular photo filename`() {
        val r = detect("DSC_1234.jpg")
        assertNull(r, "plain camera filename should not match")
    }

    @Test fun `no match for landscape photo`() {
        val r = detect("sunset_mountains.jpg", "Beautiful sunset at the mountains")
        assertNull(r)
    }

    @Test fun `no match for selfie`() {
        val r = detect("selfie_at_beach.jpg", "Having fun at the beach!")
        assertNull(r)
    }

    @Test fun `no match for food photo`() {
        val r = detect("IMG_20230601.jpg", "Delicious biryani for lunch")
        assertNull(r)
    }

    @Test fun `no match for screenshot`() {
        val r = detect("Screenshot_20230901.png")
        assertNull(r)
    }

    // ── Confidence scores ─────────────────────────────────────────────────────

    @Test fun `confidence is in range 0 to 1`() {
        val r = detect("Good Morning sunshine.jpg")
        assertNotNull(r)
        assertTrue(r.confidence in 0f..1f,
            "confidence ${r.confidence} must be in [0,1]")
        println("  confidence: ${"%.0f".format(r.confidence*100)}%")
    }

    @Test fun `confidence has minimum floor for single match`() {
        val r = detect("gm.jpg")
        assertNotNull(r)
        assertTrue(r.confidence >= 0.3f,
            "confidence ${r.confidence} should be >= floor 0.3")
    }

    // ── Edge cases ────────────────────────────────────────────────────────────

    @Test fun `null description is handled safely`() {
        val r = detect("good_morning.jpg", null)
        assertNotNull(r)
        assertTrue(r.isGoodMorning)
    }

    @Test fun `case insensitive matching`() {
        val r1 = detect("GOOD MORNING EVERYONE.jpg")
        val r2 = detect("good morning everyone.jpg")
        val r3 = detect("Good Morning Everyone.jpg")
        assertNotNull(r1); assertNotNull(r2); assertNotNull(r3)
        assertEquals(r1?.isGoodMorning, r2?.isGoodMorning)
        assertEquals(r2?.isGoodMorning, r3?.isGoodMorning)
    }

    @Test fun `matched keywords list is non-empty on match`() {
        val r = detect("Happy Diwali wishes for you.jpg")
        assertNotNull(r)
        assertTrue(r.matchedKeywords.isNotEmpty())
        println("  matched: ${r.matchedKeywords}")
    }
}
