package com.example.cameraflow.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.cameraflow.databinding.ItemMediaBinding
import com.example.cameraflow.model.MediaModel
import com.example.cameraflow.utils.FormatUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MediaAdapter(
    private val items: MutableList<MediaModel>,
    private val onItemClick: (MediaModel) -> Unit,
    private val onItemLongClick: (MediaModel) -> Unit
) : RecyclerView.Adapter<MediaAdapter.ViewHolder>() {

    inner class ViewHolder(
        private val binding: ItemMediaBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: MediaModel) {
            Glide.with(binding.root)
                .load(item.uri)
                .centerCrop()
                .into(binding.thumbnailImageView)

            binding.videoIcon.visibility =
                if (item.isVideo) View.VISIBLE else View.GONE

            if (item.isVideo && item.duration > 0) {
                binding.durationTextView.visibility = View.VISIBLE
                binding.durationTextView.text =
                    FormatUtils.formatDuration(item.duration)
            } else {
                binding.durationTextView.visibility = View.GONE
            }

            binding.dateTextView.text =
                SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                    .format(Date(item.dateAdded))

            binding.root.setOnClickListener {
                onItemClick(item)
            }

            binding.root.setOnLongClickListener {
                onItemLongClick(item)
                true
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemMediaBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun updateList(newItems: List<MediaModel>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}