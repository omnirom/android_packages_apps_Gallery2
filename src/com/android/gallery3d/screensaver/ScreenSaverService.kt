package com.android.gallery3d.screensaver;

import android.content.Intent
import android.content.IntentFilter
import android.graphics.Point
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.service.dreams.DreamService
import android.view.LayoutInflater
import android.view.View
import android.widget.Space
import android.widget.Toast

import com.android.gallery3d.R
import com.android.gallery3d.app.AlbumDataLoader
import com.android.gallery3d.app.AlbumSetDataLoader
import com.android.gallery3d.app.FilterUtils
import com.android.gallery3d.app.GalleryApp
import com.android.gallery3d.app.LoadingListener
import com.android.gallery3d.app.SlideshowDataAdapter
import com.android.gallery3d.app.SlideshowPage
import com.android.gallery3d.app.SlideshowSources.ShuffleSource
import com.android.gallery3d.app.SlideshowSources.SequentialSourceRecursive
import com.android.gallery3d.common.Utils
import com.android.gallery3d.data.ContentListener
import com.android.gallery3d.data.DataManager
import com.android.gallery3d.data.LocalAlbum
import com.android.gallery3d.data.LocalAlbumSet
import com.android.gallery3d.data.ComboAlbum
import com.android.gallery3d.data.MediaItem
import com.android.gallery3d.data.MediaObject
import com.android.gallery3d.data.MediaSet
import com.android.gallery3d.data.Path
import com.android.gallery3d.ui.SlideshowImageView
import com.android.gallery3d.util.Future
import com.android.gallery3d.util.FutureListener
import com.android.gallery3d.util.GalleryUtils
import com.android.gallery3d.util.Log
import com.android.gallery3d.util.MediaSetUtils

import java.lang.Thread

class ScreenSaverService : DreamService() {
    private val TAG = "ScreenSaverService"
    private val DEBUG = false

    val KEY_SET_PATH = "media-set-path"
    val KEY_ITEM_PATH = "media-item-path"

    private val MSG_LOAD_NEXT_BITMAP = 1
    private val MSG_SHOW_PENDING_BITMAP = 2
    private val DATA_CACHE_SIZE = 256

    private lateinit var mHandler: Handler
    private var mModel:SlideshowPage.Model? = null
    private lateinit var mSlideshowImageView:SlideshowImageView
    private lateinit var mDataManager:DataManager
    private var mAlbumSetDataLoader: AlbumSetDataLoader? = null
    private var mPendingSlide:SlideshowPage.Slide? = null
    private lateinit var mConfig: ScreenSaverConfig
    private var mMediaSet: MediaSet? = null
    

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        mSlideshowImageView = inflater.inflate(R.layout.slideshow_view, null, false) as SlideshowImageView

        setContentView(mSlideshowImageView)

        mHandler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(message: Message) {
                when (message.what) {
                    MSG_SHOW_PENDING_BITMAP -> showPendingBitmap()
                    MSG_LOAD_NEXT_BITMAP -> loadNextBitmap()
                    else -> throw AssertionError()
                }
            }
        }

        val app:GalleryApp = this.getApplicationContext() as GalleryApp
        mDataManager = app.getDataManager()
        
        // Exit dream upon user touchimport android.os.HandlerThread;

        isInteractive = false
        // Hide system UI
        isFullscreen = true
        isScreenBright = false        
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        if (DEBUG) Log.d(TAG, "onDreamingStarted")
        mConfig = ScreenSaverConfig.getFromPreferences(this)
        initializeData(mConfig)
        mSlideshowImageView.showNoImagesMessage(false)

        if (mMediaSet != null) {
            val loadingListener = object : LoadingListener {
                override fun onLoadingStarted() {
                    if (DEBUG) Log.d(TAG, "onLoadingStarted")
                    mSlideshowImageView.showProgress(true)
                }
                override fun onLoadingFinished() {
                    if (DEBUG) Log.d(TAG, "onLoadingFinished")
                    mSlideshowImageView.showProgress(false)
                    if (mMediaSet!!.getTotalMediaItemCount() != 0) {
                        createSlideshow(mConfig)
                        resumeSlideShow()
                    } else {
                        mSlideshowImageView.showNoImagesMessage(true)
                    }
                }
            }
            mAlbumSetDataLoader?.setLoadingListener(loadingListener)
            mAlbumSetDataLoader?.resume()
        } else {
            mSlideshowImageView.showNoImagesMessage(true)
        }
    }

    override fun onDreamingStopped() {
        super.onDreamingStopped()

        if (window != null) {
            // when preview is running and real dream kicks in
            // detach is called afterwards which leads all broadcast receivers dangling
            // so do it in dream start/stop
            setContentView(Space(this))
        }
        mAlbumSetDataLoader?.pause()
        pauseSlideShow()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
    }

    private fun getDuration(): Int {
        return mConfig.duration
    }
    
    private fun loadNextBitmap() {
        mModel?.nextSlide(object : FutureListener<SlideshowPage.Slide?> {
            override fun onFutureDone(future: Future<SlideshowPage.Slide?>) {
                mPendingSlide = future.get()
                mHandler.sendEmptyMessage(MSG_SHOW_PENDING_BITMAP)
            }
        })
    }

    private fun showPendingBitmap() {
        val slide: SlideshowPage.Slide? = mPendingSlide
        if (slide != null) {
            mSlideshowImageView.next(slide.bitmap)
            mHandler.sendEmptyMessageDelayed(MSG_LOAD_NEXT_BITMAP, getDuration().toLong())
        }
    }

    private fun pauseSlideShow() {
        mModel?.pause()
        mHandler.removeMessages(MSG_LOAD_NEXT_BITMAP)
        mHandler.removeMessages(MSG_SHOW_PENDING_BITMAP)
    }

    private fun resumeSlideShow() {
        if (DEBUG) Log.d(TAG, "resumeSlideShow")
        mModel?.resume()
        mSlideshowImageView.restart()
        if (mPendingSlide != null) {
            showPendingBitmap()
        } else {
            loadNextBitmap()
        }
    }
    
    private fun createSlideshow(config: ScreenSaverConfig) {
        val random: Boolean = config.random
        val repeat: Boolean = true
        mSlideshowImageView.setFillScreen(config.fillScreen)
        if (DEBUG) Log.d(TAG, "createSlideshow")

        if (random) {
            mModel = SlideshowDataAdapter(
                this,
                ShuffleSource(mMediaSet, repeat), 0, null
            )
        } else {
            val path: Path? = null
            mModel = SlideshowDataAdapter(
                this, SequentialSourceRecursive(mMediaSet, repeat),
                0, path
            )
        }
    }

    private fun initializeData(config: ScreenSaverConfig) {

        if (config.allAlbums) {
            val topPath = mDataManager.getTopSetPath(DataManager.INCLUDE_ALL)
            var mediaPath = FilterUtils.newFilterPath(topPath, FilterUtils.FILTER_IMAGE_ONLY);
            mMediaSet = mDataManager.getMediaSet(mediaPath)
            mMediaSet?.let { mediaSet ->
                mAlbumSetDataLoader = AlbumSetDataLoader(mediaSet, DATA_CACHE_SIZE)
                mAlbumSetDataLoader!!.setDummyMainHandler()
                if (DEBUG) Log.d(TAG, "initializeData allAlbums mediaPath = " + mediaPath)
            }
        } else {
            val albumList = config.albumList
            if (DEBUG) Log.d(TAG, "initializeData albumList = " + albumList);
            if (albumList.isNotEmpty()) {
                val mediaSets = ArrayList<MediaSet>()

                for (albumEntry in albumList) {
                    val albumPath = albumEntry.split(":")[1]
                    var mediaSet: MediaSet = mDataManager.getMediaObject(albumPath) as MediaSet
                    if (mediaSet is LocalAlbum) {                    
                        var mediaPath = mediaSet.getPath().toString()
                        mediaPath = FilterUtils.newFilterPath(mediaPath, FilterUtils.FILTER_IMAGE_ONLY);
                        mediaSet = mDataManager.getMediaSet(mediaPath)
                        mediaSets.add(mediaSet)
                    }
                }
                var mediaSetsArray: Array<MediaSet> = mediaSets.toTypedArray()                
                mMediaSet = ComboAlbum(Path.fromString(DataManager.TOP_IMAGE_SET_PATH), mediaSetsArray, "Slideshow")
                mMediaSet?.let { mediaSet ->
                    mAlbumSetDataLoader = AlbumSetDataLoader(mediaSet, DATA_CACHE_SIZE)
                    mAlbumSetDataLoader!!.setDummyMainHandler()
                }
            }
        }
    }
}