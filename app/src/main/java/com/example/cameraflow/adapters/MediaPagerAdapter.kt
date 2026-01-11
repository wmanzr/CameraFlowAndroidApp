package com.example.cameraflow.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.example.cameraflow.databinding.ItemMediaPageBinding
import com.example.cameraflow.model.MediaModel
import androidx.media3.exoplayer.ExoPlayer

class MediaPagerAdapter(private val items: MutableList<MediaModel>) : RecyclerView.Adapter<MediaPagerAdapter.MediaViewHolder>() {
    var activePosition: Int = RecyclerView.NO_POSITION
        set(value) {
            val old = field
            field = value
            if (old != RecyclerView.NO_POSITION) {
                notifyItemChanged(old)
            }
            if (value != RecyclerView.NO_POSITION) {
                notifyItemChanged(value)
            }
        }

    inner class MediaViewHolder(
        internal val binding: ItemMediaPageBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: MediaModel, isActive: Boolean) {
            binding.playerView.player = null

            if (item.isVideo) {
                if (isActive) {
                    binding.imageView.visibility = View.GONE
                    binding.playerContainer.visibility = View.VISIBLE
                } else {
                    binding.playerContainer.visibility = View.GONE
                    binding.imageView.visibility = View.VISIBLE
                    binding.imageView.setBackgroundColor(Color.BLACK)
                    binding.imageView.setImageDrawable(null)
                }
            } else {
                binding.playerContainer.visibility = View.GONE
                binding.imageView.visibility = View.VISIBLE
                binding.imageView.setBackgroundColor(Color.TRANSPARENT)

                Glide.with(binding.root.context)
                    .load(item.uri)
                    .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                    .fitCenter()
                    .into(binding.imageView)
            }
        }

        fun attachPlayer(player: ExoPlayer) {
            binding.imageView.visibility = View.GONE
            binding.playerContainer.visibility = View.VISIBLE
            binding.playerView.player = player
        }

        fun clearPlayer() {
            binding.playerView.player = null
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        val binding = ItemMediaPageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MediaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        holder.bind(
            item = items[position],
            isActive = position == activePosition
        )
    }

    override fun getItemCount(): Int = items.size

    fun updateList(newList: List<MediaModel>) {
        items.clear()
        items.addAll(newList)
        activePosition = RecyclerView.NO_POSITION
        notifyDataSetChanged()
    }
}