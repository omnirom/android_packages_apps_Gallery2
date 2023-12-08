/*
 * Copyright (c) 2014, The Linux Foundation. All rights reserved.
 * Not a Contribution.
 *
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
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.view.HapticFeedbackConstants;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.core.view.MenuItemCompat;
import androidx.appcompat.widget.SearchView;

import com.android.gallery3d.R;
import com.android.gallery3d.app.GallerySettings;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.DataManager;
import com.android.gallery3d.data.MediaDetails;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.data.MediaObject;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.glrenderer.FadeTexture;
import com.android.gallery3d.glrenderer.GLCanvas;
import com.android.gallery3d.ui.ActionModeHandler;
import com.android.gallery3d.ui.AlbumSetSlotRenderer;
import com.android.gallery3d.ui.GLRoot;
import com.android.gallery3d.ui.GLView;
import com.android.gallery3d.ui.SelectionManager;
import com.android.gallery3d.ui.SlotView;
import com.android.gallery3d.ui.SynchronizedHandler;
import com.android.gallery3d.util.Future;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.util.HelpUtils;
import com.android.gallery3d.util.Log;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

public class AlbumSetPage extends ActivityState implements
        SelectionManager.SelectionListener, GalleryActionBar.ClusterRunner,
        AlbumSetPageBottomControls.Delegate {
    @SuppressWarnings("unused")
    private static final String TAG = "AlbumSetPage";
    private static final boolean DEBUG = false;

    private static final int MSG_PICK_ALBUM = 1;

    public static final String KEY_MEDIA_PATH = "media-path";
    public static final String KEY_SELECTED_CLUSTER_TYPE = "selected-cluster";

    private static final int DATA_CACHE_SIZE = 256;
    private static final int REQUEST_SETTINGS = 2;
    private static final float USER_DISTANCE_METER = 0.3f;

    private boolean mIsActive = false;
    private SlotView mSlotView;
    private AlbumSetSlotRenderer mAlbumSetView;
    private Config.AlbumSetPage mConfig;

    private MediaSet mMediaSet;
    private String mMediaPath;
    private GalleryActionBar mActionBar;
    private int mSelectedAction;

    protected SelectionManager mSelectionManager;
    private AlbumSetDataLoader mAlbumSetDataAdapter;

    private boolean mGetContent;
    private boolean mGetAlbum;
    private ActionModeHandler mActionModeHandler;
    private Handler mHandler;
    private AlbumSetPageBottomControls mBottomControls;
    private float mUserDistance; // in pixel
    private MenuItem mSearchItem;

    @Override
    protected int getBackgroundColorId() {
        return R.color.albumset_background;
    }

    private final GLView mRootPane = new GLView() {
        private final float mMatrix[] = new float[16];

        @Override
        protected void onLayout(
                boolean changed, int left, int top, int right, int bottom) {

            int slotViewTop = mTopMargin + mConfig.paddingTop;
            int slotViewBottom = bottom - top - mBottomMargin - mConfig.paddingBottom;
            int slotViewRight = right - left - mConfig.paddingRight;

            mAlbumSetView.setHighlightItemPath(null);

            mSlotView.layout(mConfig.paddingLeft, slotViewTop, slotViewRight, slotViewBottom);
            GalleryUtils.setViewPointMatrix(mMatrix,
                    (right - left) / 2, (bottom - top) / 2, -mUserDistance);
        }

        @Override
        protected void render(GLCanvas canvas) {
            canvas.save(GLCanvas.SAVE_FLAG_MATRIX);
            canvas.multiplyMatrix(mMatrix, 0);
            super.render(canvas);
            canvas.restore();
        }
    };

    @Override
    public void onBackPressed() {
        if (mSelectionManager.inSelectionMode()) {
            mSelectionManager.leaveSelectionMode();
        } else {
            super.onBackPressed();
        }
    }

    private void getSlotCenter(int slotIndex, int center[]) {
        Rect offset = new Rect();
        mRootPane.getBoundsOf(mSlotView, offset);
        Rect r = mSlotView.getSlotRect(slotIndex);
        int scrollX = mSlotView.getScrollX();
        int scrollY = mSlotView.getScrollY();
        center[0] = offset.left + (r.left + r.right) / 2 - scrollX;
        center[1] = offset.top + (r.top + r.bottom) / 2 - scrollY;
    }

    public void onSingleTapUp(int slotIndex) {
        if (!mIsActive) return;

        if (mSelectionManager.inSelectionMode()) {
            MediaSet targetSet = mAlbumSetDataAdapter.getMediaSet(slotIndex);
            if (targetSet == null) return; // Content is dirty, we shall reload soon
            mSelectionManager.toggle(targetSet.getPath());
            mSlotView.invalidate();
        } else {
            // Show pressed-up animation for the single-tap.
            mAlbumSetView.setPressedIndex(slotIndex);
            mAlbumSetView.setPressedUp();
            mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_PICK_ALBUM, slotIndex, 0),
                    FadeTexture.DURATION);
        }
    }

    private boolean albumShouldOpenInFilmstrip(MediaSet album) {
        return GalleryUtils.getAlbumMode(mActivity) == 1;
    }

    private boolean albumShouldOpenInSinglePhotoPage(MediaSet album) {
        int itemCount = album.getMediaItemCount();
        ArrayList<MediaItem> list = (itemCount == 1) ? album.getMediaItem(0, 1) : null;
        // open in film strip only if there's one item in the album and the item exists
        return (list != null && !list.isEmpty());
    }

    private void pickAlbum(int slotIndex) {
        if (!mIsActive) return;

        MediaSet targetSet = mAlbumSetDataAdapter.getMediaSet(slotIndex);
        if (targetSet == null) return; // Content is dirty, we shall reload soon
        if (targetSet.getTotalMediaItemCount() == 0) {
            return;
        }

        String mediaPath = targetSet.getPath().toString();

        Bundle data = new Bundle(getData());
        int[] center = new int[2];
        getSlotCenter(slotIndex, center);
        data.putIntArray(AlbumPage.KEY_SET_CENTER, center);
        if (mGetAlbum && targetSet.isLeafAlbum()) {
            Activity activity = mActivity;
            Intent result = new Intent()
                    .putExtra(AlbumPicker.KEY_ALBUM_PATH, targetSet.getPath().toString())
                    .putExtra(AlbumPicker.KEY_ALBUM_NAME, targetSet.getName());
            activity.setResult(Activity.RESULT_OK, result);
            activity.finish();
        } else if (targetSet.getSubMediaSetCount() > 0) {
            data.putString(AlbumSetPage.KEY_MEDIA_PATH, mediaPath);
            mActivity.getStateManager().startState(AlbumSetPage.class, data);
        } else {
            if (!mGetContent && albumShouldOpenInSinglePhotoPage(targetSet)) {
                data.putParcelable(PhotoPage.KEY_OPEN_ANIMATION_RECT,
                        mSlotView.getSlotRect(slotIndex, mRootPane));
                data.putInt(PhotoPage.KEY_INDEX_HINT, 0);
                data.putString(PhotoPage.KEY_MEDIA_SET_PATH,
                        mediaPath);
                data.putBoolean(PhotoPage.KEY_IN_CAMERA_ROLL, targetSet.isCameraRoll());
                mActivity.getStateManager().startStateForResult(
                        PhotoPage.class, AlbumPage.REQUEST_PHOTO, data);
                return;
            }
            if (!mGetContent && albumShouldOpenInFilmstrip(targetSet)) {
                data.putParcelable(PhotoPage.KEY_OPEN_ANIMATION_RECT,
                        mSlotView.getSlotRect(slotIndex, mRootPane));
                data.putString(PhotoPage.KEY_MEDIA_SET_PATH,
                        mediaPath);
                data.putBoolean(PhotoPage.KEY_START_IN_FILMSTRIP, true);
                data.putBoolean(PhotoPage.KEY_IN_CAMERA_ROLL, targetSet.isCameraRoll());
                mActivity.getStateManager().startStateForResult(
                        FilmstripPage.class, AlbumPage.REQUEST_PHOTO, data);
                return;
            }
            data.putString(AlbumPage.KEY_MEDIA_PATH, mediaPath);

            mActivity.getStateManager().startState(AlbumPage.class, data);
        }
    }

    private void onDown(int index) {
        mAlbumSetView.setPressedIndex(index);
    }

    private void onUp(boolean followedByLongPress) {
        if (followedByLongPress) {
            // Avoid showing press-up animations for long-press.
            mAlbumSetView.setPressedIndex(-1);
        } else {
            mAlbumSetView.setPressedUp();
        }
    }

    public void onLongTap(int slotIndex) {
        if (mGetContent || mGetAlbum) return;
        MediaSet set = mAlbumSetDataAdapter.getMediaSet(slotIndex);
        if (set == null) return;
        mSelectionManager.setAutoLeaveSelectionMode(true);
        mSelectionManager.toggle(set.getPath());
        mSlotView.invalidate();
    }

    private void doRunClusterAction(int clusterType) {
        String basePath = mMediaSet.getPath().toString();
        String newPath = FilterUtils.switchClusterPath(basePath, clusterType);
        if (DEBUG) Log.d(TAG, "doRunClusterAction newPath = " + newPath);

        Bundle data = new Bundle(getData());
        data.putString(AlbumSetPage.KEY_MEDIA_PATH, newPath);
        data.putInt(AlbumSetPage.KEY_SELECTED_CLUSTER_TYPE, clusterType);
        mActivity.getStateManager().switchState(this, AlbumSetPage.class, data);
    }

    @Override
    public void doCluster(final int clusterType) {
        // noop
        if (mSelectedAction == clusterType) {
            return;
        }
        mSelectionManager.leaveSelectionMode();
        doRunClusterAction(clusterType);
    }

    @Override
    public void onCreate(Bundle data, Bundle restoreState) {
        super.onCreate(data, restoreState);
        if (DEBUG) Log.d(TAG, "onCreate " + this);

        initializeViews();
        initializeData(data);
        Context context = mActivity.getAndroidContext();
        mGetContent = data.getBoolean(GalleryActivity.KEY_GET_CONTENT, false);
        mGetAlbum = data.getBoolean(GalleryActivity.KEY_GET_ALBUM, false);
        mActionBar = mActivity.getGalleryActionBar();
        mUserDistance = GalleryUtils.meterToPixel(USER_DISTANCE_METER);

        mHandler = new SynchronizedHandler(mActivity.getGLRoot()) {
            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case MSG_PICK_ALBUM: {
                        pickAlbum(message.arg1);
                        break;
                    }
                    default: throw new AssertionError(message.what);
                }
            }
        };
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (DEBUG) Log.d(TAG, "onDestroy " + this);

        mActionModeHandler.destroy();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (DEBUG) Log.d(TAG, "onPause " + this);

        mIsActive = false;
        mSearchItem.collapseActionView();
        mAlbumSetDataAdapter.pause();
        mAlbumSetView.pause();
        mActionModeHandler.pause();
        GalleryUtils.setAlbumsetZoomLevel(mActivity, mSlotView.getZoomLevel());
        mBottomControls.hide(false);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (DEBUG) Log.d(TAG, "onResume " + this);

        mIsActive = true;
        mActionBar.setTransparentMode(false);
        mActivity.setSystemBarsTranlucent(false);
        mActivity.showSystemBars(false);

        setContentPane(mRootPane);
        mActivity.getGLRootView().applySystemInsets();
        mActivity.setBottomControlMargin(true);

        mAlbumSetDataAdapter.resume();

        mAlbumSetView.resume();
        mActionModeHandler.resume();
        
        RelativeLayout galleryRoot = (RelativeLayout) mActivity.findViewById(R.id.gallery_root);
        mBottomControls = new AlbumSetPageBottomControls(this, mActivity, galleryRoot);
        mBottomControls.showItemWithId(R.id.albumpage_bottom_control_album, mActivity.getStateManager().getStateCount() == 1);
        mBottomControls.show(false);
        selectActiveBottomControl();
   }

    private void initializeData(Bundle data) {
        mMediaPath = data.getString(AlbumSetPage.KEY_MEDIA_PATH);
        mSelectedAction = data.getInt(AlbumSetPage.KEY_SELECTED_CLUSTER_TYPE,
                FilterUtils.CLUSTER_BY_ALBUM);
        mMediaSet = mActivity.getDataManager().getMediaSet(mMediaPath);
        if (DEBUG)  Log.d(TAG, "initializeData mMediaPath = " + mMediaPath + " mSelectedAction = " + mSelectedAction+ " " + this);

        mSelectionManager.setSourceMediaSet(mMediaSet);
        mAlbumSetDataAdapter = new AlbumSetDataLoader(mMediaSet, DATA_CACHE_SIZE);
        mAlbumSetDataAdapter.setGLMainHandler(mActivity);
        mAlbumSetDataAdapter.setLoadingListener(new MyLoadingListener());
        mAlbumSetView.setModel(mAlbumSetDataAdapter);
    }

    private void updateSearchQuery(String query) {
        if (!TextUtils.isEmpty(query)) {
            String mediaPath = FilterUtils.newFilterPathPath(mMediaPath, query);
            mMediaSet = mActivity.getDataManager().getMediaSet(mediaPath);
        } else {
            mMediaSet = mActivity.getDataManager().getMediaSet(mMediaPath);
        }
        mSelectionManager.setSourceMediaSet(mMediaSet);
        mAlbumSetDataAdapter.pause();
        mAlbumSetDataAdapter.updateMediaSet(mMediaSet);
        mAlbumSetDataAdapter.resume();
    }

    private void initializeViews() {
        mSelectionManager = new SelectionManager(mActivity, true);
        mSelectionManager.setSelectionListener(this);

        mConfig = new Config.AlbumSetPage(mActivity);
        mSlotView = new SlotView(mActivity, mConfig.slotViewSpec);
        mSlotView.setOverscrollEffect(SlotView.OVERSCROLL_SYSTEM);
        mAlbumSetView = new AlbumSetSlotRenderer(
                mActivity, mSelectionManager, mSlotView, mConfig.labelSpec,
                mConfig.placeholderColor);
        mSlotView.setSlotRenderer(mAlbumSetView);
        mSlotView.setListener(new SlotView.SimpleListener() {
            @Override
            public void onDown(int index) {
                AlbumSetPage.this.onDown(index);
            }

            @Override
            public void onUp(boolean followedByLongPress) {
                AlbumSetPage.this.onUp(followedByLongPress);
            }

            @Override
            public void onSingleTapUp(int slotIndex) {
                AlbumSetPage.this.onSingleTapUp(slotIndex);
            }

            @Override
            public void onLongTap(int slotIndex) {
                AlbumSetPage.this.onLongTap(slotIndex);
            }
        });

        mActionModeHandler = new ActionModeHandler(mActivity, mSelectionManager);
        mRootPane.addComponent(mSlotView);
    }

    @Override
    protected boolean onCreateActionBar(Menu menu) {
        Activity activity = mActivity;
        final boolean inAlbum = mActivity.getStateManager().hasStateClass(AlbumPage.class);
        MenuInflater inflater = getSupportMenuInflater();
        inflater.inflate(R.menu.albumset, menu);

        boolean selectAlbums = !inAlbum &&
                mSelectedAction == FilterUtils.CLUSTER_BY_ALBUM;
        MenuItem selectItem = menu.findItem(R.id.action_select);
        selectItem.setTitle(activity.getString(
                selectAlbums ? R.string.select_album : R.string.select_group));

        MenuItem cameraItem = menu.findItem(R.id.action_camera);
        cameraItem.setVisible(GalleryUtils.isAnyCameraAvailable(activity) && selectAlbums);

        mSearchItem = menu.findItem(R.id.action_search);
        FilterUtils.setupMenuItems(mActionBar, mMediaSet.getPath(), false);

        setActiveActionBarTitle();

        return true;
    }

    @Override
    protected boolean onItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home: {
                mActivity.getStateManager().finishState(this);
                return true;
            }
            case R.id.action_cancel:
                mActivity.setResult(Activity.RESULT_CANCELED);
                mActivity.finish();
                return true;
            case R.id.action_select:
                mSelectionManager.setAutoLeaveSelectionMode(false);
                mSelectionManager.enterSelectionMode();
                return true;
            case R.id.action_camera: {
                GalleryUtils.startCameraActivity(mActivity);
                return true;
            }
            case R.id.action_settings: {
                Intent intent = new Intent(mActivity, GallerySettings.class);
                mActivity.startActivityForResult(intent, REQUEST_SETTINGS);
                return true;
            }
            case R.id.action_slideshow: {
                Bundle data = new Bundle();
                data.putString(SlideshowPage.KEY_SET_PATH, mMediaSet.getPath().toString());
                Intent intent = new Intent(mActivity, SlideshowActivity.class);
                intent.putExtras(data);
                mActivity.startActivity(intent);
                return true;
            }
            case R.id.action_search: {
                SearchView searchView = (SearchView) MenuItemCompat.getActionView(item);

                EditText searchPlate = (EditText) searchView.findViewById(androidx.appcompat.R.id.search_src_text);
                searchPlate.setHint(R.string.menu_search);

                searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                    @Override
                    public boolean onQueryTextSubmit(String query) {
                        return false;
                    }
                    @Override
                    public boolean onQueryTextChange(String query) {
                        updateSearchQuery(query);
                        return false;
                    }
                });
            }
            default:
                return false;
        }
    }

    @Override
    protected void onStateResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_SETTINGS:
                mMediaSet.reloadClustering();
                doCluster(mSelectedAction);
                break;
        }
    }

    private String getSelectedString() {
        int count = mSelectionManager.getSelectedCount();
        int action = mSelectedAction;
        int string = action == FilterUtils.CLUSTER_BY_ALBUM
                ? R.plurals.number_of_albums_selected
                : R.plurals.number_of_groups_selected;
        String format = mActivity.getResources().getQuantityString(string, count);
        return String.format(format, count);
    }

    @Override
    public void onSelectionModeChange(int mode) {
        switch (mode) {
            case SelectionManager.ENTER_SELECTION_MODE: {
                mActionBar.disableClusterMenu(true);
                mActionModeHandler.startActionMode();
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                break;
            }
            case SelectionManager.LEAVE_SELECTION_MODE: {
                mActionModeHandler.finishActionMode();
                mRootPane.invalidate();
                break;
            }
            case SelectionManager.SELECT_ALL_MODE: {
                mActionModeHandler.updateSupportedOperation();
                mRootPane.invalidate();
                break;
            }
        }
    }

    @Override
    public void onSelectionChange(Path path, boolean selected) {
        mActionModeHandler.setTitle(getSelectedString());
        mActionModeHandler.updateSupportedOperation(path, selected);
    }

    @Override
    public void onBottomControlClicked(int control) {
        switch(control) {
            case R.id.albumpage_bottom_control_album:
                doCluster(FilterUtils.CLUSTER_BY_ALBUM);
                break;
            case R.id.albumpage_bottom_control_location:
                doCluster(FilterUtils.CLUSTER_BY_LOCATION);
                break;
            case R.id.albumpage_bottom_control_times:
                doCluster(FilterUtils.CLUSTER_BY_TIME);
                break;
            case R.id.albumpage_bottom_control_type:
                doCluster(FilterUtils.CLUSTER_BY_TYPE);
                break;
        }
    }

    private void selectActiveBottomControl() {
        switch(mSelectedAction) {
            case FilterUtils.CLUSTER_BY_ALBUM:
                mBottomControls.selectItemWithId(R.id.albumpage_bottom_control_album);
                break;
            case FilterUtils.CLUSTER_BY_LOCATION:
                mBottomControls.selectItemWithId(R.id.albumpage_bottom_control_location);
                break;
            case FilterUtils.CLUSTER_BY_TIME:
                mBottomControls.selectItemWithId(R.id.albumpage_bottom_control_times);
                break;
            case FilterUtils.CLUSTER_BY_TYPE:
                mBottomControls.selectItemWithId(R.id.albumpage_bottom_control_type);
                break;
        }
    }

    private void setActiveActionBarTitle() {
        switch(mSelectedAction) {
            case FilterUtils.CLUSTER_BY_ALBUM:
                mActionBar.setTitle(R.string.albums);
                break;
            case FilterUtils.CLUSTER_BY_LOCATION:
                mActionBar.setTitle(R.string.locations);
                break;
            case FilterUtils.CLUSTER_BY_TIME:
                mActionBar.setTitle(R.string.times);
                break;
            case FilterUtils.CLUSTER_BY_TYPE:
                mActionBar.setTitle(R.string.types);
                break;
        }
    }

    private class MyLoadingListener implements LoadingListener {
        @Override
        public void onLoadingStarted() {
        }

        @Override
        public void onLoadingFinished() {
        }
    }
}
