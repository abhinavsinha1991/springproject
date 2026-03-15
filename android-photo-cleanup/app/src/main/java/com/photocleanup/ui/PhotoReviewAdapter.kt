package com.photocleanup.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.RoundedCornersTransformation
import com.photocleanup.R
import com.photocleanup.databinding.ItemPhotoReviewBinding
import com.photocleanup.model.PhotoItem
import com.photocleanup.model.ReviewState

class PhotoReviewAdapter(
    private val onKeep: (PhotoItem) -> Unit,
    private val onDelete: (PhotoItem) -> Unit,
    private val onUndo: (PhotoItem) -> Unit
) : ListAdapter<PhotoItem, PhotoReviewAdapter.PhotoViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val binding = ItemPhotoReviewBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return PhotoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PhotoViewHolder(
        private val binding: ItemPhotoReviewBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(photo: PhotoItem) {
            val ctx = binding.root.context

            // Load thumbnail (append =w400-h400 to cap size)
            binding.ivPhoto.load("${photo.baseUrl}=w400-h400") {
                crossfade(true)
                transformations(RoundedCornersTransformation(8f))
                placeholder(R.drawable.ic_photo_placeholder)
                error(R.drawable.ic_photo_placeholder)
            }

            // Labels
            binding.tvFilename.text = photo.filename
            binding.tvCategory.text = photo.detectionCategory.displayLabel()
            binding.tvKeywords.text = photo.matchedKeywords.take(3).joinToString(" · ")
            binding.tvConfidence.text = ctx.getString(
                R.string.confidence_pct, (photo.detectionConfidence * 100).toInt()
            )

            // State-based styling
            when (photo.reviewState) {
                ReviewState.PENDING -> {
                    binding.root.setCardBackgroundColor(
                        ContextCompat.getColor(ctx, R.color.card_pending)
                    )
                    binding.ivStateIcon.setImageResource(R.drawable.ic_pending)
                    binding.btnKeep.isEnabled = true
                    binding.btnDelete.isEnabled = true
                    binding.btnUndo.isEnabled = false
                }
                ReviewState.KEEP -> {
                    binding.root.setCardBackgroundColor(
                        ContextCompat.getColor(ctx, R.color.card_keep)
                    )
                    binding.ivStateIcon.setImageResource(R.drawable.ic_check_green)
                    binding.btnKeep.isEnabled = false
                    binding.btnDelete.isEnabled = true
                    binding.btnUndo.isEnabled = true
                }
                ReviewState.DELETE -> {
                    binding.root.setCardBackgroundColor(
                        ContextCompat.getColor(ctx, R.color.card_delete)
                    )
                    binding.ivStateIcon.setImageResource(R.drawable.ic_delete_red)
                    binding.btnKeep.isEnabled = true
                    binding.btnDelete.isEnabled = false
                    binding.btnUndo.isEnabled = true
                }
            }

            binding.btnKeep.setOnClickListener { onKeep(photo) }
            binding.btnDelete.setOnClickListener { onDelete(photo) }
            binding.btnUndo.setOnClickListener { onUndo(photo) }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<PhotoItem>() {
            override fun areItemsTheSame(oldItem: PhotoItem, newItem: PhotoItem) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: PhotoItem, newItem: PhotoItem) =
                oldItem == newItem
        }
    }
}
