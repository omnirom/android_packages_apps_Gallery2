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
import android.view.MotionEvent;

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
import com.android.gallery3d.glrenderer.GLCanvas;
import com.android.gallery3d.ui.GLView;
import com.android.gallery3d.ui.SlideshowView;
import com.android.gallery3d.ui.SynchronizedHandler;
import com.android.gallery3d.util.Future;
import com.android.gallery3d.util.FutureListener;
import com.android.gallery3d.util.GalleryUtils;

import java.util.ArrayList;
import java.util.Random;

public class SlideshowPage extends ActivityState {
    private static final String TAG = "SlideshowPage";

    public static final String KEY_SET_PATH = "media-set-path";
    public static final String KEY_ITEM_PATH = "media-item-path";
    public static final String KEY_PHOTO_INDEX = "photo-index";
    public static final String KEY_RANDOM_ORDER = "random-order";
    public static final String KEY_REPEAT = "repeat";

    private static final int SLIDESHOW_DELAY = 3000; // 3 seconds

    private static final int MSG_LOAD_NEXT_BITMAP = 1;
    private static final int MSG_SHOW_PENDING_BITMAP = 2;

    public static interface Model {
        public void pause();

        public void resume();

        public Future<Slide> nextSlide(FutureListener<Slide> listener);
    }

    public static class Slide {
        public Bitmap bitmap;
        public MediaItem item;
        public int index;

        public Slide(MediaItem item, int index, Bitmap bitmap) {
            this.bitmap = bitmap;
            this.item = item;
            this.index = index;
        }
    }

    private Handler mHandler;
    private Model mModel;
    private SlideshowView mSlideshowView;

    private Slide mPendingSlide = null;
    private boolean mIsActive = false;
    private final Intent mResultIntent = new Intent();
    private int mDuration = SLIDESHOW_DELAY;

    @Override
    protected int getBackgroundColorId() {
        return R.color.slideshow_background;
    }

    private final GLView mRootPane = new GLView() {
        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            mSlideshowView.layout(0, 0, right - left, bottom - top);
        }

        @Override
        protected boolean onTouch(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                onBackPressed();
            }
            return true;
        }

        @Override
        protected void renderBackground(GLCanvas canvas) {
            canvas.clearBuffer(getBackgroundColor());
        }
    };

    @Override
    public void onCreate(Bundle data, Bundle restoreState) {
        super.onCreate(data, restoreState);
        // User-initiated slideshow would always keep screen on.
        mActivity.setKeepScreenOn(true);

        mHandler = new SynchronizedHandler(mActivity.getGLRoot()) {
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
        mDuration = GalleryUtils.getSlideshowDuration(mActivity);
        initializeViews();
        initializeData(data);
    }

    private void loadNextBitmap() {
        mModel.nextSlide(new FutureListener<Slide>() {
            @Override
            public void onFutureDone(Future<Slide> future) {
                mPendingSlide = future.get();
                mHandler.sendEmptyMessage(MSG_SHOW_PENDING_BITMAP);
            }
        });
    }

    private void showPendingBitmap() {
        // mPendingBitmap could be null, if
        // 1.) there is no more items
        // 2.) mModel is paused
        Slide slide = mPendingSlide;
        if (slide == null) {
            if (mIsActive) {
                mActivity.getStateManager().finishState(SlideshowPage.this);
            }
            return;
        }

        mSlideshowView.next(slide.bitmap, slide.item.getRotation());

        setStateResult(Activity.RESULT_OK, mResultIntent
                .putExtra(KEY_ITEM_PATH, slide.item.getPath().toString())
                .putExtra(KEY_PHOTO_INDEX, slide.index));
        mHandler.sendEmptyMessageDelayed(MSG_LOAD_NEXT_BITMAP, mDuration);
    }

    @Override
    public void onPause() {
        super.onPause();
        mActivity.showSystemBars(false);
        mIsActive = false;
        mModel.pause();
        mSlideshowView.release();

        mHandler.removeMessages(MSG_LOAD_NEXT_BITMAP);
        mHandler.removeMessages(MSG_SHOW_PENDING_BITMAP);
    }

    @Override
    public void onResume() {
        super.onResume();
        mActivity.hideSystemBars();
        mActivity.setBottomControlMargin(false);
        mIsActive = true;
        mModel.resume();

        if (mPendingSlide != null) {
            showPendingBitmap();
        } else {
            loadNextBitmap();
        }
    }

    private void initializeData(Bundle data) {
        boolean random = data.getBoolean(KEY_RANDOM_ORDER, false);

        String mediaPath = data.getString(KEY_SET_PATH);
        mediaPath = FilterUtils.newFilterTypePath(mediaPath, FilterUtils.FILTER_IMAGE_ONLY);
        MediaSet mediaSet = mActivity.getDataManager().getMediaSet(mediaPath);
        if (random) {
            boolean repeat = data.getBoolean(KEY_REPEAT);
            mModel = new SlideshowDataAdapter(mActivity,
                    new ShuffleSource(mediaSet, repeat), 0, null);
            setStateResult(Activity.RESULT_OK, mResultIntent.putExtra(KEY_PHOTO_INDEX, 0));
        } else {
            int index = data.getInt(KEY_PHOTO_INDEX);
            String itemPath = data.getString(KEY_ITEM_PATH);
            Path path = itemPath != null ? Path.fromString(itemPath) : null;
            boolean repeat = data.getBoolean(KEY_REPEAT);
            mModel = new SlideshowDataAdapter(mActivity, new SequentialSourceRecursive(mediaSet, repeat),
                    index, path);
            setStateResult(Activity.RESULT_OK, mResultIntent.putExtra(KEY_PHOTO_INDEX, index));
        }
    }

    private void initializeViews() {
        mSlideshowView = new SlideshowView(mDuration);
        mRootPane.addComponent(mSlideshowView);
        setContentPane(mRootPane);
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
