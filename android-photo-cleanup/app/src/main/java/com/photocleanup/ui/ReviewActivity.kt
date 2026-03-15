package com.photocleanup.ui

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.photocleanup.R
import com.photocleanup.databinding.ActivityReviewBinding
import com.photocleanup.model.ReviewState
import kotlinx.coroutines.launch

/**
 * Full-screen review screen.
 * Shows all detected photos in a grid; user swipes or taps to mark Keep / Delete.
 * A floating confirm button triggers the cleanup in MainViewModel.
 */
class ReviewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReviewBinding

    // Share the same ViewModel with MainActivity via the activity scope
    private val viewModel: MainViewModel by viewModels()

    private lateinit var adapter: PhotoReviewAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Review Photos"

        setupRecyclerView()
        observePhotos()
        setupButtons()
    }

    private fun setupRecyclerView() {
        adapter = PhotoReviewAdapter(
            onKeep = { photo ->
                viewModel.updatePhotoReviewState(photo.id, ReviewState.KEEP)
            },
            onDelete = { photo ->
                viewModel.updatePhotoReviewState(photo.id, ReviewState.DELETE)
            },
            onUndo = { photo ->
                viewModel.updatePhotoReviewState(photo.id, ReviewState.PENDING)
            }
        )
        binding.recyclerView.apply {
            layoutManager = GridLayoutManager(this@ReviewActivity, 2)
            adapter = this@ReviewActivity.adapter
        }
    }

    private fun observePhotos() {
        lifecycleScope.launch {
            viewModel.detectedPhotos.collect { photos ->
                adapter.submitList(photos.toList())
                updateSummaryBar(
                    pending = photos.count { it.reviewState == ReviewState.PENDING },
                    keep   = photos.count { it.reviewState == ReviewState.KEEP },
                    delete = photos.count { it.reviewState == ReviewState.DELETE }
                )
                binding.btnConfirmDelete.isEnabled =
                    photos.any { it.reviewState == ReviewState.DELETE }
            }
        }
    }

    private fun updateSummaryBar(pending: Int, keep: Int, delete: Int) {
        binding.tvSummary.text = getString(R.string.review_summary, pending, keep, delete)
    }

    private fun setupButtons() {
        binding.btnConfirmDelete.setOnClickListener {
            val count = viewModel.toDeleteCount
            if (count == 0) return@setOnClickListener
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Confirm Deletion")
                .setMessage(
                    "You have approved $count photo(s) for permanent deletion.\n" +
                    "Kept: ${viewModel.toKeepCount}  •  Pending: ${viewModel.pendingCount}\n\n" +
                    "This action cannot be undone!"
                )
                .setPositiveButton("Delete $count Photos") { _, _ ->
                    viewModel.executeCleanup()
                    finish()
                }
                .setNegativeButton("Go Back", null)
                .show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.review_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { onBackPressedDispatcher.onBackPressed(); true }
            R.id.action_select_all -> {
                viewModel.markAllForDeletion()
                Toast.makeText(this, "All marked for deletion", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_keep_all -> {
                viewModel.keepAll()
                Toast.makeText(this, "All marked to keep", Toast.LENGTH_SHORT).show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
