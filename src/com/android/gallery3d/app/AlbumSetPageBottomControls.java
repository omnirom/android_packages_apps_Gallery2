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
import android.content.res.TypedArray;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import com.android.gallery3d.R;

import java.util.HashMap;
import java.util.Map;

public class AlbumSetPageBottomControls implements OnClickListener {
    public interface Delegate {
        public void onBottomControlClicked(int control);
    }

    private Delegate mDelegate;
    private ViewGroup mContainer;
    private Context mContext;

    private Animation mContainerAnimIn = new AlphaAnimation(0f, 1f);
    private Animation mContainerAnimOut = new AlphaAnimation(1f, 0f);
    private static final int CONTAINER_ANIM_DURATION_MS = 350;

    public AlbumSetPageBottomControls(Delegate delegate, Context context, ViewGroup layout) {
        mDelegate = delegate;
        mContext = context;

        mContainer = (ViewGroup) layout.findViewById(R.id.albumsetpage_bottom_controls);
        for (int i = 0; i < mContainer.getChildCount(); i++) {
            View child = mContainer.getChildAt(i);
            child.setOnClickListener(this);
        }

        mContainerAnimIn.setDuration(CONTAINER_ANIM_DURATION_MS);
        mContainerAnimOut.setDuration(CONTAINER_ANIM_DURATION_MS);
    }

    public void hide(boolean withAnim) {
        if (mContainer.getVisibility() != View.GONE) {
            mContainer.clearAnimation();
            mContainerAnimOut.reset();
            if (withAnim) {
                mContainer.startAnimation(mContainerAnimOut);
            }
            mContainer.setVisibility(View.GONE);
        }
    }

    public void show(boolean withAnim) {
        if (mContainer.getVisibility() != View.VISIBLE) {
            mContainer.clearAnimation();
            mContainerAnimIn.reset();
            if (withAnim) {
                mContainer.startAnimation(mContainerAnimIn);
            }
            mContainer.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onClick(View view) {
        mDelegate.onBottomControlClicked(view.getId());
    }

    public void selectItemWithId(int id) {
        for (int i = 0; i < mContainer.getChildCount(); i++) {
            View child = mContainer.getChildAt(i);
            ImageButton b = (ImageButton) child;
            if (child.getId() == id) {
                b.getDrawable().mutate().setTint(getAttrColor(android.R.attr.colorAccent));
            } else {
                b.getDrawable().mutate().setTint(getAttrColor(android.R.attr.colorControlNormal));
            }
        }
    }

    public void showItemWithId(int id, boolean visible) {
        for (int i = 0; i < mContainer.getChildCount(); i++) {
            View child = mContainer.getChildAt(i);
            ImageButton b = (ImageButton) child;
            if (child.getId() == id) {
                if (visible) {
                    child.setVisibility(View.VISIBLE);
                } else {
                    child.setVisibility(View.GONE);
                }
            }
        }
    }

    public void setGradientBackground(boolean gradient) {
        if (mContainer.getBackground() != null) {
            Drawable[] arrayDrawable = new Drawable[2];
            if (gradient) {
                arrayDrawable[0] = mContainer.getBackground();
                arrayDrawable[1] = mContext.getResources().getDrawable(R.drawable.root_bottom_bg);
            } else {
                arrayDrawable[0] = mContainer.getBackground();
                arrayDrawable[1] = new ColorDrawable(mContext.getResources().getColor(R.color.photo_overlay));
            }
            TransitionDrawable transitionDrawable = new TransitionDrawable(arrayDrawable);
            transitionDrawable.setCrossFadeEnabled(true);
            mContainer.setBackground(transitionDrawable);
            transitionDrawable.startTransition(500);
        } else {
            if (gradient) {
                mContainer.setBackground(mContext.getResources().getDrawable(R.drawable.root_bottom_bg));
            } else {
                mContainer.setBackground(
                        new ColorDrawable(mContext.getResources().getColor(R.color.photo_overlay)));
            }
        }
    }
    
    private int getAttrColor(Integer attr) {
        TypedArray ta = mContext.obtainStyledAttributes(new int[]{attr});
        int color = ta.getColor(0, 0);
        ta.recycle();
        return color;
    }
}
