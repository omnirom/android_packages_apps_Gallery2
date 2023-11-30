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

package com.android.gallery3d.gadget;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;
import android.view.MenuItem;

import android.widget.RemoteViews;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import com.android.gallery3d.R;
import com.android.gallery3d.app.AlbumPicker;
import com.android.gallery3d.app.DialogPicker;
import com.android.gallery3d.app.GalleryApp;
import com.android.gallery3d.common.ApiHelper;
import com.android.gallery3d.data.DataManager;
import com.android.gallery3d.data.LocalAlbum;
import com.android.gallery3d.data.MediaSet;
import com.android.gallery3d.data.Path;
import com.android.gallery3d.filtershow.crop.CropActivity;
import com.android.gallery3d.filtershow.crop.CropExtras;

public class WidgetConfigure extends AppCompatActivity {
    @SuppressWarnings("unused")
    private static final String TAG = "WidgetConfigure";

    public static final String KEY_WIDGET_TYPE = "widget-type";
    private static final String KEY_PICKED_ITEM = "picked-item";

    private static final int REQUEST_WIDGET_TYPE = 1;
    private static final int REQUEST_CHOOSE_ALBUM = 2;
    private static final int REQUEST_CROP_IMAGE = 3;
    private static final int REQUEST_GET_PHOTO = 4;

    public static final int RESULT_ERROR = RESULT_FIRST_USER;

    private int mAppWidgetId = -1;
    private Uri mPickedItem;
    
    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        mAppWidgetId = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);

        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayOptions(ActionBar.DISPLAY_HOME_AS_UP | ActionBar.DISPLAY_SHOW_TITLE);
        actionBar.setTitle(getResources().getString(R.string.appwidget_title));

        if (mAppWidgetId == -1) {
            setResult(Activity.RESULT_CANCELED);
            finish();
            return;
        }

        setContentView(R.layout.choose_widget_type);
        RadioGroup rg = (RadioGroup) findViewById(R.id.widget_type);
        rg.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                setWidgetType(new Intent().putExtra(KEY_WIDGET_TYPE, checkedId));
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            setResult(RESULT_CANCELED);
            finish();
            return true;
        }
        return false;
    }

    protected void onSaveInstanceStates(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(KEY_PICKED_ITEM, mPickedItem);
    }

    private void updateWidgetAndFinish(WidgetDatabaseHelper.Entry entry) {
        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        RemoteViews views = PhotoAppWidgetProvider.buildWidget(this, mAppWidgetId, entry);
        manager.updateAppWidget(mAppWidgetId, views);
        setResult(RESULT_OK, new Intent().putExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId));
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != RESULT_OK) {
            setResult(resultCode, new Intent().putExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId));
            finish();
            return;
        }

        if (requestCode == REQUEST_CHOOSE_ALBUM) {
            setChoosenAlbum(data);
        } else if (requestCode == REQUEST_GET_PHOTO) {
            setChoosenPhoto(data);
        } else if (requestCode == REQUEST_CROP_IMAGE) {
            setPhotoWidget(data);
        } else {
            throw new AssertionError("unknown request: " + requestCode);
        }
    }

    private void setPhotoWidget(Intent data) {
        // Store the cropped photo in our database
        Bitmap bitmap = (Bitmap) data.getParcelableExtra("data");
        WidgetDatabaseHelper helper = new WidgetDatabaseHelper(this);
        try {
            helper.setPhoto(mAppWidgetId, mPickedItem, bitmap);
            updateWidgetAndFinish(helper.getEntry(mAppWidgetId));
        } finally {
            helper.close();
        }
    }

    private void setChoosenPhoto(Intent data) {
        Resources res = getResources();

        float widgetWidth = res.getDimensionPixelSize(R.dimen.max_appwidget_resize_width);
        float widgetHeight = res.getDimensionPixelSize(R.dimen.max_appwidget_resize_height);

        mPickedItem = data.getData();
        Intent request = new Intent(CropActivity.CROP_ACTION, mPickedItem)
                .putExtra(CropExtras.KEY_OUTPUT_X, widgetWidth)
                .putExtra(CropExtras.KEY_OUTPUT_Y, widgetHeight)
                .putExtra(CropExtras.KEY_ASPECT_X, widgetWidth)
                .putExtra(CropExtras.KEY_ASPECT_Y, widgetHeight)
                .putExtra(CropExtras.KEY_SCALE_UP_IF_NEEDED, true)
                .putExtra(CropExtras.KEY_SCALE, true)
                .putExtra(CropExtras.KEY_RETURN_DATA, true);
        startActivityForResult(request, REQUEST_CROP_IMAGE);
    }

    private void setChoosenAlbum(Intent data) {
        String albumPath = data.getStringExtra(AlbumPicker.KEY_ALBUM_PATH);
        WidgetDatabaseHelper helper = new WidgetDatabaseHelper(this);
        try {
            String relativePath = null;
            GalleryApp galleryApp = (GalleryApp) getApplicationContext();
            DataManager manager = galleryApp.getDataManager();
            Path path = Path.fromString(albumPath);
            MediaSet mediaSet = (MediaSet) manager.getMediaObject(path);
            if (mediaSet instanceof LocalAlbum) {
                int bucketId = Integer.parseInt(path.getSuffix());
                // If the chosen album is a local album, find relative path
                // Otherwise, leave the relative path field empty
                relativePath = LocalAlbum.getRelativePath(bucketId);
                Log.i(TAG, "Setting widget, album path: " + albumPath
                        + ", relative path: " + relativePath);
            }
            helper.setWidget(mAppWidgetId,
                    WidgetDatabaseHelper.TYPE_ALBUM, albumPath, relativePath);
            updateWidgetAndFinish(helper.getEntry(mAppWidgetId));
        } finally {
            helper.close();
        }
    }

    private void setWidgetType(Intent data) {
        int widgetType = data.getIntExtra(KEY_WIDGET_TYPE, R.id.widget_type_shuffle);
        if (widgetType == R.id.widget_type_album) {
            Intent intent = new Intent(this, AlbumPicker.class);
            startActivityForResult(intent, REQUEST_CHOOSE_ALBUM);
        } else if (widgetType == R.id.widget_type_shuffle) {
            WidgetDatabaseHelper helper = new WidgetDatabaseHelper(this);
            try {
                helper.setWidget(mAppWidgetId, WidgetDatabaseHelper.TYPE_SHUFFLE, null, null);
                updateWidgetAndFinish(helper.getEntry(mAppWidgetId));
            } finally {
                helper.close();
            }
        } else {
            // Explicitly send the intent to the DialogPhotoPicker
            Intent request = new Intent(this, DialogPicker.class)
                    .setAction(Intent.ACTION_GET_CONTENT)
                    .setType("image/*");
            startActivityForResult(request, REQUEST_GET_PHOTO);
        }
    }
}
