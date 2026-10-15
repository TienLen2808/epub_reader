package com.example.epub_reader.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.epub_reader.LocalizeBaseActivity
import com.example.epub_reader.databinding.ActivityLanguageBinding
import com.example.epub_reader.utils.Constants
import com.example.epub_reader.utils.LocalizeHelper
import com.example.epub_reader.utils.PreferencesHelper
import java.util.Locale

class LanguageActivity : LocalizeBaseActivity() {
    companion object {
        private const val EXTRA_LANGUAGE_SELECTED = "LANGUAGE_SELECTED"
    }

    private lateinit var binding: ActivityLanguageBinding
    private lateinit var adapter: LanguagesAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })
        enableEdgeToEdge()

        binding = ActivityLanguageBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupMainLayout(binding.root)

        val locales = AppCompatDelegate.getApplicationLocales()
        if (locales.size() > 0) {
            currentLanguage = locales[0] ?: Locale.getDefault()
        }
        else {
            currentLanguage = Locale.getDefault()
        }

        binding.apply {
            btnBack.setOnClickListener {
                finish()
            }

            recyclerView.layoutManager = LinearLayoutManager(this@LanguageActivity)
            val useDefault = PreferencesHelper.getInstance().getBoolean(Constants.SYSTEM_DEFAULT_LANGUAGE, true)
            val selectedLanguage = when {
                useDefault -> ""
                else -> currentLanguage.language
            }
            adapter = LanguagesAdapter(selectedLanguage){ locale ->
                if (locale != null) {
                    onItemClick(locale)
                }
                else {
                    resetToSystemLanguage()
                }
            }

            recyclerView.adapter = adapter
            val index = LocalizeHelper.indexOfLanguage(currentLanguage)
            if (index in 0 until LocalizeHelper.numberOfLanguages()) {
                recyclerView.scrollToPosition(index)
            }
        }
    }

    private fun onItemClick(locale: Locale) {
        intent.putExtra(EXTRA_LANGUAGE_SELECTED, true)

        PreferencesHelper.getInstance().putBoolean(Constants.SYSTEM_DEFAULT_LANGUAGE, false)
        currentLanguage = locale

        AppCompatDelegate.setApplicationLocales(LocaleListCompat.create(currentLanguage))
        Handler(Looper.getMainLooper()).postDelayed({
            adapter.updateLanguage(locale.language)
        }, 200)
    }

    private fun resetToSystemLanguage() {
        PreferencesHelper.getInstance().putBoolean(Constants.SYSTEM_DEFAULT_LANGUAGE, true)
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        Handler(Looper.getMainLooper()).postDelayed({
            adapter.updateLanguage("")
        }, 200)
    }
}
