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

package com.android.gallery3d.ui;

import com.android.gallery3d.R;
import com.android.gallery3d.app.AbstractGalleryActivity;
import com.android.gallery3d.app.AlbumSetDataLoader;
import com.android.gallery3d.data.MediaObject;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.glrenderer.ColorTexture;
import com.android.gallery3d.glrenderer.FadeInTexture;
import com.android.gallery3d.glrenderer.GLCanvas;
import com.android.gallery3d.glrenderer.ResourceTexture;
import com.android.gallery3d.glrenderer.Texture;
import com.android.gallery3d.glrenderer.TiledTexture;
import com.android.gallery3d.glrenderer.UploadedTexture;
import com.android.gallery3d.ui.AlbumSetSlidingWindow.AlbumSetEntry;
import com.android.gallery3d.util.Log;

public class AlbumSetSlotRenderer extends AbstractSlotRenderer {
    @SuppressWarnings("unused")
    private static final String TAG = "AlbumSetSlotRenderer";
    private static boolean DEBUG = false;
    private static final int CACHE_SIZE = 96;
    private final int mPlaceholderColor;

    private final ColorTexture mWaitLoadingTexture;
    private final AbstractGalleryActivity mActivity;
    private final SelectionManager mSelectionManager;
    protected final LabelSpec mLabelSpec;

    protected AlbumSetSlidingWindow mDataWindow;
    private SlotView mSlotView;

    private int mPressedIndex = -1;
    private boolean mAnimatePressedUp;
    private Path mHighlightItemPath = null;
    private boolean mInSelectionMode;

    public static class LabelSpec {
        public int labelBackgroundHeight;
        public int titleOffset;
        public int countOffset;
        public int titleFontSize;
        public int countFontSize;
        public int leftMargin;
        public int iconSize;
        public int titleRightMargin;
        public int backgroundColor;
        public int titleColor;
        public int countColor;
        public int borderSize;
    }

    public AlbumSetSlotRenderer(AbstractGalleryActivity activity,
            SelectionManager selectionManager,
            SlotView slotView, LabelSpec labelSpec, int placeholderColor) {
        super (activity);
        mActivity = activity;
        mSelectionManager = selectionManager;
        mSlotView = slotView;
        mLabelSpec = labelSpec;
        mPlaceholderColor = placeholderColor;

        mWaitLoadingTexture = new ColorTexture(mPlaceholderColor);
        mWaitLoadingTexture.setSize(1, 1);
    }

    public void setPressedIndex(int index) {
        if (mPressedIndex == index) return;
        mPressedIndex = index;
        mSlotView.invalidate();
    }

    public void setPressedUp() {
        if (mPressedIndex == -1) return;
        mAnimatePressedUp = true;
        mSlotView.invalidate();
    }

    public void setHighlightItemPath(Path path) {
        if (mHighlightItemPath == path) return;
        mHighlightItemPath = path;
        mSlotView.invalidate();
    }

    public void setModel(AlbumSetDataLoader model) {
        if (mDataWindow != null) {
            mDataWindow.setListener(null);
            mDataWindow = null;
            mSlotView.setSlotCount(0);
        }
        if (model != null) {
            mDataWindow = new AlbumSetSlidingWindow(
                    mActivity, model, mLabelSpec, CACHE_SIZE);
            mDataWindow.setListener(new MyCacheListener());
            mSlotView.setSlotCount(mDataWindow.size());
        }
    }

    private static Texture checkLabelTexture(Texture texture) {
        return ((texture instanceof UploadedTexture)
                && ((UploadedTexture) texture).isUploading())
                ? null
                : texture;
    }

    private static Texture checkContentTexture(Texture texture) {
        return ((texture instanceof TiledTexture)
                && !((TiledTexture) texture).isReady())
                ? null
                : texture;
    }

    @Override
    public int renderSlot(GLCanvas canvas, int index, int pass, int width, int height) {
        AlbumSetEntry entry = mDataWindow.get(index);
        int renderRequestFlags = 0;
        renderRequestFlags |= renderContent(canvas, entry, width, height);
        renderRequestFlags |= renderLabel(canvas, entry, width, height);
        renderRequestFlags |= renderOverlay(canvas, index, entry, width, height);
        renderRequestFlags |= renderSelected(canvas, index, entry, width, height);
        return renderRequestFlags;
    }

    protected int renderOverlay(
            GLCanvas canvas, int index, AlbumSetEntry entry, int width, int height) {
        int renderRequestFlags = 0;
        if (entry.album != null) {
            if (entry.album.isCameraRoll()) {
                drawCameraOverlay(canvas, width, height);
            } else if (entry.album.isMoviesAlbum()) {
                drawVideoOverlay(canvas, width, height);
            } else if (entry.album.isDownloadAlbum()) {
                drawDownloadOverlay(canvas, width, height);
            } else if (entry.album.isSnapshotAlbum()) {
                drawSnapshotOverlay(canvas, width, height);
            } else if (entry.album.isPicturesAlbum()) {
                drawPicturesOverlay(canvas, width, height);
            }
        }

        return renderRequestFlags;
    }

    protected int renderSelected(
            GLCanvas canvas, int index, AlbumSetEntry entry, int width, int height) {
        int renderRequestFlags = 0;

        if (mPressedIndex == index) {
            if (mAnimatePressedUp) {
                drawPressedUpFrame(canvas, width, height);
                renderRequestFlags |= SlotView.RENDER_MORE_FRAME;
                if (isPressedUpFrameFinished()) {
                    mAnimatePressedUp = false;
                    mPressedIndex = -1;
                }
            } else {
                drawPressedFrame(canvas, width, height);
            }
        } else if ((mHighlightItemPath != null) && (mHighlightItemPath == entry.setPath)) {
            drawSelectedOverlay(canvas, width, height);
        } else if (mInSelectionMode && mSelectionManager.isItemSelected(entry.setPath)) {
            drawSelectedOverlay(canvas, width, height);
        }
        return renderRequestFlags;
    }

    protected int renderContent(
            GLCanvas canvas, AlbumSetEntry entry, int width, int height) {
        int renderRequestFlags = 0;

        Texture content = checkContentTexture(entry.content);
        if (content == null) {
            content = mWaitLoadingTexture;
            entry.isWaitLoadingDisplayed = true;
        } else if (entry.isWaitLoadingDisplayed) {
            entry.isWaitLoadingDisplayed = false;
            content = new FadeInTexture(mPlaceholderColor, entry.bitmapTexture);
            entry.content = content;
        }
        drawContent(canvas, content, width, height, entry.rotation);
        if ((content instanceof FadeInTexture) &&
                ((FadeInTexture) content).isAnimating()) {
            renderRequestFlags |= SlotView.RENDER_MORE_FRAME;
        }

        return renderRequestFlags;
    }

    protected int renderLabel(
            GLCanvas canvas, AlbumSetEntry entry, int width, int height) {
        Texture content = checkLabelTexture(entry.labelTexture);
        if (content == null) {
            content = mWaitLoadingTexture;
        }
        int b = AlbumLabelMaker.getBorderSize();
        int h = mLabelSpec.labelBackgroundHeight;
        content.draw(canvas, -b, height - h + b, width + b + b, h);

        return 0;
    }

    @Override
    public void prepareDrawing() {
        mInSelectionMode = mSelectionManager.inSelectionMode();
    }

    private class MyCacheListener implements AlbumSetSlidingWindow.Listener {

        @Override
        public void onSizeChanged(int size) {
            if (DEBUG) Log.d(TAG, "onSizeChanged " + size);
            mSlotView.setSlotCount(size);
            if (size == 0) {
                mSlotView.invalidate();
            }
        }

        @Override
        public void onContentChanged() {
            if (DEBUG) Log.d(TAG, "onContentChanged");
            mSlotView.invalidate();
        }
    }

    public void pause() {
        mDataWindow.pause();
    }

    public void resume() {
        mDataWindow.resume();
    }

    @Override
    public void onVisibleRangeChanged(int visibleStart, int visibleEnd) {
        if (mDataWindow != null) {
            mDataWindow.setActiveWindow(visibleStart, visibleEnd);
        }
    }

    @Override
    public void onSlotSizeChanged(int width, int height) {
        if (mDataWindow != null) {
            mDataWindow.onSlotSizeChanged(width, height);
        }
    }

    @Override
    protected void drawVideoOverlay(GLCanvas canvas, int width, int height) {
        mVideoOverlay.draw(canvas, width - 15 - mVideoOverlay.getWidth(),
                height - mVideoOverlay.getHeight() - 15);
    }

    @Override
    protected void drawCameraOverlay(GLCanvas canvas, int width, int height) {
        mCameraOverlay.draw(canvas, width - 15 - mCameraOverlay.getWidth(),
                height - mCameraOverlay.getHeight() - 15);
    }

    @Override
    protected void drawSnapshotOverlay(GLCanvas canvas, int width, int height) {
        mSnapshotOverlay.draw(canvas, width - 15 - mSnapshotOverlay.getWidth(),
                height - mSnapshotOverlay.getHeight() - 15);
    }

    @Override
    protected void drawDownloadOverlay(GLCanvas canvas, int width, int height) {
        mDownloadOverlay.draw(canvas, width - 15 - mDownloadOverlay.getWidth(),
                height - mDownloadOverlay.getHeight() - 15);
    }
    
    @Override
    protected void drawPicturesOverlay(GLCanvas canvas, int width, int height) {
        mPicturesOverlay.draw(canvas, width - 15 - mPicturesOverlay.getWidth(),
                height - mPicturesOverlay.getHeight() - 15);
    }
}
