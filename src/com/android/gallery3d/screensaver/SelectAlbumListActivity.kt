package com.android.gallery3d.screensaver;

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.ActionBar

import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager

import com.android.gallery3d.R
import com.android.gallery3d.app.AlbumPicker
import com.android.gallery3d.util.Log

import java.util.Collections

class SelectAlbumListActivity : AppCompatActivity() {
    private val TAG = "SelectAlbumListActivity"
    private val REQUEST_CHOOSE_ALBUM = 1

    private val mAlbumList = ArrayList<String>()
    private lateinit var mListView: ListView
    private lateinit var mListViewAdapter: AlbumNameListAdapter


    class AlbumNameListAdapter(context: Context, albumList: List<String>) :
        ArrayAdapter<String?>(context, 0, albumList) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            var convertView = convertView
            val albumItem= getItem(position)
            val albumName = albumItem!!.split(":")[0]

            // Check if an existing view is being reused, otherwise inflate the view
            if (convertView == null) {
                convertView =
                    LayoutInflater.from(context).inflate(R.layout.album_list_item, parent, false)
            }

            val albumNameText = convertView!!.findViewById<View>(R.id.album_list_name) as TextView
            albumNameText.text = albumName

            val delete = convertView.findViewById<View>(R.id.album_list_remove) as ImageView
            delete.setOnClickListener {
                remove(albumItem)
                notifyDataSetChanged()
            }

            return convertView
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initializeActionBar()
        setContentView(R.layout.select_album_list_activity)

        val config = ScreenSaverConfig.getFromPreferences(this)
        mAlbumList.addAll(config.albumList)
        Log.d(TAG, "albumList = " + mAlbumList)
        Collections.sort(mAlbumList)

        mListView = findViewById<ListView>(R.id.select_list)
        mListViewAdapter = AlbumNameListAdapter(this, mAlbumList)
        mListView.adapter = mListViewAdapter

        findViewById<View>(R.id.add_button).setOnClickListener { _: View? ->
            pickAlbum()
        }

        findViewById<View>(R.id.ok_button).setOnClickListener { _: View? ->
            val prefs = PreferenceManager.getDefaultSharedPreferences(this)
            val albumList = HashSet<String>(prefs.getStringSet(ScreenSaverConfig.KEY_ALBUMS_LIST_SLIDESHOW, HashSet<String>()))
            albumList.clear()
            albumList.addAll(mAlbumList)
            prefs.edit().putStringSet(ScreenSaverConfig.KEY_ALBUMS_LIST_SLIDESHOW, albumList).apply()
            setResult(Activity.RESULT_OK)
            finish()
        }

        mListViewAdapter.notifyDataSetChanged()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        super.onCreateOptionsMenu(menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        when (id) {
            android.R.id.home -> {
                setResult(Activity.RESULT_CANCELED)
                finish()
                return true
            }
        }
        return false
    }

    private fun initializeActionBar() {
        val actionBar: ActionBar? = supportActionBar
        actionBar?.setDisplayOptions(ActionBar.DISPLAY_HOME_AS_UP or ActionBar.DISPLAY_SHOW_TITLE)
        actionBar?.setTitle(resources.getString(R.string.slideshow_pick_album_title))
    }

    private fun pickAlbum() {
        val requestAlbum = Intent(this, AlbumPicker::class.java)
        this.startActivityForResult(requestAlbum, REQUEST_CHOOSE_ALBUM)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode == REQUEST_CHOOSE_ALBUM) {
            if (resultCode == Activity.RESULT_OK) {
                data?.let { data ->
                    if (data.hasExtra(AlbumPicker.KEY_ALBUM_PATH) && data.hasExtra(AlbumPicker.KEY_ALBUM_NAME)) {
                        val albumPath = data.getStringExtra(AlbumPicker.KEY_ALBUM_PATH)!!
                        val albumName = data.getStringExtra(AlbumPicker.KEY_ALBUM_NAME)!!
                        val albumItem = albumName + ":" + albumPath
                        if (!mAlbumList.contains(albumItem)) {
                            mAlbumList.add(albumItem)
                            Collections.sort(mAlbumList)
                            mListViewAdapter.notifyDataSetChanged()
                        }
                    }
                }
            }
        }
    }
}