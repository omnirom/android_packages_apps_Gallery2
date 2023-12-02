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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.gallery3d.R;
import com.android.gallery3d.anim.CanvasAnimation;
import com.android.gallery3d.anim.FloatAnimation;
import com.android.gallery3d.glrenderer.BitmapTexture;
import com.android.gallery3d.glrenderer.GLCanvas;
import com.android.gallery3d.util.GalleryUtils;

import java.util.Random;

public class SlideshowImageView extends FrameLayout {
    @SuppressWarnings("unused")
    private static final String TAG = "SlideshowImageView";

    private static final int DEFAULT_TRANSITION_DURATION = 1000;

    private ImageView mBackImage;
    private ImageView mFrontImage;
    private boolean mFirstImage = true;
    private Bitmap mCurrentBitmap;
    private TextView mNoImagesMessage;

    public SlideshowImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SlideshowImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public SlideshowImageView(Context context) {
        super(context);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mBackImage = findViewById(R.id.back_image);
        mFrontImage = findViewById(R.id.front_image);
        mNoImagesMessage = findViewById(R.id.slideshow_no_images);
    }

    public void showNoImagesMessage(boolean show) {
        if (show) {
            mNoImagesMessage.setVisibility(View.VISIBLE);
        } else {
            mNoImagesMessage.setVisibility(View.GONE);
        }
    }

    public void restart() {
        mFirstImage = true;
    }

    private void setScaleType() {
        boolean fillscreen = GalleryUtils.getSlideshowFillScreen(getContext());
        mBackImage.setScaleType(fillscreen ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);
        mFrontImage.setScaleType(fillscreen ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER);
    }

    private int getTransitionDuration() {
        int duration = GalleryUtils.getSlideshowDuration(getContext());
        if (duration <= DEFAULT_TRANSITION_DURATION) {
            return 0;
        }
        return DEFAULT_TRANSITION_DURATION;
    }

    public void next(Bitmap bitmap) {
        setScaleType();
        int transitionDuration = getTransitionDuration();

        if (mFirstImage) {
            mCurrentBitmap = bitmap;
            mBackImage.setImageBitmap(bitmap);
            mFirstImage = false;
        } else {
            mFrontImage.setImageBitmap(mCurrentBitmap);
            mFrontImage.setAlpha(1f);
            mFrontImage.setScaleX(1f);
            mFrontImage.setScaleY(1f);
            mFrontImage.animate().alpha(0).scaleX(1.5F).scaleY(1.5F).setDuration(transitionDuration);
            mCurrentBitmap = bitmap;
            mBackImage.setImageBitmap(bitmap);
            mBackImage.setAlpha(0f);
            mBackImage.animate().alpha(1f).setDuration(transitionDuration);
        }
    }
}
