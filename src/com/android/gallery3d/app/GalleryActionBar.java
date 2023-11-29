/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import android.widget.TwoLineListItem;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;

import com.android.gallery3d.R;
import com.android.gallery3d.common.ApiHelper;

import java.util.ArrayList;

public class GalleryActionBar implements ActionBar.OnNavigationListener {
    @SuppressWarnings("unused")
    private static final String TAG = "GalleryActionBar";

    private ClusterRunner mClusterRunner;
    private CharSequence[] mTitles;
    private CharSequence mTitle;
    private ArrayList<Integer> mActions;
    private Context mContext;
    private LayoutInflater mInflater;
    private AbstractGalleryActivity mActivity;
    private ActionBar mActionBar;
    private int mCurrentIndex;
    private ClusterAdapter mAdapter = new ClusterAdapter();

    private AlbumModeAdapter mAlbumModeAdapter;
    private OnAlbumModeSelectedListener mAlbumModeListener;
    private int mLastAlbumModeSelected;
    private CharSequence [] mAlbumModes;
    public static final int ALBUM_FILMSTRIP_MODE_SELECTED = 0;
    public static final int ALBUM_GRID_MODE_SELECTED = 1;

    public interface ClusterRunner {
        public void doCluster(int id);
    }

    public interface OnAlbumModeSelectedListener {
        public void onAlbumModeSelected(int mode);
    }

    private static class ActionItem {
        public int action;
        public boolean enabled;
        public boolean visible;
        public int spinnerTitle;
        public int dialogTitle;
        public int clusterBy;

        public ActionItem(int action, boolean applied, boolean enabled, int title,
                int clusterBy) {
            this(action, applied, enabled, title, title, clusterBy);
        }

        public ActionItem(int action, boolean applied, boolean enabled, int spinnerTitle,
                int dialogTitle, int clusterBy) {
            this.action = action;
            this.enabled = enabled;
            this.spinnerTitle = spinnerTitle;
            this.dialogTitle = dialogTitle;
            this.clusterBy = clusterBy;
            this.visible = true;
        }
    }

    private static final ActionItem[] sClusterItems = new ActionItem[] {
        new ActionItem(FilterUtils.CLUSTER_BY_ALBUM, true, false, R.string.albums,
                R.string.group_by_album),
        new ActionItem(FilterUtils.CLUSTER_BY_LOCATION, true, false,
                R.string.locations, R.string.location, R.string.group_by_location),
        new ActionItem(FilterUtils.CLUSTER_BY_TIME, true, false, R.string.times,
                R.string.time, R.string.group_by_time),
        new ActionItem(FilterUtils.CLUSTER_BY_TYPE, true, false, R.string.type,
                R.string.types, R.string.group_by_type),
    };

    private class ClusterAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return sClusterItems.length;
        }

        @Override
        public Object getItem(int position) {
            return sClusterItems[position];
        }

        @Override
        public long getItemId(int position) {
            return sClusterItems[position].action;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.action_bar_text,
                        parent, false);
            }
            TextView view = (TextView) convertView;
            view.setText(sClusterItems[position].spinnerTitle);
            return convertView;
        }
    }

    private class AlbumModeAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return mAlbumModes.length;
        }

        @Override
        public Object getItem(int position) {
            return mAlbumModes[position];
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.action_bar_two_line_text,
                        parent, false);
            }
            TwoLineListItem view = (TwoLineListItem) convertView;
            CharSequence title = mActionBar.getTitle();
            view.getText1().setText(title == null ? mTitle : title);
            view.getText2().setText((CharSequence) getItem(position));
            return convertView;
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.action_bar_text,
                        parent, false);
            }
            TextView view = (TextView) convertView;
            view.setText((CharSequence) getItem(position));
            return convertView;
        }
    }

    public static String getClusterByTypeString(Context context, int type) {
        for (ActionItem item : sClusterItems) {
            if (item.action == type) {
                return context.getString(item.clusterBy);
            }
        }
        return null;
    }

    public GalleryActionBar(AbstractGalleryActivity activity) {
        mActionBar = activity.getSupportActionBar();
        mContext = activity.getAndroidContext();
        mActivity = activity;
        mInflater = ((Activity) mActivity).getLayoutInflater();
        mCurrentIndex = 0;
        disableClusterMenu(true);
    }

    private void createDialogData() {
        ArrayList<CharSequence> titles = new ArrayList<CharSequence>();
        mActions = new ArrayList<Integer>();
        for (ActionItem item : sClusterItems) {
            if (item.enabled && item.visible) {
                titles.add(mContext.getString(item.dialogTitle));
                mActions.add(item.action);
            }
        }
        mTitles = new CharSequence[titles.size()];
        titles.toArray(mTitles);
    }

    public int getHeight() {
        return mActionBar != null ? mActionBar.getHeight() : 0;
    }

    public void setClusterItemEnabled(int id, boolean enabled) {
        for (ActionItem item : sClusterItems) {
            if (item.action == id) {
                item.enabled = enabled;
                return;
            }
        }
    }

    public void setClusterItemVisibility(int id, boolean visible) {
        for (ActionItem item : sClusterItems) {
            if (item.action == id) {
                item.visible = visible;
                return;
            }
        }
    }

    public void enableClusterMenu(int action, ClusterRunner runner) {
        if (mActionBar != null) {
            // Don't set cluster runner until action bar is ready.
            mClusterRunner = null;
            mActionBar.setListNavigationCallbacks(mAdapter, this);
            mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
            setSelectedAction(action);
            mClusterRunner = runner;
        }
    }

    // The only use case not to hideMenu in this method is to ensure
    // all elements disappear at the same time when exiting gallery.
    // hideMenu should always be true in all other cases.
    public void disableClusterMenu(boolean hideMenu) {
        if (mActionBar != null) {
            mClusterRunner = null;
            if (hideMenu) {
                mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
            }
        }
    }

    public void showClusterDialog(final ClusterRunner clusterRunner) {
        createDialogData();
        final ArrayList<Integer> actions = mActions;
        new AlertDialog.Builder(mContext).setTitle(R.string.group_by).setItems(
                mTitles, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // Need to lock rendering when operations invoked by system UI (main thread) are
                // modifying slot data used in GL thread for rendering.
                mActivity.getGLRoot().lockRenderThread();
                try {
                    clusterRunner.doCluster(actions.get(which).intValue());
                } finally {
                    mActivity.getGLRoot().unlockRenderThread();
                }
            }
        }).create().show();
    }

    public void setDisplayOptions(boolean displayHomeAsUp, boolean showTitle) {
        if (mActionBar == null) return;
        int options = 0;
        if (displayHomeAsUp) options |= ActionBar.DISPLAY_HOME_AS_UP;
        if (showTitle) options |= ActionBar.DISPLAY_SHOW_TITLE;

        mActionBar.setDisplayOptions(options,
                ActionBar.DISPLAY_HOME_AS_UP | ActionBar.DISPLAY_SHOW_TITLE);
        mActionBar.setHomeButtonEnabled(displayHomeAsUp);
    }

    public void setTitle(String title) {
        mTitle = title;
        if (mActionBar != null) mActionBar.setTitle(title);
    }

    public void setTitle(int titleId) {
        if (mActionBar != null) {
            mTitle = mContext.getString(titleId);
            mActionBar.setTitle(mTitle);
        }
    }

    public void setSubtitle(String title) {
        if (mActionBar != null) mActionBar.setSubtitle(title);
    }

    public void addOnMenuVisibilityListener(ActionBar.OnMenuVisibilityListener listener) {
        if (mActionBar != null) mActionBar.addOnMenuVisibilityListener(listener);
    }

    public void removeOnMenuVisibilityListener(ActionBar.OnMenuVisibilityListener listener) {
        if (mActionBar != null) mActionBar.removeOnMenuVisibilityListener(listener);
    }

    public boolean setSelectedAction(int type) {
        if (mActionBar == null) return false;

        for (int i = 0, n = sClusterItems.length; i < n; i++) {
            ActionItem item = sClusterItems[i];
            if (item.action == type) {
                mActionBar.setSelectedNavigationItem(i);
                mCurrentIndex = i;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onNavigationItemSelected(int itemPosition, long itemId) {
        if (itemPosition != mCurrentIndex && mClusterRunner != null
                || mAlbumModeListener != null) {
            // Need to lock rendering when operations invoked by system UI (main thread) are
            // modifying slot data used in GL thread for rendering.
            mActivity.getGLRoot().lockRenderThread();
            try {
                if (mAlbumModeListener != null) {
                    mAlbumModeListener.onAlbumModeSelected(itemPosition);
                } else {
                    mClusterRunner.doCluster(sClusterItems[itemPosition].action);
                }
            } finally {
                mActivity.getGLRoot().unlockRenderThread();
            }
        }
        return false;
    }

    private Menu mActionBarMenu;
    private Intent mSharePanoramaIntent;
    private Intent mShareIntent;

    public void createActionBarMenu(int menuRes, Menu menu) {
        mActivity.getMenuInflater().inflate(menuRes, menu);
        mActionBarMenu = menu;
    }

    public Menu getMenu() {
        return mActionBarMenu;
    }

    public void setShareIntents(Intent sharePanoramaIntent, Intent shareIntent) {
        mSharePanoramaIntent = sharePanoramaIntent;
        mShareIntent = shareIntent;
    }

    public void setTransparentMode(boolean value) {
        if (value) {
            mActionBar.setBackgroundDrawable(mActivity.getDrawable(R.drawable.root_top_bg));
        } else {
            mActionBar.setBackgroundDrawable(
                    new ColorDrawable(getAttrColor(android.R.attr.colorBackground)));
        }
    }
    
    private int getAttrColor(Integer attr) {
        TypedArray ta = mActivity.obtainStyledAttributes(new int[]{attr});
        int color = ta.getColor(0, 0);
        ta.recycle();
        return color;
    }
}
