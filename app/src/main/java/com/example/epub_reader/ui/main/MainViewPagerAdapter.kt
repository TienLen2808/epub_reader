package com.example.epub_reader.ui.main

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class MainViewPagerAdapter(fragmentActivity: FragmentActivity) : FragmentStateAdapter(fragmentActivity) {

    val filesFragment = FilesFragment()
    val transferFragment = TransferFragment()
    val settingsFragment = SettingsFragment()

    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> filesFragment
            1 -> transferFragment
            2 -> settingsFragment
            else -> filesFragment
        }
    }
}
