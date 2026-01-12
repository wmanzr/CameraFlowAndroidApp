 package com.example.cameraflow.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.example.cameraflow.viewmodel.GalleryViewModel
import com.example.cameraflow.R
import com.example.cameraflow.databinding.FragmentGalleryBinding
import com.example.cameraflow.utils.applySystemBarsPadding
import com.example.cameraflow.utils.PermissionHelper
import com.example.cameraflow.utils.FormatUtils
import com.example.cameraflow.utils.showDeleteDialog
import com.example.cameraflow.utils.shouldShowRationale
import com.example.cameraflow.model.MediaModel
import com.example.cameraflow.adapters.MediaAdapter

 class GalleryFragment : Fragment() {
    private var _binding: FragmentGalleryBinding? = null
    private val binding get() = _binding ?: throw RuntimeException("Non-zero value was expected")
    private val galleryViewModel: GalleryViewModel by activityViewModels()
    private lateinit var adapter: MediaAdapter
    private var isSortDescending = true
    
    private val storagePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            PermissionHelper.handleMultiplePermissionsResult(
                fragment = this,
                permissions = permissions,
                permissionTitleRes = R.string.storage_permission_required,
                onAllGranted = { galleryViewModel.loadMediaFiles(isSortDescending) }
            )
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGalleryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.root.applySystemBarsPadding(true, true)
        
        setupRecyclerView()
        setupButtons()
        setupObservers()
        checkPermissionsAndLoad()
    }
    
    private fun setupObservers() {
        galleryViewModel.mediaList.observe(viewLifecycleOwner) { list ->
            adapter.updateList(list)
            updateUI(list.size)
        }
        
        galleryViewModel.error.observe(viewLifecycleOwner) { errorMessage ->
            errorMessage?.let {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.error_loading_media, it),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    private fun setupButtons() {
        binding.backButton.setOnClickListener {
            findNavController().navigateUp()
        }
        
        binding.sortButton.setOnClickListener {
            toggleSortOrder()
        }
    }
    
    private fun setupRecyclerView() {
        adapter = MediaAdapter(
            items = mutableListOf<MediaModel>(),
            onItemClick = { openMediaViewer(it) },
            onItemLongClick = { item ->
                showDeleteDialog {
                    deleteMedia(item)
                }
            }
        )

        binding.galleryRecyclerView.apply {
            layoutManager = GridLayoutManager(requireContext(), 3)
            adapter = this@GalleryFragment.adapter
        }
    }

    private fun checkPermissionsAndLoad() {
        when {
            PermissionHelper.hasStoragePermission(requireContext()) -> {
                galleryViewModel.loadMediaFiles(isSortDescending)
            }
            else -> {
                val permissions = PermissionHelper.getStoragePermissions()
                val firstPermission = permissions.firstOrNull()
                if (firstPermission != null && shouldShowRationale(firstPermission)) {
                    showStoragePermissionRationale()
        } else {
        storagePermissionLauncher.launch(permissions)
    }
            }
        }
    }

    private fun showStoragePermissionRationale() {
        PermissionHelper.showRationaleDialog(
            fragment = this,
            titleRes = R.string.storage_permission_required,
            onConfirm = {
                storagePermissionLauncher.launch(PermissionHelper.getStoragePermissions())
            }
        )
    }

    
    private fun toggleSortOrder() {
        isSortDescending = !isSortDescending
        galleryViewModel.loadMediaFiles(isSortDescending)
    }

    private fun updateUI(count: Int) {
        binding.emptyTextView.visibility = if (count == 0) View.VISIBLE else View.GONE
        binding.countTextView.text =
            FormatUtils.formatItemsCount(count, requireContext())
        binding.countTextView.visibility = if (count > 0) View.VISIBLE else View.GONE
    }

    private fun openMediaViewer(item: MediaModel) {
        val list = galleryViewModel.mediaList.value.orEmpty()
        val position = list.indexOfFirst { it.id == item.id }.coerceAtLeast(0)

        galleryViewModel.setCurrentPosition(position)

        val action =
            GalleryFragmentDirections.actionGalleryFragmentToMediaViewerFragment(
                mediaId = item.id,
                initialPosition = position
            )
        findNavController().navigate(action)
    }

    private fun deleteMedia(item: MediaModel) {
        val (success, isRecoverableSecurityException) = galleryViewModel.deleteMedia(item.uri)
        if (success) {
            Toast.makeText(
                requireContext(),
                getString(R.string.file_deleted),
                Toast.LENGTH_SHORT
            ).show()
            galleryViewModel.loadMediaFiles(isSortDescending)
        } else {
            val message = if (isRecoverableSecurityException) {
                getString(R.string.file_cannot_be_deleted)
            } else {
                getString(R.string.error_deleting_file)
            }
            Toast.makeText(
                requireContext(),
                message,
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}