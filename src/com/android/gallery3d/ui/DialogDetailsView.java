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

package com.android.gallery3d.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.android.gallery3d.R;
import com.android.gallery3d.app.AbstractGalleryActivity;
import com.android.gallery3d.common.Utils;
import com.android.gallery3d.data.MediaDetails;
import com.android.gallery3d.ui.DetailsAddressResolver.AddressResolvingListener;
import com.android.gallery3d.ui.DetailsHelper.CloseListener;
import com.android.gallery3d.ui.DetailsHelper.DetailsSource;
import com.android.gallery3d.ui.DetailsHelper.DetailsViewContainer;
import com.android.gallery3d.ui.DetailsHelper.ResolutionResolvingListener;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map.Entry;

public class DialogDetailsView implements DetailsViewContainer {
    @SuppressWarnings("unused")
    private static final String TAG = "DialogDetailsView";

    private final AbstractGalleryActivity mActivity;
    private DetailsAdapter mAdapter;
    private MediaDetails mDetails;
    private final DetailsSource mSource;
    private int mIndex;
    private Dialog mDialog;
    private CloseListener mListener;

    public DialogDetailsView(AbstractGalleryActivity activity, DetailsSource source) {
        mActivity = activity;
        mSource = source;
    }

    @Override
    public void show() {
        reloadDetails();
        mDialog.show();
    }

    @Override
    public void hide() {
        mDialog.hide();
    }

    @Override
    public void reloadDetails() {
        int index = mSource.setIndex();
        if (index == -1) return;
        MediaDetails details = mSource.getDetails();
        if (details != null) {
            if (mIndex == index && mDetails == details) return;
            mIndex = index;
            mDetails = details;
            setDetails(details);
        }
    }

    private void setDetails(MediaDetails details) {
        mAdapter = new DetailsAdapter(details, mActivity);
        String title = String.format(
                mActivity.getAndroidContext().getString(R.string.details_title),
                mIndex + 1, mSource.size());
        ListView detailsList = (ListView) LayoutInflater.from(mActivity.getAndroidContext()).inflate(
                R.layout.details_list, null, false);
        detailsList.setAdapter(mAdapter);
        mDialog = new AlertDialog.Builder(mActivity)
            .setView(detailsList)
            .setTitle(title)
            .setPositiveButton(R.string.close, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int whichButton) {
                    mDialog.dismiss();
                }
            })
            .create();

        mDialog.setOnDismissListener(new OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                if (mListener != null) {
                    mListener.onClose();
                }
            }
        });
    }

    @Override
    public void setCloseListener(CloseListener listener) {
        mListener = listener;
    }
}
