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

import android.content.Context;
import android.graphics.Bitmap;

import com.android.gallery3d.data.ContentListener;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.MediaObject;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.util.Future;
import com.android.gallery3d.util.FutureListener;
import com.android.gallery3d.util.Log;
import com.android.gallery3d.util.ThreadPool;
import com.android.gallery3d.util.ThreadPool.Job;
import com.android.gallery3d.util.ThreadPool.JobContext;

import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;

public class SlideshowDataAdapter {
    @SuppressWarnings("unused")
    private static final String TAG = "SlideshowDataAdapter";

    private static final int IMAGE_QUEUE_CAPACITY = 5;

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

    public interface SlideshowSource {
        public void addContentListener(ContentListener listener);
        public void removeContentListener(ContentListener listener);
        public long reload();
        public MediaItem getMediaItem(int index);
        public int findItemIndex(Path path, int hint);
    }

    private final SlideshowSource mSource;

    private int mLoadIndex = 0;
    private int mNextOutput = 0;
    private boolean mIsActive = false;
    private boolean mNeedReset;
    private boolean mDataReady;
    private Path mInitialPath;

    private final LinkedList<Slide> mImageQueue = new LinkedList<Slide>();

    private Future<Void> mReloadTask;
    private final ThreadPool mThreadPool;

    private long mDataVersion = MediaObject.INVALID_DATA_VERSION;
    private final AtomicBoolean mNeedReload = new AtomicBoolean(false);
    private final SourceListener mSourceListener = new SourceListener();

    // The index is just a hint if initialPath is set
    public SlideshowDataAdapter(Context context, SlideshowSource source, int index,
            Path initialPath) {
        mSource = source;
        mInitialPath = initialPath;
        mLoadIndex = index;
        mNextOutput = index;
        GalleryApp app = (GalleryApp) context.getApplicationContext();
        mThreadPool = app.getThreadPool();
    }

    private MediaItem loadItem() {
        if (mNeedReload.compareAndSet(true, false)) {
            long v = mSource.reload();
            if (v != mDataVersion) {
                mDataVersion = v;
                mNeedReset = true;
                return null;
            }
        }
        int index = mLoadIndex;
        if (mInitialPath != null) {
            index = mSource.findItemIndex(mInitialPath, index);
            mInitialPath = null;
            if (index == MediaSet.INDEX_NOT_FOUND) {
                mLoadIndex = index = 0;
            }
        }
        return mSource.getMediaItem(index);
    }

    private class ReloadTask implements Job<Void> {
        @Override
        public Void run(JobContext jc) {
            while (true) {
                synchronized (SlideshowDataAdapter.this) {
                    while (mIsActive && (!mDataReady
                            || mImageQueue.size() >= IMAGE_QUEUE_CAPACITY)) {
                        try {
                            SlideshowDataAdapter.this.wait();
                        } catch (InterruptedException ex) {
                            // ignored.
                        }
                        continue;
                    }
                }
                if (!mIsActive) return null;
                mNeedReset = false;
                Log.d(TAG, "loadItem mImageQueue.size() = " + mImageQueue.size());
                MediaItem item = loadItem();

                if (mNeedReset) {
                    synchronized (SlideshowDataAdapter.this) {
                        mImageQueue.clear();
                        mLoadIndex = mNextOutput;
                    }
                    continue;
                }

                if (item == null) {
                    synchronized (SlideshowDataAdapter.this) {
                        if (!mNeedReload.get()) mDataReady = false;
                        SlideshowDataAdapter.this.notifyAll();
                    }
                    continue;
                }

                Bitmap bitmap = item
                        .requestImage(MediaItem.TYPE_THUMBNAIL)
                        .run(jc);

                if (bitmap != null) {
                    synchronized (SlideshowDataAdapter.this) {
                        mImageQueue.addLast(
                                new Slide(item, mLoadIndex, bitmap));
                        if (mImageQueue.size() == 1) {
                            SlideshowDataAdapter.this.notifyAll();
                        }
                    }
                }
                ++mLoadIndex;
            }
        }
    }

    private class SourceListener implements ContentListener {
        @Override
        public void onContentDirty() {
            synchronized (SlideshowDataAdapter.this) {
                mNeedReload.set(true);
                mDataReady = true;
                SlideshowDataAdapter.this.notifyAll();
            }
        }
    }

    private synchronized Slide innerNextBitmap() {
        while (mIsActive && mDataReady && mImageQueue.isEmpty()) {
            try {
                Log.d(TAG, "innerNextBitmap wait");
                wait();
            } catch (InterruptedException t) {
                throw new AssertionError();
            }
        }
        if (mImageQueue.isEmpty()) return null;
        mNextOutput++;
        if (mImageQueue.size() == 1) {
            this.notifyAll();
        }
        return mImageQueue.removeFirst();
    }

    public Slide nextSlide() {
        return innerNextBitmap();
    }

    public void pause() {
        synchronized (this) {
            mIsActive = false;
            notifyAll();
        }
        mSource.removeContentListener(mSourceListener);
        mReloadTask.cancel();
        mReloadTask = null;
    }

    public synchronized void resume() {
        mIsActive = true;
        mSource.addContentListener(mSourceListener);
        mNeedReload.set(true);
        mDataReady = true;
        mReloadTask = mThreadPool.submit(new ReloadTask());
    }
}
