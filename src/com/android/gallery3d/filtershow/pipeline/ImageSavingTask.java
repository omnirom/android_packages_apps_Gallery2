/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.gallery3d.filtershow.pipeline;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.Uri;
import android.util.Log;
import com.android.gallery3d.filtershow.cache.ImageLoader;
import com.android.gallery3d.filtershow.filters.FiltersManager;
import com.android.gallery3d.filtershow.tools.SaveImage;

import java.io.File;

public class ImageSavingTask extends ProcessingTask {
    private static final String TAG = "Gallery2:ImageSavingTask";
    private static final boolean DEBUG = false;

    private ProcessingService mProcessingService;

    static class SaveRequest implements Request {
        Uri sourceUri;
        Uri selectedUri;
        File destinationFile;
        ImagePreset preset;
        boolean flatten;
        int quality;
        float sizeFactor;
        Bitmap previewImage;
    }

    static class URIResult implements Result {
        Uri uri;
    }

    public ImageSavingTask(ProcessingService service) {
        mProcessingService = service;
    }

    public void saveImage(Uri sourceUri, Uri selectedUri,
                          File destinationFile, ImagePreset preset,
                          boolean flatten,
                          int quality, float sizeFactor) {
        if (DEBUG) Log.d(TAG, "saveImage flatten = " + flatten + " sourceUri = " + sourceUri + " selectedUri = " + selectedUri +  " destinationFile = " + destinationFile);
        SaveRequest request = new SaveRequest();
        request.sourceUri = sourceUri;
        request.selectedUri = selectedUri;
        request.destinationFile = destinationFile;
        request.preset = preset;
        request.flatten = flatten;
        request.quality = quality;
        request.sizeFactor = sizeFactor;
        postRequest(request);
    }

    public Result doInBackground(Request message) {
        SaveRequest request = (SaveRequest) message;
        Uri sourceUri = request.sourceUri;
        Uri selectedUri = request.selectedUri;
        File destinationFile = request.destinationFile;
        ImagePreset preset = request.preset;
        boolean flatten = request.flatten;
        
        if (DEBUG) Log.d(TAG, "doInBackground flatten = " + flatten + " sourceUri = " + sourceUri + " selectedUri = " + selectedUri +  " destinationFile = " + destinationFile);
        SaveImage saveImage = new SaveImage(mProcessingService, sourceUri,
                selectedUri, destinationFile);
        Uri uri = saveImage.processAndSaveImage(preset, flatten,
                request.quality, request.sizeFactor);
        URIResult result = new URIResult();
        result.uri = uri;
        return result;
    }

    @Override
    public void onResult(Result message) {
        URIResult result = (URIResult) message;
        mProcessingService.completeSaveImage(result.uri);
    }

    @Override
    public void onUpdate(Update message) {
    }
}
