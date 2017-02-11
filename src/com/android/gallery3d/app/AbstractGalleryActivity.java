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

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.print.PrintHelper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ProgressBar;

import com.android.gallery3d.R;
import com.android.gallery3d.common.ApiHelper;
import com.android.gallery3d.data.DataManager;
import com.android.gallery3d.data.MediaItem;
import com.android.gallery3d.filtershow.cache.ImageLoader;
import com.android.gallery3d.ui.GLRoot;
import com.android.gallery3d.ui.GLRootView;
import com.android.gallery3d.util.MediaSetUtils;
import com.android.gallery3d.util.PanoramaViewHelper;
import com.android.gallery3d.util.ThreadPool;
import com.android.photos.data.GalleryBitmapPool;

import java.io.FileNotFoundException;
import java.util.ArrayList;

public class AbstractGalleryActivity extends Activity implements GalleryContext {
    private static final String TAG = "AbstractGalleryActivity";
    private static final int PERMISSION_REQUEST_LOCATION = 2;

    private GLRootView mGLRootView;
    private StateManager mStateManager;
    private GalleryActionBar mActionBar;
    private TransitionStore mTransitionStore = new TransitionStore();
    private PanoramaViewHelper mPanoramaViewHelper;
    private ProgressBar mProgress;
    private AlertDialog mAlertDialog = null;
    private BroadcastReceiver mMountReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (getExternalCacheDir() != null) onStorageReady();
        }
    };
    private IntentFilter mMountFilter = new IntentFilter(Intent.ACTION_MEDIA_MOUNTED);
    private Runnable mRunWithPermission;
    private Runnable mRunWithoutPermission;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStoragePath();
        getWindow().setBackgroundDrawable(null);
        mPanoramaViewHelper = new PanoramaViewHelper(this);
        mPanoramaViewHelper.onCreate();
        doBindBatchService();
    }

    private void setStoragePath() {
        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(this);
        String storagePath = prefs.getString(StorageChangeReceiver.KEY_STORAGE,
                Environment.getExternalStorageDirectory().toString());
        MediaSetUtils.setRoot(storagePath);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        mGLRootView.lockRenderThread();
        try {
            super.onSaveInstanceState(outState);
            getStateManager().saveState(outState);
        } finally {
            mGLRootView.unlockRenderThread();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        mStateManager.onConfigurationChange(config);
        getGalleryActionBar().onConfigurationChanged();
        invalidateOptionsMenu();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        return getStateManager().createOptionsMenu(menu);
    }

    @Override
    public Context getAndroidContext() {
        return this;
    }

    @Override
    public DataManager getDataManager() {
        return ((GalleryApp) getApplication()).getDataManager();
    }

    @Override
    public ThreadPool getThreadPool() {
        return ((GalleryApp) getApplication()).getThreadPool();
    }

    public synchronized StateManager getStateManager() {
        if (mStateManager == null) {
            mStateManager = new StateManager(this);
        }
        return mStateManager;
    }

    public GLRoot getGLRoot() {
        return mGLRootView;
    }

    public GLRootView getGLRootView() {
        return mGLRootView;
    }

    @Override
    public void setContentView(int resId) {
        super.setContentView(resId);
        mGLRootView = (GLRootView) findViewById(R.id.gl_root_view);
        mProgress = (ProgressBar) findViewById(R.id.gallery_root_progress);
    }

    protected void onStorageReady() {
        if (mAlertDialog != null) {
            mAlertDialog.dismiss();
            mAlertDialog = null;
            unregisterReceiver(mMountReceiver);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (getExternalCacheDir() == null) {
            OnCancelListener onCancel = new OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    finish();
                }
            };
            OnClickListener onClick = new OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.cancel();
                }
            };
            AlertDialog.Builder builder = new AlertDialog.Builder(this)
                    .setTitle(R.string.no_external_storage_title)
                    .setMessage(R.string.no_external_storage)
                    .setNegativeButton(android.R.string.cancel, onClick)
                    .setOnCancelListener(onCancel);
            if (ApiHelper.HAS_SET_ICON_ATTRIBUTE) {
                setAlertDialogIconAttribute(builder);
            } else {
                builder.setIcon(android.R.drawable.ic_dialog_alert);
            }
            mAlertDialog = builder.show();
            registerReceiver(mMountReceiver, mMountFilter);
        }
        mPanoramaViewHelper.onStart();
    }

    @TargetApi(ApiHelper.VERSION_CODES.HONEYCOMB)
    private static void setAlertDialogIconAttribute(
            AlertDialog.Builder builder) {
        builder.setIconAttribute(android.R.attr.alertDialogIcon);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mAlertDialog != null) {
            unregisterReceiver(mMountReceiver);
            mAlertDialog.dismiss();
            mAlertDialog = null;
        }
        mPanoramaViewHelper.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mGLRootView.lockRenderThread();
        try {
            getStateManager().resume();
            getDataManager().resume();
        } finally {
            mGLRootView.unlockRenderThread();
        }
        mGLRootView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mGLRootView.onPause();
        mGLRootView.lockRenderThread();
        try {
            getStateManager().pause();
            getDataManager().pause();
        } finally {
            mGLRootView.unlockRenderThread();
        }
        GalleryBitmapPool.getInstance().clear();
        MediaItem.getBytesBufferPool().clear();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mGLRootView.lockRenderThread();
        try {
            getStateManager().destroy();
        } finally {
            mGLRootView.unlockRenderThread();
        }
        doUnbindBatchService();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        mGLRootView.lockRenderThread();
        try {
            getStateManager().notifyActivityResult(
                    requestCode, resultCode, data);
        } finally {
            mGLRootView.unlockRenderThread();
        }
    }

    @Override
    public void onBackPressed() {
        // send the back event to the top sub-state
        GLRoot root = getGLRoot();
        root.lockRenderThread();
        try {
            getStateManager().onBackPressed();
        } finally {
            root.unlockRenderThread();
        }
    }

    public GalleryActionBar getGalleryActionBar() {
        if (mActionBar == null) {
            mActionBar = new GalleryActionBar(this);
        }
        return mActionBar;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        GLRoot root = getGLRoot();
        root.lockRenderThread();
        try {
            return getStateManager().itemSelected(item);
        } finally {
            root.unlockRenderThread();
        }
    }

    public TransitionStore getTransitionStore() {
        return mTransitionStore;
    }

    public PanoramaViewHelper getPanoramaViewHelper() {
        return mPanoramaViewHelper;
    }

    protected boolean isFullscreen() {
        return (getWindow().getAttributes().flags
                & WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0;
    }

    private BatchService mBatchService;
    private boolean mBatchServiceIsBound = false;
    private ServiceConnection mBatchServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            mBatchService = ((BatchService.LocalBinder)service).getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            mBatchService = null;
        }
    };

    private void doBindBatchService() {
        bindService(new Intent(this, BatchService.class), mBatchServiceConnection, Context.BIND_AUTO_CREATE);
        mBatchServiceIsBound = true;
    }

    private void doUnbindBatchService() {
        if (mBatchServiceIsBound) {
            // Detach our existing connection.
            unbindService(mBatchServiceConnection);
            mBatchServiceIsBound = false;
        }
    }

    public ThreadPool getBatchServiceThreadPoolIfAvailable() {
        if (mBatchServiceIsBound && mBatchService != null) {
            return mBatchService.getThreadPool();
        } else {
            throw new RuntimeException("Batch service unavailable");
        }
    }

    public void printSelectedImage(Uri uri) {
        if (uri == null) {
            return;
        }
        String path = ImageLoader.getLocalPathFromUri(this, uri);
        if (path != null) {
            Uri localUri = Uri.parse(path);
            path = localUri.getLastPathSegment();
        } else {
            path = uri.getLastPathSegment();
        }
        PrintHelper printer = new PrintHelper(this);
        try {
            printer.printBitmap(path, uri);
        } catch (FileNotFoundException fnfe) {
            Log.e(TAG, "Error printing an image", fnfe);
        }
    }

    public void hideSystemBars() {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_IMMERSIVE);
    }

    public void showSystemBars() {
        showSystemUI();
    }

    public void showSystemUI() {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    public void setSystemBarsTranlucent(boolean withTranlucent) {
        if (withTranlucent) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
                | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
                | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        }
    }

    public void setKeepScreenOn(boolean keepScreenOn) {
        if (keepScreenOn) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    public void setDismissKeyguard(boolean dismissKeyguard) {
        if (dismissKeyguard) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        }
    }

    protected void showProgress() {
        mProgress.setVisibility(View.VISIBLE);
    }
    protected void hideProgress() {
        mProgress.setVisibility(View.GONE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[],
            int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_LOCATION: {
                if (checkPermissionGrantResults(grantResults)) {
                    if (mRunWithPermission != null) {
                        mRunWithPermission.run();
                    }
                } else {
                    if (mRunWithoutPermission != null) {
                        mRunWithoutPermission.run();
                    }
                }
            }
        }
    }

    public void doRunWithLocationPermission(Runnable runWithPermission, Runnable runWithoutPermission) {
        mRunWithPermission = runWithPermission;
        mRunWithoutPermission = runWithoutPermission;
        String[] permissions = {
                Manifest.permission.ACCESS_FINE_LOCATION
        };

        if (!hasLocationPermission()) {
            requestPermissions(permissions, PERMISSION_REQUEST_LOCATION);
        }  else {
            runWithPermission.run();
        }
    }

    public boolean hasLocationPermission() {
        boolean needRequest = false;

        String[] permissions = {
                Manifest.permission.ACCESS_FINE_LOCATION
        };
        for (String permission : permissions) {
            if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                needRequest = true;
                break;
            }
        }
        return !needRequest;
    }

    private boolean checkPermissionGrantResults(int[] grantResults) {
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }
}
