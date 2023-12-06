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

import java.util.ArrayList;
import java.util.Random;

public class SlideshowSources {
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
    
    public static class ShuffleSource implements SlideshowDataAdapter.SlideshowSource {
        private static final String TAG = "ShuffleSource";
        private static final int RETRY_COUNT = 5;
        private final MediaSet mMediaSet;
        private final Random mRandom = new Random();
        private int mOrder[] = new int[0];
        private final boolean mRepeat;
        private long mSourceVersion = MediaSet.INVALID_DATA_VERSION;
        private int mLastIndex = -1;

        public ShuffleSource(MediaSet mediaSet, boolean repeat) {
            mMediaSet = Utils.checkNotNull(mediaSet);
            mRepeat = repeat;
        }

        @Override
        public int findItemIndex(Path path, int hint) {
            return hint;
        }

        @Override
        public MediaItem getMediaItem(int index) {
            if (!mRepeat && index >= mOrder.length) return null;
            if (mOrder.length == 0) return null;
            mLastIndex = mOrder[index % mOrder.length];
            MediaItem item = findMediaItem(mMediaSet, mLastIndex);
            for (int i = 0; i < RETRY_COUNT && item == null; ++i) {
                Log.w(TAG, "fail to find image: " + mLastIndex);
                mLastIndex = mRandom.nextInt(mOrder.length);
                item = findMediaItem(mMediaSet, mLastIndex);
            }
            return item;
        }

        @Override
        public long reload() {
            long version = mMediaSet.reload();
            if (version != mSourceVersion) {
                mSourceVersion = version;
                int count = mMediaSet.getTotalMediaItemCount();
                if (count != mOrder.length) generateOrderArray(count);
            }
            return version;
        }

        private void generateOrderArray(int totalCount) {
            if (mOrder.length != totalCount) {
                mOrder = new int[totalCount];
                for (int i = 0; i < totalCount; ++i) {
                    mOrder[i] = i;
                }
            }
            for (int i = totalCount - 1; i > 0; --i) {
                Utils.swap(mOrder, i, mRandom.nextInt(i + 1));
            }
            if (mOrder[0] == mLastIndex && totalCount > 1) {
                Utils.swap(mOrder, 0, mRandom.nextInt(totalCount - 1) + 1);
            }
        }

        @Override
        public void addContentListener(ContentListener listener) {
            mMediaSet.addContentListener(listener);
        }

        @Override
        public void removeContentListener(ContentListener listener) {
            mMediaSet.removeContentListener(listener);
        }
    }

    public static class SequentialSource implements SlideshowDataAdapter.SlideshowSource {
        private static final String TAG = "SequentialSource";
        private static final int DATA_SIZE = 32;

        private ArrayList<MediaItem> mData = new ArrayList<MediaItem>();
        private int mDataStart = 0;
        private long mDataVersion = MediaObject.INVALID_DATA_VERSION;
        private final MediaSet mMediaSet;
        private final boolean mRepeat;

        public SequentialSource(MediaSet mediaSet, boolean repeat) {
            mMediaSet = mediaSet;
            mRepeat = repeat;
        }

        @Override
        public int findItemIndex(Path path, int hint) {
            return mMediaSet.getIndexOfItem(path, hint);
        }

        @Override
        public MediaItem getMediaItem(int index) {
            int dataEnd = mDataStart + mData.size();

            if (mRepeat) {
                int count = mMediaSet.getMediaItemCount();
                if (count == 0) return null;
                index = index % count;
            }
            if (index < mDataStart || index >= dataEnd) {
                mData = mMediaSet.getMediaItem(index, DATA_SIZE);
                mDataStart = index;
                dataEnd = index + mData.size();
            }

            return (index < mDataStart || index >= dataEnd) ? null : mData.get(index - mDataStart);
        }

        @Override
        public long reload() {
            long version = mMediaSet.reload();
            if (version != mDataVersion) {
                mDataVersion = version;
                mData.clear();
            }
            return mDataVersion;
        }

        @Override
        public void addContentListener(ContentListener listener) {
            mMediaSet.addContentListener(listener);
        }

        @Override
        public void removeContentListener(ContentListener listener) {
            mMediaSet.removeContentListener(listener);
        }
    }

    public static class SequentialSourceRecursive implements SlideshowDataAdapter.SlideshowSource {
        private static final String TAG = "SequentialSourceRecursive";
        private static final int RETRY_COUNT = 5;
        private long mDataVersion = MediaObject.INVALID_DATA_VERSION;
        private final MediaSet mMediaSet;
        private final boolean mRepeat;
        private int mTotal;

        public SequentialSourceRecursive(MediaSet mediaSet, boolean repeat) {
            mMediaSet = mediaSet;
            mRepeat = repeat;
        }

        @Override
        public int findItemIndex(Path path, int hint) {
            return hint;
        }

        @Override
        public MediaItem getMediaItem(int index) {
            if (!mRepeat && index >= mTotal) return null;
            if (mTotal == 0) return null;
            if (mRepeat) {
                index = index % mTotal;
            }
            MediaItem item = findMediaItem(mMediaSet, index);
            for (int i = 0; i < RETRY_COUNT && item == null; ++i) {
                Log.w(TAG, "fail to find image: " + index);
                item = findMediaItem(mMediaSet, index);
            }
            return item;
        }

        @Override
        public long reload() {
            long version = mMediaSet.reload();
            if (version != mDataVersion) {
                mDataVersion = version;
                mTotal = mMediaSet.getTotalMediaItemCount();
            }
            return mDataVersion;
        }

        @Override
        public void addContentListener(ContentListener listener) {
            mMediaSet.addContentListener(listener);
        }

        @Override
        public void removeContentListener(ContentListener listener) {
            mMediaSet.removeContentListener(listener);
        }
    }
}
