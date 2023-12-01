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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Window;
import android.view.WindowManager;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import com.android.gallery3d.R;
import com.android.gallery3d.anim.StateTransitionAnimation;
import com.android.gallery3d.glrenderer.RawTexture;
import com.android.gallery3d.ui.GLView;
import com.android.gallery3d.ui.PreparePageFadeoutTexture;
import com.android.gallery3d.util.GalleryUtils;

abstract public class ActivityState {
    protected AbstractGalleryActivity mActivity;
    protected Bundle mData;
    protected ResultEntry mReceivedResults;
    protected ResultEntry mResult;

    protected static class ResultEntry {
        public int requestCode;
        public int resultCode = Activity.RESULT_CANCELED;
        public Intent resultData;
    }

    private boolean mDestroyed = false;
    boolean mIsFinishing = false;

    private static final String KEY_TRANSITION_IN = "transition-in";

    private StateTransitionAnimation.Transition mNextTransition =
            StateTransitionAnimation.Transition.None;
    private StateTransitionAnimation mIntroAnimation;
    private GLView mContentPane;

    protected ActivityState() {
    }

    protected void setContentPane(GLView content) {
        mContentPane = content;
        if (mIntroAnimation != null) {
            mContentPane.setIntroAnimation(mIntroAnimation);
            mIntroAnimation = null;
        }
        mContentPane.setBackgroundColor(getBackgroundColor());
        mActivity.getGLRoot().setContentPane(mContentPane);
    }

    void initialize(AbstractGalleryActivity activity, Bundle data) {
        mActivity = activity;
        mData = data;
    }

    public Bundle getData() {
        return mData;
    }

    protected void onBackPressed() {
        mActivity.getStateManager().finishState(this);
    }

    protected void setStateResult(int resultCode, Intent data) {
        if (mResult == null) return;
        mResult.resultCode = resultCode;
        mResult.resultData = data;
    }

    protected void onConfigurationChanged(Configuration config) {
    }

    protected void onSaveState(Bundle outState) {
    }

    protected void onStateResult(int requestCode, int resultCode, Intent data) {
    }

    protected float[] mBackgroundColor;

    abstract protected int getBackgroundColorId();

    protected float[] getBackgroundColor() {
        return mBackgroundColor;
    }

    protected void onCreate(Bundle data, Bundle storedState) {
        mBackgroundColor = GalleryUtils.intColorToFloatARGBArray(
                mActivity.getResources().getColor(getBackgroundColorId()));
    }

    protected void clearStateResult() {
    }

    protected void transitionOnNextPause(Class<? extends ActivityState> outgoing,
            Class<? extends ActivityState> incoming, StateTransitionAnimation.Transition hint) {
        if (outgoing == SinglePhotoPage.class && incoming == AlbumPage.class) {
            mNextTransition = StateTransitionAnimation.Transition.Outgoing;
        } else if (outgoing == AlbumPage.class && incoming == SinglePhotoPage.class) {
            mNextTransition = StateTransitionAnimation.Transition.PhotoIncoming;
        } else {
            mNextTransition = hint;
        }
    }

    protected void performHapticFeedback(int feedbackConstant) {
        mActivity.getWindow().getDecorView().performHapticFeedback(feedbackConstant,
                HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING);
    }

    protected void onPause() {
        if (mNextTransition != StateTransitionAnimation.Transition.None) {
            mActivity.getTransitionStore().put(KEY_TRANSITION_IN, mNextTransition);
            PreparePageFadeoutTexture.prepareFadeOutTexture(mActivity, mContentPane);
            mNextTransition = StateTransitionAnimation.Transition.None;
        }
    }

    // should only be called by StateManager
    void resume() {
        AbstractGalleryActivity activity = mActivity;
        ActionBar actionBar = activity.getSupportActionBar();
        if (actionBar != null) {
            int stateCount = mActivity.getStateManager().getStateCount();
            mActivity.getGalleryActionBar().setDisplayOptions(stateCount > 1, true);
            // Default behavior, this can be overridden in ActivityState's onResume.
            actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
        }

        activity.invalidateOptionsMenu();

        ResultEntry entry = mReceivedResults;
        if (entry != null) {
            mReceivedResults = null;
            onStateResult(entry.requestCode, entry.resultCode, entry.resultData);
        }

        onResume();

        // the transition store should be cleared after resume;
        mActivity.getTransitionStore().clear();
    }

    // a subclass of ActivityState should override the method to resume itself
    protected void onResume() {
        RawTexture fade = mActivity.getTransitionStore().get(
                PreparePageFadeoutTexture.KEY_FADE_TEXTURE);
        mNextTransition = mActivity.getTransitionStore().get(
                KEY_TRANSITION_IN, StateTransitionAnimation.Transition.None);
        if (mNextTransition != StateTransitionAnimation.Transition.None) {
            mIntroAnimation = new StateTransitionAnimation(mNextTransition, fade);
            mNextTransition = StateTransitionAnimation.Transition.None;
        }
    }

    protected boolean onCreateActionBar(Menu menu) {
        // TODO: we should return false if there is no menu to show
        //       this is a workaround for a bug in system
        return true;
    }

    protected boolean onItemSelected(MenuItem item) {
        return false;
    }

    protected void onDestroy() {
        mDestroyed = true;
    }

    boolean isDestroyed() {
        return mDestroyed;
    }

    public boolean isFinishing() {
        return mIsFinishing;
    }

    protected MenuInflater getSupportMenuInflater() {
        return mActivity.getMenuInflater();
    }
}
