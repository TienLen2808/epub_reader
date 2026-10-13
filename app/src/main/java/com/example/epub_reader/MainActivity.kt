package com.example.epub_reader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.marginBottom
import androidx.lifecycle.lifecycleScope
import com.example.epub_reader.AdsManager.AdsManager
import com.example.epub_reader.databinding.ActivityMainBinding
import com.example.epub_reader.epub.BookHolder
import com.example.epub_reader.epub.EpubBook
import com.example.epub_reader.epub.EpubParser
import com.example.epub_reader.utils.AdPosition
import com.example.epub_reader.utils.DisabledAdType
import com.example.epub_reader.utils.PreferencesHelper
import com.example.epub_reader.utils.dpToPx
import com.example.epub_reader.utils.removeSubviews
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : LocalizeBaseActivity() {
    private lateinit var binding: ActivityMainBinding

    private val openDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            parseEpubFile(uri)
        } else {
            Toast.makeText(this, "No file selected", Toast.LENGTH_SHORT).show()
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            launchEpubPicker()
        } else {
            Toast.makeText(
                this,
                "Storage permission is required to import EPUB files",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupMainLayout(binding.main)

        binding.btnSettings.setOnClickListener {
            Toast.makeText(this, "Settings", Toast.LENGTH_SHORT).show()
        }

        binding.btnImportEpub.setOnClickListener {
            checkPermissionAndImport()
        }

        val lastUriStr = PreferencesHelper.getInstance().getString("last_opened_epub_uri")
        if (!lastUriStr.isNullOrEmpty()) {
            try {
                val uri = Uri.parse(lastUriStr)
                parseEpubFile(uri)
            } catch (e: Exception) {
                Log.e("MainActivity", "Error re-opening last URI", e)
            }
        }
    }

    private fun checkPermissionAndImport() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            launchEpubPicker()
        } else {
            val permission = Manifest.permission.READ_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
                launchEpubPicker()
            } else {
                requestPermissionLauncher.launch(permission)
            }
        }
    }

    private fun launchEpubPicker() {
        try {
            openDocumentLauncher.launch(
                arrayOf(
                    "application/epub+zip",
                    "application/octet-stream",
                    "*/*"
                )
            )
        } catch (e: Exception) {
            Log.e("MainActivity", "Error launching file picker", e)
            Toast.makeText(this, "Cannot launch file picker", Toast.LENGTH_SHORT).show()
        }
    }

    private fun parseEpubFile(uri: Uri) {
        binding.tvStatus.text = "Reading and parsing EPUB file..."
        binding.cardBookInfo.visibility = View.GONE

        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {}

        PreferencesHelper.getInstance().putString("last_opened_epub_uri", uri.toString())

        lifecycleScope.launch(Dispatchers.IO) {
            val epubBook = EpubParser.parse(this@MainActivity, uri)
            withContext(Dispatchers.Main) {
                if (epubBook != null) {
                    displayBookInfo(epubBook)
                } else {
                    binding.tvStatus.text = "Failed to parse EPUB file. Please try another file."
                    Toast.makeText(
                        this@MainActivity,
                        "Error: Could not read EPUB file",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun displayBookInfo(book: EpubBook) {
        binding.tvStatus.text = "EPUB file loaded successfully!"
        binding.cardBookInfo.visibility = View.VISIBLE

        binding.tvTitle.text = book.title
        binding.tvAuthor.text = "Author: ${book.author}"
        binding.tvChaptersCount.text = "Total Chapters: ${book.chapters.size}"

        if (book.coverBitmap != null) {
            binding.imgCover.setImageBitmap(book.coverBitmap)
        } else {
            binding.imgCover.setImageResource(android.R.drawable.ic_menu_gallery)
        }

        val openReaderAction = View.OnClickListener {
            BookHolder.currentBook = book
            val intent = Intent(this, ReaderActivity::class.java)
            startActivity(intent)
        }

        binding.cardBookInfo.setOnClickListener(openReaderAction)
        binding.btnReadNow.setOnClickListener(openReaderAction)

        Toast.makeText(
            this,
            "Loaded: ${book.title} (${book.chapters.size} chapters)",
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun loadAd() {
        super.loadAd()
        var showBanner = false
        var showMrec = false

        binding.anchorView.post {
            val canShowMrec = canShowMrec(DisabledAdType.homeMrec)
            val canShowBanner = canShowBanner(DisabledAdType.homeBanner)

            val anchorHeight = binding.anchorView.measuredHeight + binding.mainContent.marginBottom
            if (anchorHeight < dpToPx(250)) {
                if (canShowBanner) {
                    showBanner = true
                }
            } else if (anchorHeight < dpToPx(320)) {
                if (canShowMrec) {
                    showMrec = true
                } else if (canShowBanner) {
                    showBanner = true
                }
            } else {
                if (canShowBanner) {
                    showBanner = true
                    (binding.mainContent.layoutParams as MarginLayoutParams).bottomMargin = dpToPx(70)
                    binding.mainContent.requestLayout()
                }
                if (canShowMrec) {
                    showMrec = true
                }
            }

            if (showBanner) {
                AdsManager.getInstance(this).loadApplovinInlineAdaptiveBannerWithTricky(
                    this, DisabledAdType.homeBanner,
                    AdPosition.homeScreen, binding.bannerContainer, AdsManager.RETRY_COUNT, {}
                )
            } else {
                binding.bannerContainer.removeSubviews()
            }

            if (showMrec) {
                AdsManager.getInstance(this).loadApplovinMrecWithTricky(
                    this, DisabledAdType.homeMrec,
                    AdPosition.homeScreen, AdsManager.RETRY_COUNT, binding.mrecContainer, {}
                )
            } else {
                binding.mrecContainer.removeSubviews()
            }
        }
    }
}
