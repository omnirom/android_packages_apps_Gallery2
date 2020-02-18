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

import android.content.Context;
import android.text.format.DateFormat;
import android.text.format.DateUtils;

import com.android.gallery3d.common.Utils;
import com.android.gallery3d.util.GalleryUtils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

// cluster by month
// TODO maybe allow weeks optional
public class TimeClustering extends Clustering {
    @SuppressWarnings("unused")
    private static final String TAG = "TimeClustering";

    private Context mContext;
    private ArrayList<Cluster> mClusters;
    private String[] mNames;
    private HashMap<String, Cluster> mClusterMap = new HashMap<>();

    private static final Comparator<SmallItem> sDateComparator =
            new DateComparator();

    private static class DateComparator implements Comparator<SmallItem> {
        @Override
        public int compare(SmallItem item1, SmallItem item2) {
            return -Utils.compare(item1.dateInMs, item2.dateInMs);
        }
    }

    public TimeClustering(Context context) {
        mContext = context;
        mClusters = new ArrayList<Cluster>();
    }

    @Override
    public void run(MediaSet baseSet) {
        final int total = baseSet.getTotalMediaItemCount();
        final ArrayList<SmallItem> items = new ArrayList<SmallItem>(total);
        final Calendar cal = Calendar.getInstance();
        baseSet.enumerateTotalMediaItems(new MediaSet.ItemConsumer() {
            @Override
            public void consume(int index, MediaItem item) {
                if (index < 0 || index >= total) return;
                SmallItem s = new SmallItem();
                s.path = item.getPath();
                s.dateInMs = item.getDateInMs();
                cal.setTimeInMillis(s.dateInMs);
                s.year = cal.get(Calendar.YEAR);
                s.month = cal.get(Calendar.MONTH);
                s.week = cal.get(Calendar.WEEK_OF_MONTH);
                items.add(s);
            }
        });

        for (SmallItem item : items) {
            String key = item.year + ":" + item.month;
            Cluster c = mClusterMap.get(key);
            if (c != null) {
                c.addItem(item);
            } else {
                c = new Cluster();
                c.addItem(item);
                mClusterMap.put(key, c);
            }
        }

        ArrayList<String> keyList = new ArrayList<>();
        keyList.addAll(mClusterMap.keySet());
        Collections.sort(keyList);

        for (String key : keyList) {
            mClusters.add(mClusterMap.get(key));
        }
        Collections.reverse(mClusters);

        int m = mClusters.size();
        mNames = new String[m];
        for (int i = 0; i < m; i++) {
            mNames[i] = mClusters.get(i).generateCaption(mContext);
        }
    }

    @Override
    public int getNumberOfClusters() {
        return mClusters.size();
    }

    @Override
    public ArrayList<Path> getCluster(int index) {
        ArrayList<SmallItem> items = mClusters.get(index).getItems();
        Collections.sort(items, sDateComparator);
        ArrayList<Path> result = new ArrayList<Path>(items.size());
        for (int i = 0, n = items.size(); i < n; i++) {
            result.add(items.get(i).path);
        }
        return result;
    }

    @Override
    public String getClusterName(int index) {
        return mNames[index];
    }
}

class SmallItem {
    Path path;
    long dateInMs;
    int month;
    int year;
    int week;
}

class Cluster {
    @SuppressWarnings("unused")

    private ArrayList<SmallItem> mItems = new ArrayList<SmallItem>();

    public Cluster() {
    }

    public void addItem(SmallItem item) {
        mItems.add(item);
    }

    public int size() {
        return mItems.size();
    }

    public ArrayList<SmallItem> getItems() {
        return mItems;
    }

    public String generateCaption(Context context) {        
        int flags = DateUtils.FORMAT_ABBREV_MONTH | DateUtils.FORMAT_SHOW_YEAR |
                DateUtils.FORMAT_NO_MONTH_DAY;
        return DateUtils.formatDateTime(context, mItems.get(0).dateInMs, flags);
    }
}
