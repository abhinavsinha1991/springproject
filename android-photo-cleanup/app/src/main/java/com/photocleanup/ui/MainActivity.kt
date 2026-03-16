package com.photocleanup.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.photocleanup.BuildConfig
import com.photocleanup.PhotoCleanupApp
import com.photocleanup.R
import com.photocleanup.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var googleSignInClient: GoogleSignInClient

    // ── Google Sign-In ─────────────────────────────────────────────────────────
    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            handleSignInSuccess(account)
        } catch (e: ApiException) {
            showError("Sign-in failed: ${e.statusCode}", e)
        } catch (e: Exception) {
            showError("Sign-in failed: ${e.message ?: "unknown error"}", e)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupGoogleSignIn()
        observeViewModel()
        setupClickListeners()

        binding.tvVersion.text = "v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})"
        binding.btnErrorDismiss.setOnClickListener { hideErrorBanner() }

        // Auto sign-in if already signed in
        val existing = GoogleSignIn.getLastSignedInAccount(this)
        if (existing != null && !existing.isExpired) {
            handleSignInSuccess(existing)
        } else {
            showSignedOut()
        }
    }

    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestIdToken(getString(R.string.default_web_client_id))
            // Request the Photos Library read + write scope
            .requestScopes(
                Scope("https://www.googleapis.com/auth/photoslibrary.readonly"),
                Scope("https://www.googleapis.com/auth/photoslibrary.edit.appcreateddata")
            )
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                updateUiForState(state)
            }
        }
        lifecycleScope.launch {
            viewModel.detectedPhotos.collect { photos ->
                binding.tvDetectedCount.text = getString(
                    R.string.detected_count,
                    photos.size
                )
            }
        }
    }

    private var lastStateWasError = false

    private fun updateUiForState(state: MainViewModel.UiState) {
        // Only auto-hide the banner when the ViewModel itself is clearing an error state.
        // Hiding on every non-Error state would erase sign-in errors that are shown
        // directly via showError() and never go through the ViewModel.
        if (lastStateWasError && state !is MainViewModel.UiState.Error) hideErrorBanner()
        lastStateWasError = state is MainViewModel.UiState.Error
        when (state) {
            is MainViewModel.UiState.Idle -> {
                binding.progressBar.hide()
                binding.tvStatus.text = getString(R.string.status_ready)
                binding.btnScan.isEnabled = true
                binding.btnReview.isEnabled = false
                binding.btnCleanup.isEnabled = false
            }

            is MainViewModel.UiState.Scanning -> {
                binding.progressBar.show()
                binding.tvStatus.text = getString(
                    R.string.status_scanning,
                    state.scanned,
                    state.detected
                )
                binding.btnScan.isEnabled = false
                binding.btnReview.isEnabled = false
                binding.btnCleanup.isEnabled = false
            }

            is MainViewModel.UiState.ScanComplete -> {
                binding.progressBar.hide()
                binding.tvStatus.text = getString(
                    R.string.status_scan_complete,
                    state.totalScanned,
                    state.detected
                )
                binding.btnScan.isEnabled = true
                binding.btnLoadMore.isEnabled = state.hasMore
                binding.btnReview.isEnabled = state.detected > 0
                binding.btnCleanup.isEnabled = false
            }

            is MainViewModel.UiState.Deleting -> {
                binding.progressBar.show()
                binding.tvStatus.text = getString(
                    R.string.status_deleting,
                    state.done,
                    state.total
                )
                binding.btnScan.isEnabled = false
                binding.btnReview.isEnabled = false
                binding.btnCleanup.isEnabled = false
            }

            is MainViewModel.UiState.CleanupComplete -> {
                binding.progressBar.hide()
                val r = state.result
                binding.tvStatus.text = getString(
                    R.string.status_cleanup_complete,
                    r.succeeded,
                    r.failed.size
                )
                // Update lifetime counter
                val prefs = PhotoCleanupApp.instance.preferencesManager
                prefs.totalDeletedCount += r.succeeded
                binding.tvLifetimeDeleted.text = getString(
                    R.string.lifetime_deleted,
                    prefs.totalDeletedCount
                )
                binding.btnScan.isEnabled = true
                binding.btnReview.isEnabled = false
                binding.btnCleanup.isEnabled = false
            }

            is MainViewModel.UiState.Error -> {
                binding.progressBar.hide()
                showError(state.message, state.cause)
                binding.btnScan.isEnabled = true
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnSignIn.setOnClickListener {
            signInLauncher.launch(googleSignInClient.signInIntent)
        }
        binding.btnSignOut.setOnClickListener {
            googleSignInClient.signOut().addOnCompleteListener { showSignedOut() }
        }
        binding.btnScan.setOnClickListener {
            viewModel.startScan(loadMore = false)
        }
        binding.btnLoadMore.setOnClickListener {
            viewModel.startScan(loadMore = true)
        }
        binding.btnReview.setOnClickListener {
            startActivity(Intent(this, ReviewActivity::class.java))
        }
        binding.btnCleanup.setOnClickListener {
            confirmAndCleanup()
        }
    }

    private fun handleSignInSuccess(account: GoogleSignInAccount) {
        // Retrieve OAuth access token (cached by Google Play Services)
        GoogleSignIn.getLastSignedInAccount(this)?.serverAuthCode?.let { _ ->
            // In production: exchange authCode server-side for access token.
            // For demo: use the idToken to call your backend which returns accessToken.
        }
        // For direct API access, request access token via GoogleAuthUtil
        lifecycleScope.launch {
            try {
                val token = com.google.android.gms.auth.GoogleAuthUtil.getToken(
                    this@MainActivity,
                    account.account!!,
                    "oauth2:" +
                        "https://www.googleapis.com/auth/photoslibrary.readonly " +
                        "https://www.googleapis.com/auth/photoslibrary.edit.appcreateddata"
                )
                viewModel.setAccessToken(token)
                showSignedIn(account.email ?: "")
            } catch (e: Exception) {
                showError("Could not get access token: ${e.message}", e)
            }
        }
    }

    private fun showSignedIn(email: String) {
        binding.tvAccountInfo.text = getString(R.string.signed_in_as, email)
        binding.btnSignIn.isEnabled = false
        binding.btnSignOut.isEnabled = true
        binding.btnScan.isEnabled = true
        val prefs = PhotoCleanupApp.instance.preferencesManager
        prefs.accountEmail = email
        binding.tvLifetimeDeleted.text = getString(R.string.lifetime_deleted, prefs.totalDeletedCount)
    }

    private fun showSignedOut() {
        binding.tvAccountInfo.text = getString(R.string.not_signed_in)
        binding.btnSignIn.isEnabled = true
        binding.btnSignOut.isEnabled = false
        binding.btnScan.isEnabled = false
        binding.btnReview.isEnabled = false
        binding.btnCleanup.isEnabled = false
    }

    private fun confirmAndCleanup() {
        val count = viewModel.toDeleteCount
        if (count == 0) {
            Toast.makeText(this, "No photos marked for deletion", Toast.LENGTH_SHORT).show()
            return
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Confirm Cleanup")
            .setMessage("Permanently delete $count photo(s) from Google Photos?\nThis cannot be undone.")
            .setPositiveButton("Delete") { _, _ -> viewModel.executeCleanup() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showError(msg: String, cause: Throwable? = null) {
        val debugText = buildString {
            append(msg)
            var t: Throwable? = cause
            while (t != null) {
                append("\n[${t.javaClass.simpleName}]")
                if (t.message != null && t.message != msg) append(" ${t.message}")
                t = t.cause
            }
        }.take(200)
        binding.tvErrorMessage.text = debugText
        binding.errorBanner.visibility = View.VISIBLE
        binding.tvStatus.text = getString(R.string.status_error, msg)
    }

    private fun hideErrorBanner() {
        binding.errorBanner.visibility = View.GONE
    }
}
