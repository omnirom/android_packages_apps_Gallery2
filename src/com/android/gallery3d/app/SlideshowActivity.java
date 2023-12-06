/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.gallery3d.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.view.WindowInsetsCompat;

import com.android.gallery3d.R;
import com.android.gallery3d.app.SlideshowSources.ShuffleSource;
import com.android.gallery3d.app.SlideshowSources.SequentialSourceRecursive;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.ContentListener;
import com.android.gallery3d.data.DataManager;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.MediaObject;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.ui.SlideshowImageView;
import com.android.gallery3d.util.Future;
import com.android.gallery3d.util.FutureListener;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.util.Log;

import java.util.ArrayList;
import java.util.Random;

public class SlideshowActivity extends AppCompatActivity {
    private static final String TAG = "SlideshowActivity";

    public static final String KEY_SET_PATH = "media-set-path";
    public static final String KEY_ITEM_PATH = "media-item-path";

    private static final int MSG_LOAD_NEXT_BITMAP = 1;
    private static final int MSG_SHOW_PENDING_BITMAP = 2;

    private Handler mHandler;
    private SlideshowPage.Model mModel;
    private SlideshowImageView mSlideshowImageView;
    private DataManager mDataManager;

    private SlideshowPage.Slide mPendingSlide = null;
    private boolean mIsActive = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.slideshow_view);
        mSlideshowImageView = findViewById(R.id.slideshow_view);
        initializeActionBar();

        GalleryApp app = (GalleryApp) this.getApplicationContext();
        mDataManager = app.getDataManager();
        
        mHandler = new Handler() {
            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case MSG_SHOW_PENDING_BITMAP:
                        showPendingBitmap();
                        break;
                    case MSG_LOAD_NEXT_BITMAP:
                        loadNextBitmap();
                        break;
                    default: throw new AssertionError();
                }
            }
        };
        Intent intent = getIntent();
        if (!initializeData(intent.getExtras())) {
            mSlideshowImageView.showNoImagesMessage(true);
        } else {
            mSlideshowImageView.showNoImagesMessage(false);
            mSlideshowImageView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mIsActive) {
                        pauseSlideShow();
                    } else {
                        resumeSlideShow();
                    }
                }
            });
            resumeSlideShow();
        }
    }

    private int getDuration() {
        return GalleryUtils.getSlideshowDuration(this);
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.slideshow, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch(id) {
            case android.R.id.home: {
                finish();
                return true;
            }
            case R.id.action_settings: {
                this.startActivity(new Intent(this, GallerySettings.class));
                return true;
            }
        }
        return false;
    }

    private void initializeActionBar() {
        final ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayOptions(ActionBar.DISPLAY_HOME_AS_UP | ActionBar.DISPLAY_SHOW_TITLE);
        actionBar.setTitle(getResources().getString(R.string.slideshow));
    }

    private void setKeepScreenOn(boolean keepScreenOn) {
        if (keepScreenOn) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }
    
    private void hideSystemBars() {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        getWindow().setAttributes(lp);
        
        WindowInsetsControllerCompat insetController = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        insetController.hide(WindowInsetsCompat.Type.systemBars());

        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }
    
    public void showSystemBars(boolean forceDark) {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;
        getWindow().setAttributes(lp);
        
        WindowInsetsControllerCompat insetController = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        if (forceDark) {
            insetController.setAppearanceLightStatusBars(false);
            insetController.setAppearanceLightNavigationBars(false);
        } else {
            insetController.setAppearanceLightStatusBars(!getResources().getConfiguration().isNightModeActive());
            insetController.setAppearanceLightNavigationBars(!getResources().getConfiguration().isNightModeActive());
        }
        insetController.show(WindowInsetsCompat.Type.systemBars());
        
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    private void loadNextBitmap() {
        mModel.nextSlide(new FutureListener<SlideshowPage.Slide>() {
            @Override
            public void onFutureDone(Future<SlideshowPage.Slide> future) {
                mPendingSlide = future.get();
                mHandler.sendEmptyMessage(MSG_SHOW_PENDING_BITMAP);
            }
        });
    }

    private void showPendingBitmap() {
        SlideshowPage.Slide slide = mPendingSlide;
        if (slide != null) {
            mSlideshowImageView.next(slide.bitmap);
            mHandler.sendEmptyMessageDelayed(MSG_LOAD_NEXT_BITMAP, getDuration());
        }
    }

    private void pauseSlideShow() {
        showSystemBars(false);
        setKeepScreenOn(false);
        mIsActive = false;
        mModel.pause();

        mHandler.removeMessages(MSG_LOAD_NEXT_BITMAP);
        mHandler.removeMessages(MSG_SHOW_PENDING_BITMAP);
    }

    private void resumeSlideShow() {
        hideSystemBars();
        setKeepScreenOn(true);
        mIsActive = true;
        mModel.resume();
        mSlideshowImageView.setFillScreen(GalleryUtils.getSlideshowFillScreen(this));
        mSlideshowImageView.restart();

        if (mPendingSlide != null) {
            showPendingBitmap();
        } else {
            loadNextBitmap();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mIsActive) {
            pauseSlideShow();
        }
    }

    private boolean initializeData(Bundle data) {
        // TODO settings should update with the setting and
        // not passed in static
        boolean random = GalleryUtils.isRandomSlideshow(this);
        boolean repeat = GalleryUtils.isRepeatSlideshow(this);

        String mediaPath = data.getString(KEY_SET_PATH);
        mediaPath = FilterUtils.newFilterPath(mediaPath, FilterUtils.FILTER_IMAGE_ONLY);
        MediaSet mediaSet = mDataManager.getMediaSet(mediaPath);
        mediaSet.reload();
        if (mediaSet.getTotalMediaItemCount() == 0) {
            return false;
        }
        if (random) {
            mModel = new SlideshowDataAdapter(this,
                    new ShuffleSource(mediaSet, repeat), 0, null);
        } else {
            String itemPath = data.getString(KEY_ITEM_PATH);
            Path path = itemPath != null ? Path.fromString(itemPath) : null;
            mModel = new SlideshowDataAdapter(this, new SequentialSourceRecursive(mediaSet, repeat),
                    0, path);
        }
        return true;
    }

    private static MediaItem findMediaItem(MediaSet mediaSet, int index) {
        for (int i = 0, n = mediaSet.getSubMediaSetCount(); i < n; ++i) {
            MediaSet subset = mediaSet.getSubMediaSet(i);
            int count = subset.getTotalMediaItemCount();
            if (index < count) {
                return findMediaItem(subset, index);
            }
            index -= count;
        }
        ArrayList<MediaItem> list = mediaSet.getMediaItem(index, 1);
        return list.isEmpty() ? null : list.get(0);
    }
}
