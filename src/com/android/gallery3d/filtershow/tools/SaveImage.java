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

package com.android.gallery3d.filtershow.tools;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Images.ImageColumns;
import android.util.Log;
import android.widget.Toast;

import com.android.gallery3d.R;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.exif.ExifInterface;
import com.android.gallery3d.filtershow.FilterShowActivity;
import com.android.gallery3d.filtershow.cache.ImageLoader;
import com.android.gallery3d.filtershow.filters.FilterRepresentation;
import com.android.gallery3d.filtershow.filters.FiltersManager;
import com.android.gallery3d.filtershow.imageshow.MasterImage;
import com.android.gallery3d.filtershow.pipeline.CachingPipeline;
import com.android.gallery3d.filtershow.pipeline.ImagePreset;
import com.android.gallery3d.filtershow.pipeline.ProcessingService;
import com.android.gallery3d.util.XmpUtilHelper;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * Handles saving edited photo
 */
public class SaveImage {
    private static final String LOGTAG = "Gallery2:SaveImage";
    private static final boolean DEBUG = false;

    public interface ContentResolverQueryCallback {
        void onCursorResult(Cursor cursor);
    }

    private static final String TIME_STAMP_NAME = "_yyyyMMdd_HHmmss";
    private static final String PREFIX_PANO = "PANO";
    private static final String PREFIX_IMG = "IMG";
    private static final String POSTFIX_JPG = ".jpg";

    private final Context mContext;
    private final Uri mSourceUri;
    private final File mDestinationFile;
    private final Uri mSelectedImageUri;
    private final boolean mEditInPlace = false;

    /**
     * @param context
     * @param sourceUri The Uri for the original image,
     * @param selectedImageUri The Uri for the image selected by the user.
     *  In most cases, it is a content Uri for local image or remote image.
     * @param destination Destinaton File, if this is null, a new file will be
     *  created under the same directory as selectedImageUri.
     * @param callback Let the caller know the saving has completed.
     * @return the newSourceUri
     */
    public SaveImage(Context context, Uri sourceUri, Uri selectedImageUri,
                     File destination)  {
        mContext = context;
        mSourceUri = sourceUri;
        if (destination == null) {
            if (mEditInPlace) {
                mDestinationFile = getLocalFileFromUri(context, sourceUri);
            } else {
                mDestinationFile = getNewFile(context, selectedImageUri);
            }
        } else {
            mDestinationFile = destination;
        }

        mSelectedImageUri = selectedImageUri;
    }

    public static File getNewFile(Context context, Uri sourceUri) {
        File saveDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        String filename = new SimpleDateFormat(TIME_STAMP_NAME).format(new Date(
                System.currentTimeMillis()));
        if (hasPanoPrefix(context, sourceUri)) {
            return new File(saveDirectory, PREFIX_PANO + filename + POSTFIX_JPG);
        }
        return new File(saveDirectory, PREFIX_IMG + filename + POSTFIX_JPG);
    }

    public Object getPanoramaXMPData(Uri source, ImagePreset preset) {
        Object xmp = null;
        if (preset.isPanoramaSafe()) {
            InputStream is = null;
            try {
                is = mContext.getContentResolver().openInputStream(source);
                xmp = XmpUtilHelper.extractXMPMeta(is);
            } catch (FileNotFoundException e) {
                Log.w(LOGTAG, "Failed to get XMP data from image: ", e);
            } finally {
                Utils.closeSilently(is);
            }
        }
        return xmp;
    }

    public boolean putPanoramaXMPData(File file, Object xmp) {
        if (xmp != null) {
            return XmpUtilHelper.writeXMPMeta(file.getAbsolutePath(), xmp);
        }
        return false;
    }

    public ExifInterface getExifData(Uri source) {
        ExifInterface exif = new ExifInterface();
        String mimeType = mContext.getContentResolver().getType(mSelectedImageUri);
        if (mimeType == null) {
            mimeType = ImageLoader.getMimeType(mSelectedImageUri);
        }
        if (mimeType.equals(ImageLoader.JPEG_MIME_TYPE)) {
            InputStream inStream = null;
            try {
                inStream = mContext.getContentResolver().openInputStream(source);
                exif.readExif(inStream);
            } catch (FileNotFoundException e) {
                Log.w(LOGTAG, "Cannot find file: " + source, e);
            } catch (IOException e) {
                Log.w(LOGTAG, "Cannot read exif for: " + source, e);
            } catch (NullPointerException e) {
                Log.w(LOGTAG, "Invalid exif data for: " + source, e);
            } finally {
                Utils.closeSilently(inStream);
            }
        }
        return exif;
    }

    public boolean putExifData(File file, ExifInterface exif, Bitmap image,
            int jpegCompressQuality) {
        boolean ret = false;
        OutputStream s = null;
        try {
            s = exif.getExifWriterStream(file.getAbsolutePath());
            image.compress(Bitmap.CompressFormat.JPEG,
                    (jpegCompressQuality > 0) ? jpegCompressQuality : 1, s);
            s.flush();
            s.close();
            s = null;
            ret = true;
        } catch (FileNotFoundException e) {
            Log.w(LOGTAG, "File not found: " + file.getAbsolutePath(), e);
        } catch (IOException e) {
            Log.w(LOGTAG, "Could not write exif: ", e);
        } finally {
            Utils.closeSilently(s);
        }
        return ret;
    }


    private void updateExifData(ExifInterface exif, long time) {
        // Set tags
        exif.addDateTimeStampTag(ExifInterface.TAG_DATE_TIME, time,
                TimeZone.getDefault());
        exif.setTag(exif.buildTag(ExifInterface.TAG_ORIENTATION,
                ExifInterface.Orientation.TOP_LEFT));
        // Remove old thumbnail
        exif.removeCompressedThumbnail();
    }

    public Uri processAndSaveImage(ImagePreset preset, boolean flatten,
                                   int quality, float sizeFactor) {

        if (DEBUG) Log.d(LOGTAG, "processAndSaveImage flatten = " + flatten + " mSourceUri = " + mSourceUri + " mSelectedImageUri = " + mSelectedImageUri +  " mDestinationFile = " + mDestinationFile);
        Uri uri = null;
        boolean noBitmap = true;
        int num_tries = 0;
        int sampleSize = 1;

        // Stopgap fix for low-memory devices.
        while (noBitmap) {
            try {
                // Try to do bitmap operations, downsample if low-memory
                Bitmap bitmap = ImageLoader.loadOrientedBitmapWithBackouts(mContext, mSourceUri,
                        sampleSize);
                if (bitmap == null) {
                    return null;
                }
                if (sizeFactor != 1f) {
                    // if we have a valid size
                    int w = (int) (bitmap.getWidth() * sizeFactor);
                    int h = (int) (bitmap.getHeight() * sizeFactor);
                    if (w == 0 || h == 0) {
                        w = 1;
                        h = 1;
                    }
                    bitmap = Bitmap.createScaledBitmap(bitmap, w, h, true);
                }
                CachingPipeline pipeline = new CachingPipeline(mContext, FiltersManager.getManager(),
                        "Saving");

                bitmap = pipeline.renderFinalImage(bitmap, preset);

                Object xmp = getPanoramaXMPData(mSourceUri, preset);
                ExifInterface exif = getExifData(mSourceUri);
                long time = System.currentTimeMillis();

                updateExifData(exif, time);

                // If we succeed in writing the bitmap as a jpeg, return a uri.
                if (putExifData(mDestinationFile, exif, bitmap, quality)) {
                    putPanoramaXMPData(mDestinationFile, xmp);
                    
                    if (!flatten) {
                        XmpPresets.writeFilterXMP(mContext, mSourceUri,
                                mDestinationFile, preset);
                    }
                    if (mEditInPlace) {
                        uri = updateFile(mContext, mSelectedImageUri, mDestinationFile, time);
                    } else {
                        uri = createNewUriFromFile(mContext, mSelectedImageUri,
                                mDestinationFile, time);
                    }
                }

                noBitmap = false;
            } catch (OutOfMemoryError e) {
                // Try 5 times before failing for good.
                if (++num_tries >= 5) {
                    throw e;
                }
                System.gc();
                sampleSize *= 2;
            }
        }
        
        if (DEBUG) Log.d(LOGTAG, "processAndSaveImage uri = " + uri);
        return uri;
    }

    public static Uri makeAndInsertUri(Context context, Uri sourceUri) {
        long time = System.currentTimeMillis();
        String filename = new SimpleDateFormat(TIME_STAMP_NAME).format(new Date(time));
        File saveDirectory = getSaveDirectory(context, sourceUri);
        File file = new File(saveDirectory, filename  + POSTFIX_JPG);
        return createNewUriFromFile(context, sourceUri, file, time);
    }

    public static void saveImage(ImagePreset preset, final FilterShowActivity filterShowActivity,
            File destination) {
        Uri selectedImageUri = filterShowActivity.getSelectedImageUri();
        Uri sourceImageUri = MasterImage.getImage().getUri();
        boolean flatten = false;
        if (preset.contains(FilterRepresentation.TYPE_TINYPLANET)){
            flatten = true;
        }
        if (DEBUG) Log.d(LOGTAG, "saveImage flatten = " + flatten + " sourceImageUri = " + sourceImageUri + " selectedImageUri = " + selectedImageUri +  " destination = " + destination);
        
        Intent processIntent = ProcessingService.getSaveIntent(filterShowActivity, preset,
                destination, selectedImageUri, sourceImageUri, flatten, 90, 1f);

        filterShowActivity.startForegroundService(processIntent);
    }

    public static void querySource(Context context, Uri sourceUri, String[] projection,
            ContentResolverQueryCallback callback) {
        ContentResolver contentResolver = context.getContentResolver();
        querySourceFromContentResolver(contentResolver, sourceUri, projection, callback);
    }

    private static void querySourceFromContentResolver(
            ContentResolver contentResolver, Uri sourceUri, String[] projection,
            ContentResolverQueryCallback callback) {
        Cursor cursor = null;
        try {
            cursor = contentResolver.query(sourceUri, projection, null, null,
                    null);
            if ((cursor != null) && cursor.moveToNext()) {
                callback.onCursorResult(cursor);
            }
        } catch (Exception e) {
            // Ignore error for lacking the data column from the source.
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private static File getSaveDirectory(Context context, Uri sourceUri) {
        File file = getLocalFileFromUri(context, sourceUri);
        if (file != null) {
            return file.getParentFile();
        } else {
            return null;
        }
    }

    /**
     * Construct a File object based on the srcUri.
     * @return The file object. Return null if srcUri is invalid or not a local
     * file.
     */
    private static File getLocalFileFromUri(Context context, Uri srcUri) {
        if (srcUri == null) {
            Log.e(LOGTAG, "srcUri is null.");
            return null;
        }

        String scheme = srcUri.getScheme();
        if (scheme == null) {
            Log.e(LOGTAG, "scheme is null.");
            return null;
        }

        final File[] file = new File[1];
        // sourceUri can be a file path or a content Uri, it need to be handled
        // differently.
        if (scheme.equals(ContentResolver.SCHEME_CONTENT)) {
            if (srcUri.getAuthority().equals(MediaStore.AUTHORITY)) {
                querySource(context, srcUri, new String[] {
                        ImageColumns.DATA
                },
                        new ContentResolverQueryCallback() {

                            @Override
                            public void onCursorResult(Cursor cursor) {
                                file[0] = new File(cursor.getString(0));
                            }
                        });
            }
        } else if (scheme.equals(ContentResolver.SCHEME_FILE)) {
            file[0] = new File(srcUri.getPath());
        }
        return file[0];
    }

    /**
     * Gets the actual filename for a Uri from Gallery's ContentProvider.
     */
    private static String getTrueFilename(Context context, Uri src) {
        if (context == null || src == null) {
            return null;
        }
        final String[] trueName = new String[1];
        querySource(context, src, new String[] {
                ImageColumns.DATA
        }, new ContentResolverQueryCallback() {
            @Override
            public void onCursorResult(Cursor cursor) {
                trueName[0] = new File(cursor.getString(0)).getName();
            }
        });
        return trueName[0];
    }

    /**
     * Checks whether the true filename has the panorama image prefix.
     */
    private static boolean hasPanoPrefix(Context context, Uri src) {
        String name = getTrueFilename(context, src);
        return name != null && name.startsWith(PREFIX_PANO);
    }

    private static Uri createNewUriFromFile(Context context, Uri sourceUri,
            File file, long time) {
        //File oldSelectedFile = getLocalFileFromUri(context, sourceUri);
        final ContentValues values = getContentValues(context, sourceUri, file, time);
        Uri result = context.getContentResolver().insert(
                    Images.Media.EXTERNAL_CONTENT_URI, values);
        return result;
    }

    public static Uri updateFile(Context context, Uri sourceUri, File file, long time) {
        final ContentValues values = getContentValues(context, sourceUri, file, time);
        context.getContentResolver().update(sourceUri, values, null, null);
        return sourceUri;
    }

    private static ContentValues getContentValues(Context context, Uri sourceUri,
                                                  File file, long time) {
        final ContentValues values = new ContentValues();

        time /= 1000;
        values.put(Images.Media.TITLE, file.getName());
        values.put(Images.Media.DISPLAY_NAME, file.getName());
        values.put(Images.Media.MIME_TYPE, "image/jpeg");
        values.put(Images.Media.DATE_TAKEN, time);
        values.put(Images.Media.DATE_MODIFIED, time);
        values.put(Images.Media.DATE_ADDED, time);
        values.put(Images.Media.ORIENTATION, 0);
        values.put(Images.Media.DATA, file.getAbsolutePath());
        values.put(Images.Media.SIZE, file.length());
        // This is a workaround to trigger the MediaProvider to re-generate the
        // thumbnail.
        values.put(Images.Media.MINI_THUMB_MAGIC, 0);

        final String[] projection = new String[] {
                ImageColumns.DATE_TAKEN,
                ImageColumns.LATITUDE, ImageColumns.LONGITUDE,
        };

        SaveImage.querySource(context, sourceUri, projection,
                new ContentResolverQueryCallback() {

                    @Override
                    public void onCursorResult(Cursor cursor) {
                        values.put(Images.Media.DATE_TAKEN, cursor.getLong(0));

                        double latitude = cursor.getDouble(1);
                        double longitude = cursor.getDouble(2);
                        // TODO: Change || to && after the default location
                        // issue is fixed.
                        if ((latitude != 0f) || (longitude != 0f)) {
                            values.put(Images.Media.LATITUDE, latitude);
                            values.put(Images.Media.LONGITUDE, longitude);
                        }
                    }
                });
        return values;
    }

    /**
     * @param sourceUri
     * @return true if the sourceUri is a local file Uri.
     */
    private static boolean isFileUri(Uri sourceUri) {
        String scheme = sourceUri.getScheme();
        if (scheme != null && scheme.equals(ContentResolver.SCHEME_FILE)) {
            return true;
        }
        return false;
    }

}
