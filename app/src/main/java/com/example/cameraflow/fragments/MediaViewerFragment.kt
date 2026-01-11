package com.example.cameraflow.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.media3.common.Player
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.cameraflow.R
import com.example.cameraflow.adapters.MediaPagerAdapter
import com.example.cameraflow.databinding.FragmentMediaViewerBinding
import com.example.cameraflow.utils.applySystemBarsPadding
import com.example.cameraflow.utils.FormatUtils
import com.example.cameraflow.utils.showDeleteDialog
import com.example.cameraflow.viewmodel.GalleryViewModel
import com.example.cameraflow.model.MediaModel

class MediaViewerFragment : Fragment() {
    private var _binding: FragmentMediaViewerBinding? = null
    private val binding get() = _binding ?: throw RuntimeException("Non-zero value was expected")
    private val args: MediaViewerFragmentArgs by navArgs()
    private val galleryViewModel: GalleryViewModel by activityViewModels()
    private lateinit var pagerAdapter: MediaPagerAdapter
    private var player: ExoPlayer? = null
    private var isInitialized = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMediaViewerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.root.applySystemBarsPadding(true, true)

        setupViewPager()
        setupObservers()
        setupButtons()
    }

    private fun setupViewPager() {
        pagerAdapter = MediaPagerAdapter(mutableListOf<MediaModel>())

        binding.viewPager.apply {
            adapter = pagerAdapter
            orientation = ViewPager2.ORIENTATION_HORIZONTAL
            offscreenPageLimit = 1

            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    galleryViewModel.setCurrentPosition(position)
                    updatePositionIndicator(position, pagerAdapter.itemCount)
                    binding.topBar.visibility = View.VISIBLE
                }
            })
        }
    }

    private fun setupObservers() {
        galleryViewModel.mediaList.observe(viewLifecycleOwner) { list ->
            if (list.isEmpty()) {
                findNavController().navigateUp()
                return@observe
            }

            pagerAdapter.updateList(list)

            val targetPosition = if (!isInitialized) {
                args.initialPosition.coerceIn(0, list.lastIndex)
            } else {
                galleryViewModel.currentPosition.value?.coerceIn(0, list.lastIndex) ?: 0
            }

            if (binding.viewPager.currentItem != targetPosition) {
                binding.viewPager.setCurrentItem(targetPosition, false)
            }
            
            updatePositionIndicator(targetPosition, list.size)
            isInitialized = true

            binding.viewPager.post { activatePlayerForCurrentPage() }
        }

        galleryViewModel.currentPosition.observe(viewLifecycleOwner) { position ->
            if (isInitialized && binding.viewPager.currentItem != position) {
                binding.viewPager.setCurrentItem(position, false)
            }
            updatePositionIndicator(position, pagerAdapter.itemCount)
            binding.viewPager.post { activatePlayerForCurrentPage() }
        }
    }

    private fun getOrCreatePlayer(): ExoPlayer {
        if (player == null) {
            player = ExoPlayer.Builder(requireContext()).build().apply {
                repeatMode = Player.REPEAT_MODE_ONE
            }
        }
        return player!!
    }

    private fun activatePlayerForCurrentPage() {
        val position = binding.viewPager.currentItem
        val item = galleryViewModel.mediaList.value?.getOrNull(position) ?: return

        detachPlayerFromAll()

        if (!item.isVideo) {
            player?.pause()
            return
        }

        val recycler = binding.viewPager.getChildAt(0) as? RecyclerView ?: return
        val holder = recycler.findViewHolderForAdapterPosition(position)
                as? MediaPagerAdapter.MediaViewHolder ?: return

        val p = getOrCreatePlayer()
        p.setMediaItem(MediaItem.fromUri(item.uri))
        p.prepare()
        p.playWhenReady = true

        holder.attachPlayer(p)
    }

    private fun detachPlayerFromAll() {
        val recycler = binding.viewPager.getChildAt(0) as? RecyclerView ?: return
        for (i in 0 until recycler.childCount) {
            val vh = recycler.getChildViewHolder(recycler.getChildAt(i))
            (vh as? MediaPagerAdapter.MediaViewHolder)?.clearPlayer()
        }
    }
    
    private fun setupButtons() {
        binding.backButton.setOnClickListener {
            findNavController().navigateUp()
        }
        
        binding.menuButton.setOnClickListener { view ->
            showPopupMenu(view)
        }
    }

    private fun showPopupMenu(view: View) {
        PopupMenu(requireContext(), view).apply {
            menuInflater.inflate(R.menu.media_viewer_menu, menu)
            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.action_share -> { shareCurrentMedia(); true }
                    R.id.action_info -> { showMediaInfo(); true }
                    R.id.action_delete -> { showDeleteDialog { deleteCurrentMedia() }; true }
                    else -> false
                }
            }
        }.show()
    }

    private fun getCurrentItem(): MediaModel? =
        galleryViewModel.mediaList.value
            ?.getOrNull(binding.viewPager.currentItem)

    private fun updatePositionIndicator(position: Int, total: Int) {
        binding.positionIndicator.text =
            if (total > 0) "${position + 1}/$total" else ""
    }

    private fun shareCurrentMedia() {
        val item = getCurrentItem() ?: return

        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                putExtra(android.content.Intent.EXTRA_STREAM, item.uri)
                type = item.mimeType
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(android.content.Intent.createChooser(intent, getString(R.string.share)))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), getString(R.string.error_sharing), Toast.LENGTH_SHORT).show()
        }
    }

    private fun showMediaInfo() {
        val currentItem = getCurrentItem() ?: return

        val info = buildString {
            append(getString(R.string.file_name))
            append("\n")
            append("   ${currentItem.displayName}\n\n")

            append(getString(R.string.type))
            append("\n")
            append("   ${if (currentItem.isVideo) getString(R.string.video) else getString(R.string.photo)}\n")
            append("   ${currentItem.mimeType}\n\n")

            append(getString(R.string.file_size))
            append("\n")
            append("   ${FormatUtils.formatFileSize(currentItem.size)}\n\n")

            if (currentItem.width > 0 && currentItem.height > 0) {
                append(getString(R.string.resolution))
                append("\n")
                append("   ${currentItem.width} × ${currentItem.height} пикселей\n")

                val megapixels =
                    (currentItem.width.toLong() * currentItem.height.toLong()) / 1_000_000.0
                append("   ${String.format("%.1f", megapixels)} МП\n\n")
            }

            if (currentItem.isVideo && currentItem.duration > 0) {
                append(getString(R.string.duration))
                append("\n")
                append("   ${FormatUtils.formatDuration(currentItem.duration)}\n\n")
            }

            append(getString(R.string.date_created))
            append("\n")
            val dateFormat = java.text.SimpleDateFormat(
                "dd MMMM yyyy, HH:mm:ss",
                java.util.Locale.forLanguageTag("ru-RU")
            )
            append("   ${dateFormat.format(java.util.Date(currentItem.dateAdded))}\n\n")

            append(getString(R.string.uri))
            append("\n")
            append("   ${currentItem.uri}")
        }

        android.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.file_info))
            .setMessage(info)
            .setPositiveButton(getString(R.string.ok), null)
            .show()
    }

    private fun deleteCurrentMedia() {
        val item = getCurrentItem() ?: return

        detachPlayerFromAll()
        player?.pause()

        val (success, isRecoverableSecurityException) = galleryViewModel.deleteMedia(item.uri)
        if (success) {
            Toast.makeText(requireContext(), getString(R.string.file_deleted), Toast.LENGTH_SHORT).show()
        } else {
            val message = if (isRecoverableSecurityException) {
                getString(R.string.file_cannot_be_deleted)
            } else {
                getString(R.string.error_deleting_file)
            }
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            binding.viewPager.post { activatePlayerForCurrentPage() }
        }
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        player?.release()
        player = null
        _binding = null
    }
}