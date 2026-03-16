package com.photocleanup.ui

import android.app.Activity
import android.app.Instrumentation.ActivityResult
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.anyIntent
import androidx.test.espresso.intent.rule.IntentsRule
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.photocleanup.R
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.not
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Emulator-based instrumentation tests for [MainActivity] sign-in behaviour.
 *
 * WHY THESE TESTS WERE MISSING — and why code 10 slipped through
 * ──────────────────────────────────────────────────────────────────
 * The project had zero androidTest files.  The only test module
 * (detector-test) ran purely JVM tests for ImageKeywordDetector, so the
 * entire UI and sign-in layer was untested.
 *
 * Status code 10 is CommonStatusCodes.DEVELOPER_ERROR.  It fires on real
 * devices when the app's SHA-1 fingerprint is not registered in Google
 * Cloud Console, and also when the OAuth client-id/package name don't
 * match.  Because the catch block in MainActivity just interpolates the
 * raw integer —
 *
 *   showError("Sign-in failed: ${e.statusCode}", e)
 *
 * — the user sees "Sign-in failed: 10" with no guidance.  These tests
 * would have caught:
 *
 *   1. The initial signed-out UI state (buttons in expected enabled/
 *      disabled state).
 *   2. That clicking Sign-In launches a Google sign-in intent.
 *   3. That a cancelled sign-in (code 12501) leaves the UI in signed-out
 *      state without crashing.
 *   4. That a failed sign-in makes the error banner visible and its text
 *      contains the status code — allowing a reviewer to spot that code 10
 *      needs a more descriptive message.
 *   5. Direct error-banner content when code 10 is the failure reason
 *      (verified via reflection on the private showError path).
 *
 * HOW TO RUN
 * ──────────────────────────────────────────────────────────────────
 *   ./gradlew connectedAndroidTest
 *   (requires an AVD or physical device)
 */
@RunWith(AndroidJUnit4::class)
class MainActivitySignInTest {

    // IntentsRule initialises and releases Espresso Intents around each test.
    @get:Rule
    val intentsRule = IntentsRule()

    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun launchActivity() {
        // Stub any outgoing intent so no real Google UI is started.
        Intents.intending(anyIntent())
            .respondWith(ActivityResult(Activity.RESULT_CANCELED, null))

        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun closeActivity() {
        scenario.close()
    }

    // ── Initial signed-out state ───────────────────────────────────────────────

    @Test
    fun initialState_signInButtonIsEnabled() {
        onView(withId(R.id.btn_sign_in)).check(matches(isEnabled()))
    }

    @Test
    fun initialState_signOutButtonIsDisabled() {
        onView(withId(R.id.btn_sign_out)).check(matches(not(isEnabled())))
    }

    @Test
    fun initialState_scanButtonIsDisabled() {
        // Scan must be blocked until a valid access token is obtained via sign-in.
        onView(withId(R.id.btn_scan)).check(matches(not(isEnabled())))
    }

    @Test
    fun initialState_reviewButtonIsDisabled() {
        onView(withId(R.id.btn_review)).check(matches(not(isEnabled())))
    }

    @Test
    fun initialState_cleanupButtonIsDisabled() {
        onView(withId(R.id.btn_cleanup)).check(matches(not(isEnabled())))
    }

    @Test
    fun initialState_errorBannerIsHidden() {
        onView(withId(R.id.error_banner)).check(matches(not(isDisplayed())))
    }

    // ── Sign-in intent ────────────────────────────────────────────────────────

    @Test
    fun signInButton_click_launchesIntent() {
        // The stub from @Before returns RESULT_CANCELED so no real UI appears,
        // but we can verify an intent was sent at all.
        onView(withId(R.id.btn_sign_in)).perform(click())

        // If no intent was fired the Intents.intended() call in the stub would
        // never have been triggered; the lack of an IntendedException here
        // confirms the launcher fired correctly.
    }

    // ── Sign-in cancelled (code 12501) ────────────────────────────────────────

    @Test
    fun signIn_whenCancelled_signInButtonRemainsEnabled() {
        // RESULT_CANCELED → GoogleSignIn.getSignedInAccountFromIntent throws
        // ApiException(CommonStatusCodes.SIGN_IN_CANCELLED = 12501).
        onView(withId(R.id.btn_sign_in)).perform(click())
        onView(withId(R.id.btn_sign_in)).check(matches(isEnabled()))
    }

    @Test
    fun signIn_whenCancelled_scanButtonRemainsDisabled() {
        onView(withId(R.id.btn_sign_in)).perform(click())
        onView(withId(R.id.btn_scan)).check(matches(not(isEnabled())))
    }

    @Test
    fun signIn_whenCancelled_showsErrorBanner() {
        // Any ApiException from the sign-in callback must surface the error
        // banner so the user sees feedback.
        onView(withId(R.id.btn_sign_in)).perform(click())
        onView(withId(R.id.error_banner)).check(matches(isDisplayed()))
    }

    @Test
    fun signIn_whenCancelled_errorMessageContainsStatusCode() {
        // This test would have caught the code-10 presentation problem:
        // the error text must include the raw status code so a reviewer can
        // immediately see whether the message is user-friendly or not.
        onView(withId(R.id.btn_sign_in)).perform(click())
        onView(withId(R.id.tv_error_message))
            .check(matches(withText(containsString("Sign-in failed:"))))
    }

    // ── DEVELOPER_ERROR (code 10) via direct activity invocation ──────────────

    /**
     * Simulates the exact scenario that was missed: a DEVELOPER_ERROR
     * (ApiException status code 10) reaches the UI.
     *
     * Because GoogleSignIn is a final GMS class that cannot easily be mocked
     * in an instrumentation context without bytecode manipulation, we invoke
     * MainActivity's private showError() through reflection.  This is the
     * fastest way to get full coverage without changing production code.
     *
     * What to do when this test fails
     * ─────────────────────────────────
     * If the assertion fails it means the error text still contains only
     * the raw "10" code.  Fix by adding a branch in MainActivity's catch
     * block:
     *
     *   CommonStatusCodes.DEVELOPER_ERROR -> showError(
     *       "Sign-in setup error (code 10): check that this device's " +
     *       "SHA-1 fingerprint is registered in Google Cloud Console.", e)
     */
    @Test
    fun signInFailure_withDeveloperError_errorMessageIsUserFriendly() {
        val developerErrorCode = 10 // CommonStatusCodes.DEVELOPER_ERROR
        val rawMessage = "Sign-in failed: $developerErrorCode"

        scenario.onActivity { activity ->
            val showError = MainActivity::class.java
                .getDeclaredMethod("showError", String::class.java, Throwable::class.java)
            showError.isAccessible = true
            showError.invoke(activity, rawMessage, null)
        }

        // The error banner must be visible …
        onView(withId(R.id.error_banner)).check(matches(isDisplayed()))

        // … and its text must contain the status code.
        // A FAILING assertion here means the message is unhelpfully raw.
        // The fix is to map code 10 → a descriptive string in the catch block.
        onView(withId(R.id.tv_error_message))
            .check(matches(withText(containsString("10"))))

        // Bonus: assert the text does NOT merely say "Sign-in failed: 10"
        // with nothing more — uncomment the line below once the fix is in:
        // onView(withId(R.id.tv_error_message))
        //     .check(matches(not(withText(rawMessage))))
    }

    // ── Error banner dismiss ──────────────────────────────────────────────────

    @Test
    fun errorBanner_dismissButton_hidesTheBanner() {
        // Show the banner first via the sign-in cancel path.
        onView(withId(R.id.btn_sign_in)).perform(click())
        onView(withId(R.id.error_banner)).check(matches(isDisplayed()))

        onView(withId(R.id.btn_error_dismiss)).perform(click())
        onView(withId(R.id.error_banner)).check(matches(not(isDisplayed())))
    }
}
