/*
 * Copyright (C) 2007 The Android Open Source Project
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
import android.app.AlertDialog;
import android.app.WallpaperManager;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.graphics.Point;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Display;

import com.android.gallery3d.common.ApiHelper;
import com.android.gallery3d.filtershow.crop.CropActivity;
import com.android.gallery3d.filtershow.crop.CropExtras;
import com.android.gallery3d.R;

import java.lang.IllegalArgumentException;

/**
 * Wallpaper picker for the gallery application. This just redirects to the
 * standard pick action.
 */
public class Wallpaper extends Activity {
    @SuppressWarnings("unused")
    private static final String TAG = "Wallpaper";

    private static final String IMAGE_TYPE = "image/*";
    private static final String KEY_STATE = "activity-state";
    private static final String KEY_PICKED_ITEM = "picked-item";
    private static final String KEY_ASPECT_X = "aspectX";
    private static final String KEY_ASPECT_Y = "aspectY";
    private static final String KEY_SPOTLIGHT_X = "spotlightX";
    private static final String KEY_SPOTLIGHT_Y = "spotlightY";
    private static final String KEY_FROM_SCREENCOLOR = "fromScreenColor";

    private static final int STATE_INIT = 0;
    private static final int STATE_PHOTO_PICKED = 1;

    private int mState = STATE_INIT;
    private Uri mPickedItem;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        if (bundle != null) {
            mState = bundle.getInt(KEY_STATE);
            mPickedItem = (Uri) bundle.getParcelable(KEY_PICKED_ITEM);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle saveState) {
        saveState.putInt(KEY_STATE, mState);
        if (mPickedItem != null) {
            saveState.putParcelable(KEY_PICKED_ITEM, mPickedItem);
        }
    }

    @SuppressWarnings("deprecation")
    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
    private Point getDefaultDisplaySize(Point size) {
        Display d = getWindowManager().getDefaultDisplay();
        if (Build.VERSION.SDK_INT >= ApiHelper.VERSION_CODES.HONEYCOMB_MR2) {
            d.getRealSize(size);
        } else {
            size.set(d.getWidth(), d.getHeight());
        }
        return size;
    }

    @SuppressWarnings("fallthrough")
    @Override
    protected void onResume() {
        super.onResume();
        Intent intent = getIntent();
        switch (mState) {
            case STATE_INIT: {
                mPickedItem = intent.getData();
                if (mPickedItem == null) {
                    Intent request = new Intent(Intent.ACTION_GET_CONTENT)
                            .setClass(this, DialogPicker.class)
                            .setType(IMAGE_TYPE);
                    startActivityForResult(request, STATE_PHOTO_PICKED);
                    return;
                }
                mState = STATE_PHOTO_PICKED;
                // fall-through
            }
            case STATE_PHOTO_PICKED: {
                Intent cropAndSetWallpaperIntent;
                boolean fromScreenColor = false;

                // Do this for screencolor select and crop image to preview.
                Bundle extras = intent.getExtras();
                if (extras != null) {
                    fromScreenColor = extras.getBoolean(KEY_FROM_SCREENCOLOR, false);
                }
                final WallpaperManager wpm = WallpaperManager.getInstance(getApplicationContext());
                final Point dispSize = getDefaultDisplaySize(new Point());

                boolean setWallpaper = !fromScreenColor;
                if (setWallpaper) {
                    // ask if scrolling wallpaper should be used original size
                    // or if it should be cropped to image size
                    AlertDialog.Builder scrollingWallDialog = new AlertDialog.Builder(this);
                    scrollingWallDialog.setMessage(getResources().getString(R.string.scrolling_wall_dialog_text));
                    scrollingWallDialog.setTitle(getResources().getString(R.string.scrolling_wall_dialog_title));
                    scrollingWallDialog.setCancelable(false);
                    scrollingWallDialog.setPositiveButton(R.string.scrolling_wall_yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            int width = wpm.getDesiredMinimumWidth();
                            int height = wpm.getDesiredMinimumHeight();
                            float spotlightX = (float) dispSize.x / width;
                            float spotlightY = (float) dispSize.y / height;
                            doCallCropWallpaper(width, height, spotlightX, spotlightY, true);
                        }
                    });
                    scrollingWallDialog.setNegativeButton(R.string.scrolling_wall_no, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            int width = dispSize.x;
                            int height = dispSize.y;
                            float spotlightX = (float) dispSize.x / width;
                            float spotlightY = (float) dispSize.y / height;
                            doCallCropWallpaper(width, height, spotlightX, spotlightY, true);
                        }
                    });
                    AlertDialog d = scrollingWallDialog.create();
                    d.show();
                } else {
                    int width = extras.getInt(KEY_ASPECT_X, 0);
                    int height = extras.getInt(KEY_ASPECT_Y, 0);
                    float spotlightX = extras.getFloat(KEY_SPOTLIGHT_X, 0);
                    float spotlightY = extras.getFloat(KEY_SPOTLIGHT_Y, 0);
                    doCallCropWallpaper(width, height, spotlightX, spotlightY, false);
                }
            }
        }
    }

    private void doCallCropWallpaper(int width, int height, float spotlightX, float spotlightY, boolean setWallpaper) {
        final Intent cropAndSetWallpaperIntent = new Intent(CropActivity.CROP_ACTION)
                        .setClass(this, CropActivity.class)
                        .setDataAndType(mPickedItem, IMAGE_TYPE)
                        .addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT)
                        .putExtra(CropExtras.KEY_OUTPUT_X, width)
                        .putExtra(CropExtras.KEY_OUTPUT_Y, height)
                        .putExtra(CropExtras.KEY_ASPECT_X, width)
                        .putExtra(CropExtras.KEY_ASPECT_Y, height)
                        .putExtra(CropExtras.KEY_SPOTLIGHT_X, spotlightX)
                        .putExtra(CropExtras.KEY_SPOTLIGHT_Y, spotlightY)
                        .putExtra(CropExtras.KEY_SCALE, true)
                        .putExtra(CropExtras.KEY_SCALE_UP_IF_NEEDED, true);

        if (setWallpaper) {
            AlertDialog.Builder wallpaperTypeDialog = new AlertDialog.Builder(this);
            wallpaperTypeDialog.setTitle(getResources().getString(R.string.wallpaper_type_dialog_title));
            wallpaperTypeDialog.setItems(R.array.wallpaper_type_list, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int item) {
                    int wallpaperType = CropExtras.DEFAULT_WALLPAPER_TYPE;
                    if (item == 1) {
                        wallpaperType = WallpaperManager.FLAG_SYSTEM;
                    } else if (item == 2) {
                        wallpaperType = WallpaperManager.FLAG_LOCK;
                    }
                    cropAndSetWallpaperIntent.putExtra(CropExtras.KEY_SET_AS_WALLPAPER, setWallpaper)
                            .putExtra(CropExtras.KEY_WALLPAPER_TYPE, wallpaperType);
                    startActivity(cropAndSetWallpaperIntent);
                    finish();
                }
            });
            AlertDialog d = wallpaperTypeDialog.create();
            d.show();
        } else {
            cropAndSetWallpaperIntent.putExtra(CropExtras.KEY_SET_AS_WALLPAPER, setWallpaper);
            startActivity(cropAndSetWallpaperIntent);
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != RESULT_OK) {
            setResult(resultCode);
            finish();
            return;
        }
        mState = requestCode;
        if (mState == STATE_PHOTO_PICKED) {
            mPickedItem = data.getData();
        }

        // onResume() would be called next
    }
}
