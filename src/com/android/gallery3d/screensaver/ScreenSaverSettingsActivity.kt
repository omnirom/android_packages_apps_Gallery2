package com.android.gallery3d.screensaver;

import android.os.Bundle
import android.view.View
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity

import com.android.gallery3d.R;

class ScreenSaverSettingsActivity : AppCompatActivity() {    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        initializeActionBar()
        setContentView(R.layout.screensaver_settings);
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, ScreenSaverSettingsFragment()).commit()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        when (id) {
            android.R.id.home -> {
                finish()
                return true
            }
        }
        return false
    }

    private fun initializeActionBar() {
        val actionBar: ActionBar? = supportActionBar
        actionBar?.setDisplayOptions(ActionBar.DISPLAY_HOME_AS_UP or ActionBar.DISPLAY_SHOW_TITLE)
        actionBar?.setTitle(resources.getString(R.string.screensaver_settings_label))
    }
}