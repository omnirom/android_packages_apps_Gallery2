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

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import android.view.ActionMode;
import android.view.ActionMode.Callback;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AlertDialog;

import com.android.gallery3d.R;
import com.android.gallery3d.app.AbstractGalleryActivity;
import com.android.gallery3d.common.ApiHelper;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.DataManager;
import com.android.gallery3d.data.MediaObject;
import com.android.gallery3d.data.MediaObject.PanoramaSupportCallback;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.util.Future;
import com.android.gallery3d.util.GalleryUtils;
import com.android.gallery3d.util.ThreadPool.Job;
import com.android.gallery3d.util.ThreadPool.JobContext;

import java.util.ArrayList;

public class ActionModeHandler implements Callback, PopupList.OnPopupItemClickListener {

    @SuppressWarnings("unused")
    private static final String TAG = "ActionModeHandler";

    private static final int MAX_SELECTED_ITEMS_FOR_SHARE_INTENT = 300;
    private static final int MAX_SELECTED_ITEMS_FOR_PANORAMA_SHARE_INTENT = 10;

    private static final int SUPPORT_MULTIPLE_MASK = MediaObject.SUPPORT_DELETE
            | MediaObject.SUPPORT_ROTATE | MediaObject.SUPPORT_SHARE
            | MediaObject.SUPPORT_CACHE;

    private final AbstractGalleryActivity mActivity;
    private final SelectionManager mSelectionManager;
    private Menu mMenu;
    private MenuItem mSharePanoramaMenuItem;
    private MenuItem mShareMenuItem;
    private MenuItem mDeleteMenuItem;
    private SelectionMenu mSelectionMenu;
    private Future<?> mMenuTask;
    private final Handler mMainHandler;
    private ActionMode mActionMode;
    private Intent mShareIntent;
    private Intent mSharePanoramaIntent;
    private ProgressDialog mDeleteProgress;

    private static class GetAllPanoramaSupports implements PanoramaSupportCallback {
        private int mNumInfoRequired;
        private JobContext mJobContext;
        public boolean mAllPanoramas = true;
        public boolean mAllPanorama360 = true;
        public boolean mHasPanorama360 = false;
        private Object mLock = new Object();

        public GetAllPanoramaSupports(ArrayList<MediaObject> mediaObjects, JobContext jc) {
            mJobContext = jc;
            mNumInfoRequired = mediaObjects.size();
            for (MediaObject mediaObject : mediaObjects) {
                mediaObject.getPanoramaSupport(this);
            }
        }

        @Override
        public void panoramaInfoAvailable(MediaObject mediaObject, boolean isPanorama,
                                          boolean isPanorama360) {
            synchronized (mLock) {
                mNumInfoRequired--;
                mAllPanoramas = isPanorama && mAllPanoramas;
                mAllPanorama360 = isPanorama360 && mAllPanorama360;
                mHasPanorama360 = mHasPanorama360 || isPanorama360;
                if (mNumInfoRequired == 0 || mJobContext.isCancelled()) {
                    mLock.notifyAll();
                }
            }
        }

        public void waitForPanoramaSupport() {
            synchronized (mLock) {
                while (mNumInfoRequired != 0 && !mJobContext.isCancelled()) {
                    try {
                        mLock.wait();
                    } catch (InterruptedException e) {
                        // May be a cancelled job context
                    }
                }
            }
        }
    }

    public ActionModeHandler(
            AbstractGalleryActivity activity, SelectionManager selectionManager) {
        mActivity = Utils.checkNotNull(activity);
        mSelectionManager = Utils.checkNotNull(selectionManager);
        mMainHandler = new Handler(activity.getMainLooper());
    }

    public void startActionMode() {
        Activity a = mActivity;
        mActionMode = a.startActionMode(this);
        View customView = LayoutInflater.from(a).inflate(
                R.layout.action_mode, null);
        mActionMode.setCustomView(customView);
        mSelectionMenu = new SelectionMenu(a,
                (Button) customView.findViewById(R.id.selection_menu), this);
        updateSelectionMenu();
    }

    public void finishActionMode() {
        mActionMode.finish();
    }

    public void setTitle(String title) {
        mSelectionMenu.setTitle(title);
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        return true;
    }

    @Override
    public boolean onPopupItemClick(int itemId) {
        GLRoot root = mActivity.getGLRoot();
        root.lockRenderThread();
        try {
            if (itemId == R.id.action_select_all) {
                updateSupportedOperation();
                mSelectionManager.selectAll();
            }
            return true;
        } finally {
            root.unlockRenderThread();
        }
    }

    private void updateSelectionMenu() {
        // update title
        int count = mSelectionManager.getSelectedCount();
        String format = mActivity.getResources().getQuantityString(
                R.plurals.number_of_items_selected, count);
        setTitle(String.format(format, count));

        // For clients who call SelectionManager.selectAll() directly, we need to ensure the
        // menu status is consistent with selection manager.
        mSelectionMenu.updateSelectAllMode(mSelectionManager.inSelectAllMode());
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        return false;
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        mode.getMenuInflater().inflate(R.menu.operation, menu);

        mMenu = menu;
        mSharePanoramaMenuItem = menu.findItem(R.id.action_share_panorama_selected);
        if (mSharePanoramaMenuItem != null) {
            mSharePanoramaMenuItem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    mSelectionManager.leaveSelectionMode();
                    if (mSharePanoramaIntent != null) {
                        Intent intent = Intent.createChooser(mSharePanoramaIntent, null);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        mActivity.startActivity(intent);
                    }
                    return true;
                }
            });
        }

        mShareMenuItem = menu.findItem(R.id.action_share_selected);
        if (mShareMenuItem != null) {
            mShareMenuItem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    mSelectionManager.leaveSelectionMode();
                    if (mShareIntent != null) {
                        Intent intent = Intent.createChooser(mShareIntent, null);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        mActivity.startActivity(intent);
                    }
                    return true;
                }
            });
        }

        mDeleteMenuItem = menu.findItem(R.id.action_delete_selected);
        if (mDeleteMenuItem != null) {
            mDeleteMenuItem.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    ArrayList<Path> ids = mSelectionManager.getSelected(true);
                    String format = mActivity.getResources().getQuantityString(
                            R.plurals.number_of_items_selected, ids.size());
                    String confirmMsg = mActivity.getResources().getString(R.string.delete) + " " +
                            String.format(format, ids.size()) + "?";
                    AlertDialog delete = new AlertDialog.Builder(mActivity)
                            .setMessage(confirmMsg)
                            .setPositiveButton(android.R.string.ok,
                                    (dialogInterface, i) -> {
                                        showProgressDialog();
                                        final DataManager manager = mActivity.getDataManager();

                                        new Thread(new Runnable() {
                                            @Override
                                            public void run() {
                                                try {
                                                    for (Path id : ids) {
                                                        manager.delete(id);
                                                    }
                                                } catch (Exception e) {
                                                    e.printStackTrace();
                                                }

                                                mMainHandler.post(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        mSelectionManager.leaveSelectionMode();
                                                        hideProgressDialog();
                                                    }
                                                });
                                            }
                                        }).start();
                                    })
                            .setNegativeButton(android.R.string.cancel, null)
                            .create();
                    delete.show();
                    return true;
                }
            });
        }
        return true;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        mSelectionManager.leaveSelectionMode();
    }

    private ArrayList<MediaObject> getSelectedMediaObjects(JobContext jc) {
        ArrayList<Path> unexpandedPaths = mSelectionManager.getSelected(false);
        if (unexpandedPaths.isEmpty()) {
            // This happens when starting selection mode from overflow menu
            // (instead of long press a media object)
            return null;
        }
        ArrayList<MediaObject> selected = new ArrayList<MediaObject>();
        DataManager manager = mActivity.getDataManager();
        for (Path path : unexpandedPaths) {
            if (jc.isCancelled()) {
                return null;
            }
            selected.add(manager.getMediaObject(path));
        }

        return selected;
    }

    // Menu options are determined by selection set itself.
    // We cannot expand it because MenuExecuter executes it based on
    // the selection set instead of the expanded result.
    // e.g. LocalImage can be rotated but collections of them (LocalAlbum) can't.
    private int computeMenuOptions(ArrayList<MediaObject> selected) {
        int operation = MediaObject.SUPPORT_ALL;
        int type = 0;
        for (MediaObject mediaObject : selected) {
            int support = mediaObject.getSupportedOperations();
            type |= mediaObject.getMediaType();
            operation &= support;
        }

        switch (selected.size()) {
            case 1:
                final String mimeType = getMimeType(type);
                if (!GalleryUtils.isEditorAvailable(mActivity, mimeType)) {
                    operation &= ~MediaObject.SUPPORT_EDIT;
                }
                break;
            default:
                operation &= SUPPORT_MULTIPLE_MASK;
        }

        return operation;
    }

    // Share intent needs to expand the selection set so we can get URI of
    // each media item
    private Intent computePanoramaSharingIntent(JobContext jc, int maxItems) {
        ArrayList<Path> expandedPaths = mSelectionManager.getSelected(true, maxItems);
        if (expandedPaths == null || expandedPaths.size() == 0) {
            return new Intent();
        }
        final ArrayList<Uri> uris = new ArrayList<Uri>();
        DataManager manager = mActivity.getDataManager();
        final Intent intent = new Intent();
        for (Path path : expandedPaths) {
            if (jc.isCancelled()) return null;
            uris.add(manager.getContentUri(path));
        }

        final int size = uris.size();
        if (size > 0) {
            if (size > 1) {
                intent.setAction(Intent.ACTION_SEND_MULTIPLE);
                intent.setType(GalleryUtils.MIME_TYPE_PANORAMA360);
                intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            } else {
                intent.setAction(Intent.ACTION_SEND);
                intent.setType(GalleryUtils.MIME_TYPE_PANORAMA360);
                intent.putExtra(Intent.EXTRA_STREAM, uris.get(0));
            }
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }

        return intent;
    }

    private Intent computeSharingIntent(JobContext jc, int maxItems) {
        ArrayList<Path> expandedPaths = mSelectionManager.getSelected(true, maxItems);
        if (expandedPaths == null || expandedPaths.size() == 0) {
            return new Intent();
        }
        final ArrayList<Uri> uris = new ArrayList<Uri>();
        DataManager manager = mActivity.getDataManager();
        int type = 0;
        final Intent intent = new Intent();
        for (Path path : expandedPaths) {
            if (jc.isCancelled()) return null;
            int support = manager.getSupportedOperations(path);
            type |= manager.getMediaType(path);

            if ((support & MediaObject.SUPPORT_SHARE) != 0) {
                uris.add(manager.getContentUri(path));
            }
        }

        final int size = uris.size();
        if (size > 0) {
            final String mimeType = getMimeType(type);
            if (size > 1) {
                intent.setAction(Intent.ACTION_SEND_MULTIPLE).setType(mimeType);
                intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            } else {
                intent.setAction(Intent.ACTION_SEND).setType(mimeType);
                intent.putExtra(Intent.EXTRA_STREAM, uris.get(0));
            }
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }

        return intent;
    }

    public void updateSupportedOperation(Path path, boolean selected) {
        // TODO: We need to improve the performance
        updateSupportedOperation();
    }

    public void updateSupportedOperation() {
        // Interrupt previous unfinished task, mMenuTask is only accessed in main thread
        if (mMenuTask != null) mMenuTask.cancel();

        updateSelectionMenu();

        // Disable share actions until share intent is in good shape
        if (mSharePanoramaMenuItem != null) mSharePanoramaMenuItem.setEnabled(false);
        if (mShareMenuItem != null) mShareMenuItem.setEnabled(false);

        // Generate sharing intent and update supported operations in the background
        // The task can take a long time and be canceled in the mean time.
        mMenuTask = mActivity.getThreadPool().submit(new Job<Void>() {
            @Override
            public Void run(final JobContext jc) {
                // Pass1: Deal with unexpanded media object list for menu operation.
                ArrayList<MediaObject> selected = getSelectedMediaObjects(jc);
                if (selected == null) {
                    mMainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mMenuTask = null;
                            if (jc.isCancelled()) return;
                        }
                    });
                    return null;
                }
                final int operation = computeMenuOptions(selected);
                if (jc.isCancelled()) {
                    return null;
                }
                int numSelected = selected.size();
                final boolean canSharePanoramas =
                        numSelected < MAX_SELECTED_ITEMS_FOR_PANORAMA_SHARE_INTENT;
                final boolean canShare =
                        numSelected < MAX_SELECTED_ITEMS_FOR_SHARE_INTENT;
                final boolean canDelete = true;

                final GetAllPanoramaSupports supportCallback = canSharePanoramas ?
                        new GetAllPanoramaSupports(selected, jc)
                        : null;

                // Pass2: Deal with expanded media object list for sharing operation.
                final Intent share_panorama_intent = canSharePanoramas ?
                        computePanoramaSharingIntent(jc, MAX_SELECTED_ITEMS_FOR_PANORAMA_SHARE_INTENT)
                        : new Intent();
                final Intent share_intent = canShare ?
                        computeSharingIntent(jc, MAX_SELECTED_ITEMS_FOR_SHARE_INTENT)
                        : new Intent();

                if (canSharePanoramas) {
                    supportCallback.waitForPanoramaSupport();
                }
                if (jc.isCancelled()) {
                    return null;
                }
                mMainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mMenuTask = null;
                        if (jc.isCancelled()) return;
                        if (mSharePanoramaMenuItem != null) {
                            mSharePanoramaMenuItem.setVisible(canSharePanoramas);
                            mSharePanoramaMenuItem.setEnabled(canSharePanoramas);
                            if (canSharePanoramas && supportCallback.mAllPanorama360) {
                                mShareMenuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
                                mShareMenuItem.setTitle(
                                        mActivity.getResources().getString(R.string.share_as_photo));
                            } else {
                                mSharePanoramaMenuItem.setVisible(false);
                                mShareMenuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
                                mShareMenuItem.setTitle(
                                        mActivity.getResources().getString(R.string.share));
                            }
                            mSharePanoramaIntent = share_panorama_intent;
                        }
                        if (mShareMenuItem != null) {
                            mShareMenuItem.setVisible(canShare);
                            mShareMenuItem.setEnabled(canShare);
                            mShareIntent = share_intent;
                        }
                        if (mDeleteMenuItem != null) {
                            mDeleteMenuItem.setEnabled(canDelete);
                            mDeleteMenuItem.setVisible(canDelete);
                        }
                    }
                });
                return null;
            }
        });
    }

    public void pause() {
        if (mMenuTask != null) {
            mMenuTask.cancel();
            mMenuTask = null;
        }
    }

    public void destroy() {
    }

    public void resume() {
        if (mSelectionManager.inSelectionMode()) updateSupportedOperation();
    }

    private String getMimeType(int type) {
        switch (type) {
            case MediaObject.MEDIA_TYPE_IMAGE:
                return GalleryUtils.MIME_TYPE_IMAGE;
            case MediaObject.MEDIA_TYPE_VIDEO:
                return GalleryUtils.MIME_TYPE_VIDEO;
            default:
                return GalleryUtils.MIME_TYPE_ALL;
        }
    }

    private void showProgressDialog() {
        mDeleteProgress = new ProgressDialog(mActivity);
        mDeleteProgress.setTitle(mActivity.getString(R.string.delete));
        mDeleteProgress.setMessage(mActivity.getString(R.string.please_wait));
        mDeleteProgress.setCancelable(false);
        mDeleteProgress.setCanceledOnTouchOutside(false);
        mDeleteProgress.show();
    }

    private void hideProgressDialog() {
        if (mDeleteProgress != null) {
            mDeleteProgress.dismiss();
            mDeleteProgress = null;
        }
    }
}
