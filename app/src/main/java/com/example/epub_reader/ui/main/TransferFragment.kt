package com.example.epub_reader.ui.main

import android.app.Activity
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.epub_reader.R
import com.example.epub_reader.databinding.FragmentTransferBinding
import com.example.epub_reader.utils.FileItemManager
import com.example.epub_reader.utils.FileShareHelper
import com.example.epub_reader.utils.LibraryImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Chuyển tệp" tab:
 *  - Files/iCloud Drive  -> system file picker (several files), copied into "Các tập tin"
 *  - Chuyển Wifi         -> [WifiTransferDialog] (upload from a computer browser)
 *  - Chia sẻ tệp iTunes  -> [UsbTransferDialog] (copy over USB, then import)
 *  - Dropbox / Drive     -> import several files from the app, or batch upload library files to it
 */
class TransferFragment : Fragment() {

    private var _binding: FragmentTransferBinding? = null
    private val binding get() = _binding!!

    private var activeDialog: Dialog? = null

    private enum class CloudService(
        val packageName: String,
        val nameRes: Int,
        val importRes: Int,
        val uploadRes: Int,
        val infoRes: Int
    ) {
        DROPBOX(
            FileShareHelper.DROPBOX_PACKAGE,
            R.string.dropbox,
            R.string.cloud_import_dropbox,
            R.string.cloud_upload_dropbox,
            R.string.cloud_info_dropbox
        ),
        GOOGLE_DRIVE(
            FileShareHelper.GOOGLE_DRIVE_PACKAGE,
            R.string.google_drive,
            R.string.cloud_import_drive,
            R.string.cloud_upload_drive,
            R.string.cloud_info_drive
        )
    }

    /** Android system picker (local storage, Downloads, Drive, Dropbox... in the side menu) */
    private val openDocumentsLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) importUris(uris)
    }

    /** Picker of the Dropbox / Google Drive app itself */
    private val cloudPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uris = urisFromResult(result.data)
            if (uris.isNotEmpty()) importUris(uris)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTransferBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnOptionIcloud.setOnClickListener { openSystemPicker() }
        binding.btnOptionWifi.setOnClickListener { showPage(WifiTransferDialog(), WifiTransferDialog.TAG) }
        binding.btnOptionItunes.setOnClickListener { showPage(UsbTransferDialog(), UsbTransferDialog.TAG) }
        binding.btnOptionDropbox.setOnClickListener { showCloudActions(CloudService.DROPBOX) }
        binding.btnOptionDrive.setOnClickListener { showCloudActions(CloudService.GOOGLE_DRIVE) }
        binding.btnInfoDropbox.setOnClickListener { showCloudInfo(CloudService.DROPBOX) }
        binding.btnInfoDrive.setOnClickListener { showCloudInfo(CloudService.GOOGLE_DRIVE) }

        childFragmentManager.setFragmentResultListener(
            LibraryPickerDialog.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, result ->
            val ids = result.getStringArrayList(LibraryPickerDialog.RESULT_IDS).orEmpty()
            val service = CloudService.values().firstOrNull { it.name == result.getString(LibraryPickerDialog.RESULT_TARGET) }
            if (service != null && ids.isNotEmpty()) uploadToCloud(service, ids)
        }
    }

    override fun onDestroyView() {
        activeDialog?.dismiss()
        activeDialog = null
        super.onDestroyView()
        _binding = null
    }

    // region Files / iCloud Drive

    private fun openSystemPicker() {
        try {
            openDocumentsLauncher.launch(arrayOf("*/*"))
        } catch (_: Exception) {
            toast(getString(R.string.error_launch_file_picker))
        }
    }

    private fun urisFromResult(data: Intent?): List<Uri> {
        val uris = mutableListOf<Uri>()
        val clip = data?.clipData
        if (clip != null) {
            for (i in 0 until clip.itemCount) {
                clip.getItemAt(i).uri?.let { uris += it }
            }
        }
        if (uris.isEmpty()) data?.data?.let { uris += it }
        return uris
    }

    /** Copies the picked files into the library ("Các tập tin") */
    private fun importUris(uris: List<Uri>) {
        val appContext = context?.applicationContext ?: return
        toast(getString(R.string.transfer_importing))
        viewLifecycleOwner.lifecycleScope.launch {
            val imported = withContext(Dispatchers.IO) { LibraryImporter.importUris(appContext, uris) }
            if (imported.isEmpty()) {
                toast(getString(R.string.transfer_import_failed))
            } else {
                toast(getString(R.string.transfer_imported_files, imported.size))
            }
        }
    }

    // endregion

    // region Wi-Fi / iTunes pages

    private fun showPage(page: DialogFragment, tag: String) {
        if (childFragmentManager.findFragmentByTag(tag) != null) return
        page.show(childFragmentManager, tag)
    }

    // endregion

    // region Dropbox / Google Drive

    private fun showCloudActions(service: CloudService) {
        val context = context ?: return
        activeDialog?.dismiss()
        activeDialog = IosAlertDialogs.showActions(
            context = context,
            title = getString(service.nameRes),
            message = getString(R.string.support_batch_upload),
            actions = listOf(
                IosAlertDialogs.Action(getString(service.importRes)) { importFromCloud(service) },
                IosAlertDialogs.Action(getString(service.uploadRes)) { pickFilesToUpload(service) }
            ),
            cancelText = getString(R.string.cancel)
        )
    }

    private fun showCloudInfo(service: CloudService) {
        val context = context ?: return
        activeDialog?.dismiss()
        activeDialog = IosAlertDialogs.showMessage(
            context = context,
            title = getString(R.string.support_batch_upload),
            message = getString(service.infoRes),
            buttonText = getString(R.string.ok)
        )
    }

    private fun importFromCloud(service: CloudService) {
        if (!isInstalled(service.packageName)) {
            showNotInstalled(service, offerSystemPicker = true)
            return
        }
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            setPackage(service.packageName)
        }
        try {
            cloudPickerLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            // The app has no picker of its own: use the system picker (Dropbox / Drive are in its menu)
            openSystemPicker()
        } catch (_: Exception) {
            openSystemPicker()
        }
    }

    private fun pickFilesToUpload(service: CloudService) {
        val hasFiles = FileItemManager.instance.getFiles().any { LibraryPickerDialog.isUploadable(it) }
        if (!hasFiles) {
            toast(getString(R.string.no_files_to_upload))
            return
        }
        if (!isInstalled(service.packageName)) {
            showNotInstalled(service, offerSystemPicker = false)
            return
        }
        // Bar: "Hủy" | Dropbox / Google Drive | "Tải lên"
        showPage(
            LibraryPickerDialog.newInstance(getString(service.nameRes), service.name),
            LibraryPickerDialog.TAG
        )
    }

    /** Batch upload: ACTION_SEND_MULTIPLE straight to the Dropbox / Google Drive app */
    private fun uploadToCloud(service: CloudService, ids: List<String>) {
        val ctx = context ?: return
        val items = FileItemManager.instance.getFiles().filter { it.id in ids }
        val intent = FileShareHelper.buildSendIntent(ctx, items, service.packageName)
        if (intent == null) {
            toast(getString(R.string.transfer_import_failed))
            return
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            showNotInstalled(service, offerSystemPicker = false)
        } catch (_: Exception) {
            toast(getString(R.string.transfer_import_failed))
        }
    }

    private fun showNotInstalled(service: CloudService, offerSystemPicker: Boolean) {
        val context = context ?: return
        val name = getString(service.nameRes)
        val actions = mutableListOf(
            IosAlertDialogs.Action(getString(R.string.install_app)) { openPlayStore(service.packageName) }
        )
        if (offerSystemPicker) {
            actions += IosAlertDialogs.Action(getString(R.string.choose_from_files)) { openSystemPicker() }
        }
        activeDialog?.dismiss()
        activeDialog = IosAlertDialogs.showActions(
            context = context,
            title = getString(R.string.app_not_installed_title, name),
            message = getString(R.string.app_not_installed_message, name),
            actions = actions,
            cancelText = getString(R.string.cancel)
        )
    }

    @Suppress("DEPRECATION")
    private fun isInstalled(packageName: String): Boolean {
        return try {
            requireContext().packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun openPlayStore(packageName: String) {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
        try {
            startActivity(market)
        } catch (_: ActivityNotFoundException) {
            try {
                startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))
                )
            } catch (_: Exception) {
            }
        }
    }

    // endregion

    private fun toast(message: String) {
        val ctx = context ?: return
        Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
    }
}
