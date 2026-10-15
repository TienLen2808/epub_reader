package com.example.epub_reader.ui

import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.epub_reader.R
import com.example.epub_reader.databinding.DialogTtsLanguageBinding
import com.example.epub_reader.databinding.ItemTtsLanguageBinding
import com.example.epub_reader.databinding.ItemTtsLanguageFooterBinding
import com.example.epub_reader.databinding.ItemTtsLanguageHeaderBinding
import com.example.epub_reader.utils.TtsLanguage
import com.example.epub_reader.utils.TtsLanguages
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlin.math.roundToInt

/**
 * "Ngôn ngữ đọc" (iOS sheet), shown before the book is read aloud.
 * The language detected from the book is checked by default; the choice is returned with
 * the Fragment Result API ([REQUEST_KEY] / [RESULT_CODE]).
 */
class TtsLanguageBottomSheet : BottomSheetDialogFragment() {

    private var _binding: DialogTtsLanguageBinding? = null
    private val binding get() = _binding!!

    private var selectedCode = ""
    private var voiceStatuses: Map<String, Int> = emptyMap()
    private var rows: List<Row> = emptyList()
    private val adapter = LanguageAdapter()

    private enum class GroupPosition { SINGLE, TOP, MIDDLE, BOTTOM }

    private sealed class Row {
        class Header(val title: String) : Row()
        class Language(val language: TtsLanguage, val badgeRes: Int?, val position: GroupPosition) : Row()
        object SettingsLink : Row()
    }

    override fun getTheme(): Int = R.style.ThemeOverlay_Epub_reader_IosSheet

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogTtsLanguageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val args = arguments ?: Bundle()

        selectedCode = savedInstanceState?.getString(STATE_SELECTED)
            ?: args.getString(ARG_SELECTED).orEmpty()
        if (TtsLanguages.find(selectedCode) == null) selectedCode = TtsLanguages.defaultCode()

        val codes = args.getStringArrayList(ARG_STATUS_CODES).orEmpty()
        val values = args.getIntArray(ARG_STATUS_VALUES) ?: IntArray(0)
        voiceStatuses = codes.indices
            .filter { it < values.size }
            .associate { codes[it] to values[it] }

        rows = buildRows(args.getString(ARG_DETECTED), args.getString(ARG_PREVIOUS))

        val layoutManager = LinearLayoutManager(requireContext())
        binding.rvTtsLanguages.layoutManager = layoutManager
        binding.rvTtsLanguages.adapter = adapter
        binding.btnTtsCancel.setOnClickListener { dismiss() }
        binding.btnTtsStart.setOnClickListener { deliver() }

        // The checked language is visible when the sheet opens
        val selectedIndex = rows.indexOfFirst { it is Row.Language && it.language.code == selectedCode }
        if (selectedIndex > 6) {
            layoutManager.scrollToPositionWithOffset(selectedIndex, (120 * resources.displayMetrics.density).roundToInt())
        }
    }

    override fun onStart() {
        super.onStart()
        val sheetDialog = dialog as? BottomSheetDialog ?: return
        val sheet = sheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) ?: return
        val targetHeight = (resources.displayMetrics.heightPixels * SHEET_HEIGHT_RATIO).roundToInt()
        val params = sheet.layoutParams
        if (params != null && params.height != targetHeight) {
            params.height = targetHeight
            sheet.layoutParams = params
        }
        val behavior = BottomSheetBehavior.from(sheet)
        behavior.skipCollapsed = true
        behavior.peekHeight = targetHeight
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_SELECTED, selectedCode)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun deliver() {
        if (TtsLanguages.find(selectedCode) == null) return
        parentFragmentManager.setFragmentResult(REQUEST_KEY, bundleOf(RESULT_CODE to selectedCode))
        dismiss()
    }

    private fun select(code: String) {
        if (code == selectedCode) return
        val previous = selectedCode
        selectedCode = code
        rows.forEachIndexed { index, row ->
            if (row is Row.Language && (row.language.code == previous || row.language.code == code)) {
                adapter.notifyItemChanged(index)
            }
        }
    }

    /** "Đề xuất cho sách này" (detected / chosen last time) then all the other languages */
    private fun buildRows(detectedCode: String?, previousCode: String?): List<Row> {
        val result = mutableListOf<Row>()

        val suggested = mutableListOf<Pair<TtsLanguage, Int>>()
        TtsLanguages.find(detectedCode)?.let { language ->
            suggested += language to R.string.tts_badge_detected
        }
        TtsLanguages.find(previousCode)?.let { language ->
            if (suggested.none { it.first.code == language.code }) {
                suggested += language to R.string.tts_badge_previous
            }
        }
        if (suggested.isNotEmpty()) {
            result += Row.Header(getString(R.string.tts_section_suggested))
            suggested.forEachIndexed { index, (language, badgeRes) ->
                result += Row.Language(language, badgeRes, positionOf(index, suggested.size))
            }
        }

        val others = TtsLanguages.all.filter { language -> suggested.none { it.first.code == language.code } }
        result += Row.Header(
            getString(if (suggested.isEmpty()) R.string.tts_section_languages else R.string.tts_section_other)
        )
        others.forEachIndexed { index, language ->
            result += Row.Language(language, null, positionOf(index, others.size))
        }

        result += Row.SettingsLink
        return result
    }

    private fun positionOf(index: Int, count: Int): GroupPosition = when {
        count == 1 -> GroupPosition.SINGLE
        index == 0 -> GroupPosition.TOP
        index == count - 1 -> GroupPosition.BOTTOM
        else -> GroupPosition.MIDDLE
    }

    private fun bindLanguage(holder: LanguageHolder, row: Row.Language) {
        val b = holder.binding
        val language = row.language
        val ctx = b.root.context
        val status = voiceStatuses[language.code] ?: TtsLanguages.VOICE_UNKNOWN

        b.tvTtsLangName.text = language.nativeName
        b.tvTtsLangName.alpha = if (status == TtsLanguages.VOICE_NOT_SUPPORTED) 0.45f else 1f

        val badgeRes = row.badgeRes
        b.tvTtsLangBadge.isVisible = badgeRes != null
        if (badgeRes != null) b.tvTtsLangBadge.setText(badgeRes)

        // Second line: name in Vietnamese + state of the voice on this device
        val subtitle = SpannableStringBuilder()
        val localizedName = getString(language.nameRes)
        if (!localizedName.equals(language.nativeName, ignoreCase = true)) subtitle.append(localizedName)
        val statusText = when (status) {
            TtsLanguages.VOICE_NEEDS_DOWNLOAD -> getString(R.string.tts_status_needs_download)
            TtsLanguages.VOICE_NOT_SUPPORTED -> getString(R.string.tts_status_not_supported)
            else -> null
        }
        if (statusText != null) {
            if (subtitle.isNotEmpty()) subtitle.append(" · ")
            val start = subtitle.length
            subtitle.append(statusText)
            val colorRes = if (status == TtsLanguages.VOICE_NEEDS_DOWNLOAD) R.color.ios_orange else R.color.ios_placeholder
            subtitle.setSpan(
                ForegroundColorSpan(ContextCompat.getColor(ctx, colorRes)),
                start,
                subtitle.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        b.tvTtsLangSub.text = subtitle
        b.tvTtsLangSub.isVisible = subtitle.isNotEmpty()

        b.imgTtsLangCheck.visibility = if (language.code == selectedCode) View.VISIBLE else View.INVISIBLE

        b.root.setBackgroundResource(
            when (row.position) {
                GroupPosition.SINGLE -> R.drawable.bg_ios_group_single
                GroupPosition.TOP -> R.drawable.bg_ios_group_top
                GroupPosition.MIDDLE -> R.drawable.bg_ios_group_middle
                GroupPosition.BOTTOM -> R.drawable.bg_ios_group_bottom
            }
        )
        b.viewTtsLangSeparator.isVisible =
            row.position == GroupPosition.TOP || row.position == GroupPosition.MIDDLE

        b.root.setOnClickListener {
            val position = holder.adapterPosition
            if (position == RecyclerView.NO_POSITION) return@setOnClickListener
            val clicked = rows.getOrNull(position) as? Row.Language ?: return@setOnClickListener
            select(clicked.language.code)
        }
    }

    private class HeaderHolder(val binding: ItemTtsLanguageHeaderBinding) : RecyclerView.ViewHolder(binding.root)

    private class LanguageHolder(val binding: ItemTtsLanguageBinding) : RecyclerView.ViewHolder(binding.root)

    private class SettingsHolder(val binding: ItemTtsLanguageFooterBinding) : RecyclerView.ViewHolder(binding.root)

    private inner class LanguageAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun getItemCount(): Int = rows.size

        override fun getItemViewType(position: Int): Int = when (rows[position]) {
            is Row.Header -> TYPE_HEADER
            is Row.Language -> TYPE_LANGUAGE
            is Row.SettingsLink -> TYPE_SETTINGS
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return when (viewType) {
                TYPE_HEADER -> HeaderHolder(ItemTtsLanguageHeaderBinding.inflate(inflater, parent, false))
                TYPE_SETTINGS -> SettingsHolder(ItemTtsLanguageFooterBinding.inflate(inflater, parent, false))
                else -> LanguageHolder(ItemTtsLanguageBinding.inflate(inflater, parent, false))
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val row = rows[position]
            if (row is Row.Header && holder is HeaderHolder) {
                holder.binding.tvTtsHeader.text = row.title
            } else if (row is Row.Language && holder is LanguageHolder) {
                bindLanguage(holder, row)
            } else if (holder is SettingsHolder) {
                holder.binding.btnTtsSettings.setOnClickListener {
                    context?.let { TtsLanguages.openTtsSettings(it) }
                }
            }
        }
    }

    companion object {
        const val TAG = "TtsLanguageBottomSheet"
        const val REQUEST_KEY = "tts_language_request"
        const val RESULT_CODE = "tts_language_code"

        private const val ARG_SELECTED = "arg_selected"
        private const val ARG_DETECTED = "arg_detected"
        private const val ARG_PREVIOUS = "arg_previous"
        private const val ARG_STATUS_CODES = "arg_status_codes"
        private const val ARG_STATUS_VALUES = "arg_status_values"
        private const val STATE_SELECTED = "state_selected"

        private const val TYPE_HEADER = 0
        private const val TYPE_LANGUAGE = 1
        private const val TYPE_SETTINGS = 2
        private const val SHEET_HEIGHT_RATIO = 0.88f

        /**
         * @param selected language checked when the sheet opens
         * @param detected language detected from the book ("Tự nhận diện")
         * @param previous language chosen last time for this book
         * @param voiceStatuses [TtsLanguages.VOICE_READY]... for each language code (empty when unknown)
         */
        fun newInstance(
            selected: String,
            detected: String?,
            previous: String?,
            voiceStatuses: Map<String, Int>
        ): TtsLanguageBottomSheet {
            val codes = ArrayList(voiceStatuses.keys)
            val values = IntArray(codes.size) { index -> voiceStatuses[codes[index]] ?: TtsLanguages.VOICE_UNKNOWN }
            val args = Bundle().apply {
                putString(ARG_SELECTED, selected)
                putString(ARG_DETECTED, detected)
                putString(ARG_PREVIOUS, previous)
                putStringArrayList(ARG_STATUS_CODES, codes)
                putIntArray(ARG_STATUS_VALUES, values)
            }
            return TtsLanguageBottomSheet().apply { arguments = args }
        }
    }
}
