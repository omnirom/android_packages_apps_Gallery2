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

package com.android.gallery3d.app;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.RelativeLayout;

import com.android.gallery3d.R;

import java.util.HashMap;
import java.util.Map;

public class PhotoPageBottomControls implements OnClickListener {
    public interface Delegate {
        public void onBottomControlClicked(int control);
    }

    private Delegate mDelegate;
    private ViewGroup mContainer;

    private Animation mContainerAnimIn = new AlphaAnimation(0f, 1f);
    private Animation mContainerAnimOut = new AlphaAnimation(1f, 0f);
    private static final int CONTAINER_ANIM_DURATION_MS = 200;

    private static final int CONTROL_ANIM_DURATION_MS = 150;
    private static Animation getControlAnimForVisibility(boolean visible) {
        Animation anim = visible ? new AlphaAnimation(0f, 1f)
                : new AlphaAnimation(1f, 0f);
        anim.setDuration(CONTROL_ANIM_DURATION_MS);
        return anim;
    }

    public PhotoPageBottomControls(Delegate delegate, Context context, ViewGroup layout) {
        mDelegate = delegate;

        mContainer = (ViewGroup) layout.findViewById(R.id.photopage_bottom_controls);
        for (int i = mContainer.getChildCount() - 1; i >= 0; i--) {
            View child = mContainer.getChildAt(i);
            child.setOnClickListener(this);
        }

        mContainerAnimIn.setDuration(CONTAINER_ANIM_DURATION_MS);
        mContainerAnimOut.setDuration(CONTAINER_ANIM_DURATION_MS);
    }

    public void hide() {
        if (mContainer.getVisibility() != View.GONE) {
            mContainer.clearAnimation();
            mContainerAnimOut.reset();
            mContainer.startAnimation(mContainerAnimOut);
            mContainer.setVisibility(View.GONE);
        }
    }

    public void show() {
        if (mContainer.getVisibility() != View.VISIBLE) {
            mContainer.clearAnimation();
            mContainerAnimIn.reset();
            mContainer.startAnimation(mContainerAnimIn);
            mContainer.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onClick(View view) {
        mDelegate.onBottomControlClicked(view.getId());
    }
}
