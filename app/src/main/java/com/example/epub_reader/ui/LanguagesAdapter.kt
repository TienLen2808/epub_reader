package com.example.epub_reader.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.epub_reader.R
import com.example.epub_reader.utils.LocalizeHelper
import java.util.Locale

class LanguagesAdapter(private var currentLanguage: String, private val onItemClick: (Locale?) -> Unit ) :
    RecyclerView.Adapter<LanguagesAdapter.FileViewHolder>() {

    class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val flag: ImageView = itemView.findViewById(R.id.flag)
        val textViewName: TextView = itemView.findViewById(R.id.textViewName)
        val textViewChecked: ImageView = itemView.findViewById(R.id.textViewChecked)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_llanguage, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val locale = LocalizeHelper.languageAtIndex(position)
        try {
            Glide.with(holder.flag)
                .load(getFlagResource(locale))
                .fitCenter()
                .into(holder.flag)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        holder.textViewName.text = nativeName(locale)
        val selected = currentLanguage == locale.language
        holder.textViewChecked.setImageResource(
            if (selected) R.drawable.ic_check else R.drawable.unselect
        )
        holder.textViewChecked.visibility = View.VISIBLE

        holder.itemView.setOnClickListener {
            onItemClick(locale)
        }
    }

    override fun getItemCount(): Int = LocalizeHelper.numberOfLanguages()

    fun updateLanguage(lang: String) {
        currentLanguage = lang
        notifyDataSetChanged()
    }

    /** Each language is written in itself ("Deutsch", "Tiếng Việt", "日本語"...), whatever the app language */
    private fun nativeName(locale: Locale): String {
        val name = locale.getDisplayLanguage(locale).ifBlank { locale.displayLanguage }
        return name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    }

    private fun getFlagResource(locale: Locale): Int {
        return when (locale.language) {
            "en" -> R.drawable.language_en
            "de" -> R.drawable.language_de
            "es" -> R.drawable.language_es
            "fr" -> R.drawable.language_fr
            "it" -> R.drawable.language_it
            "pt" -> R.drawable.language_pt
            "ja" -> R.drawable.language_jp
            "ko" -> R.drawable.language_kr
            "zh" -> R.drawable.language_cn
            "ar" -> R.drawable.language_arab
            "hi" -> R.drawable.language_in
            "in" -> R.drawable.language_id
            "id" -> R.drawable.language_id
            "pl" -> R.drawable.language_pl
            "tl" -> R.drawable.language_ph
            "tr" -> R.drawable.language_tr
            else -> R.drawable.language_vn
        }
    }
}
