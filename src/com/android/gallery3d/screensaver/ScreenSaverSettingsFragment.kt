/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.gallery3d.screensaver

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager

import com.android.gallery3d.R
import com.android.gallery3d.app.AlbumPicker
import com.android.gallery3d.data.Path

class ScreenSaverSettingsFragment : PreferenceFragmentCompat(),        
    Preference.OnPreferenceChangeListener {
    private val TAG = "Gallery2:ScreenSaverSettingsFragment"
    private val REQUEST_CHOOSE_ALBUM = 1

    private var mPickAlbum: Preference? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.screen_saver_settings, rootKey)
        
        mPickAlbum = findPreference("screensaver_pick_album")
        updatePickAlbumSummary()
        mPickAlbum?.setOnPreferenceClickListener {
            pickAlbum()
            true
        }
    }

    override fun onPreferenceChange(pref: Preference, newValue: Any): Boolean {
        return true
    }
        
    private fun pickAlbum() {
        val requestAlbum = Intent(requireContext(), SelectAlbumListActivity::class.java)
        this.startActivityForResult(requestAlbum, REQUEST_CHOOSE_ALBUM)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode == REQUEST_CHOOSE_ALBUM) {  
            if (resultCode == Activity.RESULT_OK) {
                updatePickAlbumSummary()
            }
        }  
    }

    private fun updatePickAlbumSummary() {
        val config = ScreenSaverConfig.getFromPreferences(requireContext())
        val format = getResources().getQuantityString(R.plurals.album_list_selection, config.albumList.size)
        mPickAlbum?.setSummary(String.format(format, config.albumList.size)) 
    }
}
