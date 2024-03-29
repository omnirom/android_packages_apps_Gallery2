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

package com.android.gallery3d.data;

import android.database.Cursor;

import com.android.gallery3d.util.GalleryUtils;

import java.io.File;
import java.text.DateFormat;
import java.util.Date;
import java.text.SimpleDateFormat;

//
// LocalMediaItem is an abstract class captures those common fields
// in LocalImage and LocalVideo.
//
public abstract class LocalMediaItem extends MediaItem {

    @SuppressWarnings("unused")
    private static final String TAG = "LocalMediaItem";

    // database fields
    public int id;
    public String caption;
    public String mimeType;
    public long fileSize;
    protected float latitude = MediaItem.INVALID_LATLNG;
    protected float longitude = MediaItem.INVALID_LATLNG;
    public long dateTakenInMs;
    public long dateAddedInSec;
    public long dateModifiedInSec;
    public String filePath;
    public int bucketId;
    public int width;
    public int height;
    private boolean mLocationResolved;
    public String relFilePath;

    private static final SimpleDateFormat mDateFormatFilter = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    public LocalMediaItem(Path path, long version) {
        super(path, version);
    }

    @Override
    public long getDateInMs() {
        return dateModifiedInSec * 1000;
    }

    @Override
    public String getName() {
        return caption;
    }

    @Override
    public void getLatLong(double[] latLong) {
        if (!mLocationResolved && latitude == MediaItem.INVALID_LATLNG &&
                longitude == MediaItem.INVALID_LATLNG) {
            resolveLocation();
            mLocationResolved = true;
        }
        latLong[0] = latitude;
        latLong[1] = longitude;
    }

    abstract protected boolean updateFromCursor(Cursor cursor);

    public int getBucketId() {
        return bucketId;
    }

    protected void updateContent(Cursor cursor) {
        if (updateFromCursor(cursor)) {
            mDataVersion = nextVersionNumber();
        }
    }

    @Override
    public MediaDetails getDetails() {
        MediaDetails details = super.getDetails();
        details.addDetail(MediaDetails.INDEX_PATH, filePath);
        if (caption == null && filePath != null) {
            caption = new File(filePath).getName();
        }
        details.addDetail(MediaDetails.INDEX_TITLE, caption);
        DateFormat formater = DateFormat.getDateTimeInstance();
        details.addDetail(MediaDetails.INDEX_DATETIME,
                formater.format(new Date(dateModifiedInSec * 1000)));
        details.addDetail(MediaDetails.INDEX_WIDTH, width);
        details.addDetail(MediaDetails.INDEX_HEIGHT, height);

        if (GalleryUtils.isValidLocation(latitude, longitude)) {
            details.addDetail(MediaDetails.INDEX_LOCATION, new double[] {latitude, longitude});
        }
        if (fileSize > 0) details.addDetail(MediaDetails.INDEX_SIZE, fileSize);
        return details;
    }

    @Override
    public String getMimeType() {
        return mimeType;
    }

    @Override
    public long getSize() {
        return fileSize;
    }

    protected void calcRelFilePath() {
        File f = new File(filePath);
        if (f.getParent() != null) {
            relFilePath = new File(f.getParentFile().getName(), f.getName()).getPath();
        } else {
            relFilePath = filePath;
        }
    }
}
