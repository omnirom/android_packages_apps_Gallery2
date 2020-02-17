/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;

import com.android.gallery3d.R;
import com.android.gallery3d.glrenderer.FadeOutTexture;
import com.android.gallery3d.glrenderer.GLCanvas;
import com.android.gallery3d.glrenderer.GLPaint;
import com.android.gallery3d.glrenderer.NinePatchTexture;
import com.android.gallery3d.glrenderer.ResourceTexture;
import com.android.gallery3d.glrenderer.Texture;

public abstract class AbstractSlotRenderer implements SlotView.SlotRenderer {

    private final ResourceTexture mVideoPlayIcon;
    private final ResourceTexture mPanoramaIcon;
    private final ResourceTexture mFramePressed;
    private FadeOutTexture mFramePressedUp;
    private GLPaint mFramePaint;
    private final ResourceTexture mSelectionIcon;
    private final ResourceTexture mCameraOverlay;
    private final ResourceTexture mSnapshotOverlay;
    private final ResourceTexture mDownloadOverlay;

    protected AbstractSlotRenderer(Context context) {
        mVideoPlayIcon = new ResourceTexture(context, R.drawable.ic_gallery_play);
        mPanoramaIcon = new ResourceTexture(context, R.drawable.ic_360pano_holo_light);
        mFramePressed = new ResourceTexture(context, R.drawable.grid_pressed_overlay);
        mCameraOverlay = new ResourceTexture(context, R.drawable.ic_camera_album_overlay);
        mSnapshotOverlay = new ResourceTexture(context, R.drawable.ic_snapshot_album_overlay);
        mDownloadOverlay = new ResourceTexture(context, R.drawable.ic_download_album_overlay);
        mFramePaint = new GLPaint();
        mFramePaint.setColor(context.getResources().getColor(R.color.primary_light));
        mFramePaint.setLineWidth(context.getResources().getDimensionPixelSize(R.dimen.selected_frame_width));
        mSelectionIcon = new ResourceTexture(context, R.drawable.multiselect);
    }

    protected void drawContent(GLCanvas canvas,
            Texture content, int width, int height, int rotation) {
        canvas.save(GLCanvas.SAVE_FLAG_MATRIX);

        // The content is always rendered in to the largest square that fits
        // inside the slot, aligned to the top of the slot.
        width = height = Math.min(width, height);
        if (rotation != 0) {
            canvas.translate(width / 2, height / 2);
            canvas.rotate(rotation, 0, 0, 1);
            canvas.translate(-width / 2, -height / 2);
        }

        // Fit the content into the box
        float scale = Math.min(
                (float) width / content.getWidth(),
                (float) height / content.getHeight());
        canvas.scale(scale, scale, 1);
        content.draw(canvas, 0, 0);

        canvas.restore();
    }

    protected void drawVideoOverlay(GLCanvas canvas, int width, int height) {
        mVideoPlayIcon.draw(canvas, width - 15 - mVideoPlayIcon.getWidth(), 15);
    }

    protected void drawPanoramaIcon(GLCanvas canvas, int width, int height) {
        mPanoramaIcon.draw(canvas, width - 15 - mPanoramaIcon.getWidth(), 15);
    }

    protected void drawCameraOverlay(GLCanvas canvas, int width, int height) {
        mCameraOverlay.draw(canvas, width - 15 - mCameraOverlay.getWidth(), 15);
    }

    protected void drawSnapshotOverlay(GLCanvas canvas, int width, int height) {
        mSnapshotOverlay.draw(canvas, width - 15 - mSnapshotOverlay.getWidth(), 15);
    }

    protected void drawDownloadOverlay(GLCanvas canvas, int width, int height) {
        mDownloadOverlay.draw(canvas, width - 15 - mDownloadOverlay.getWidth(), 15);
    }

    protected boolean isPressedUpFrameFinished() {
        if (mFramePressedUp != null) {
            if (mFramePressedUp.isAnimating()) {
                return false;
            } else {
                mFramePressedUp = null;
            }
        }
        return true;
    }

    protected void drawPressedUpFrame(GLCanvas canvas, int width, int height) {
        if (mFramePressedUp == null) {
            mFramePressedUp = new FadeOutTexture(mFramePressed);
        }
        drawFrame(canvas, new Rect(0, 0, 0, 0), mFramePressedUp, 0, 0, width, height);
    }

    protected void drawPressedFrame(GLCanvas canvas, int width, int height) {
        mFramePressed.draw(canvas, 0, 0, width, height);
    }

    private void drawSelectedFrame(GLCanvas canvas, int width, int height) {
        canvas.drawRect(0, 0, width, height, mFramePaint);
    }

    protected void drawSelectedOverlay(GLCanvas canvas, int width, int height) {
        mSelectionIcon.draw(canvas, 15, 15);
        drawSelectedFrame(canvas, width, height);
    }

    protected static void drawFrame(GLCanvas canvas, Rect padding, Texture frame,
            int x, int y, int width, int height) {
        frame.draw(canvas, x - padding.left, y - padding.top, width + padding.left + padding.right,
                 height + padding.top + padding.bottom);
    }
}
