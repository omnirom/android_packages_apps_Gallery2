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

package com.android.gallery3d.data;

import android.content.Context;

import com.android.gallery3d.R;

import java.util.ArrayList;

public class TypeClustering extends Clustering {
    @SuppressWarnings("unused")
    private static final String TAG = "TypeClustering";

    private TypeCluster[] mClusters;
    private String mUntaggedString;
    private Context mContext;

    private class TypeCluster {
        ArrayList<Path> mPaths = new ArrayList<Path>();
        String mName;
        MediaItem mCoverItem;

        public TypeCluster(String name) {
            mName = name;
        }

        public void add(MediaItem item) {
            Path path = item.getPath();
            mPaths.add(path);
            if (mCoverItem == null) {
                mCoverItem = item;
            }
        }

        public int size() {
            return mPaths.size();
        }

        public MediaItem getCover() {
            if (mCoverItem != null) {
                return mCoverItem;
            }
            return null;
        }
    }

    public TypeClustering(Context context) {
        mContext = context;
        mUntaggedString = context.getResources().getString(R.string.untagged);
    }

    @Override
    public void run(MediaSet baseSet) {
        // image and video
        final TypeCluster images = new TypeCluster(mContext.getResources().getString(R.string.type_images));
        final TypeCluster videos = new TypeCluster(mContext.getResources().getString(R.string.type_videos));
        final TypeCluster unknown = new TypeCluster(mUntaggedString);

        baseSet.enumerateTotalMediaItems(new MediaSet.ItemConsumer() {
            @Override
            public void consume(int index, MediaItem item) {
                if (item.getMediaType() == MediaObject.MEDIA_TYPE_IMAGE) {
                    images.add(item);
                } else if (item.getMediaType() == MediaObject.MEDIA_TYPE_VIDEO) {
                    videos.add(item);
                } else {
                    unknown.add(item);
                }
            }
        });
        if (unknown.size() != 0) {
            mClusters = new TypeCluster[] { images, videos, unknown };
        } else {
            mClusters = new TypeCluster[] { images, videos };
        }
    }

    @Override
    public int getNumberOfClusters() {
        return mClusters.length;
    }

    @Override
    public ArrayList<Path> getCluster(int index) {
        return mClusters[index].mPaths;
    }

    @Override
    public String getClusterName(int index) {
        return mClusters[index].mName;
    }

    @Override
    public MediaItem getClusterCover(int index) {
        return mClusters[index].getCover();
    }
}
