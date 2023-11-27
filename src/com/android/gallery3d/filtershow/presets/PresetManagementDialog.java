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

package com.android.gallery3d.filtershow.presets;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.FilterShowActivity;

public class PresetManagementDialog extends DialogFragment {
    private UserPresetsAdapter mAdapter;
    private EditText mEditText;
    private static final int CANCEL_ID = 0;
    private static final int OK_ID = 1;

    private View doCreateView() {
        View view = getLayoutInflater().inflate(R.layout.filtershow_presets_management_dialog, null);
        FilterShowActivity activity = (FilterShowActivity) getActivity();
        mAdapter = activity.getUserPresetsAdapter();
        mEditText = (EditText) view.findViewById(R.id.editView);
        return view;
    }
    
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        View view = doCreateView();
        
        return new AlertDialog.Builder(getActivity())
                .setTitle(getString(R.string.filtershow_save_preset))
                .setView(view)
                .setPositiveButton(android.R.string.ok,
                        (dialogInterface, i) -> this.onClickId(OK_ID))
                .setNegativeButton(android.R.string.cancel,
                        (dialogInterface, i) -> this.onClickId(CANCEL_ID))
                .create();
     }
    
    public void onClickId(int id) {
        FilterShowActivity activity = (FilterShowActivity) getActivity();
        switch (id) {
            case CANCEL_ID:
                mAdapter.clearChangedRepresentations();
                mAdapter.clearDeletedRepresentations();
                activity.updateUserPresetsFromAdapter(mAdapter);
                break;
            case OK_ID:
                String text = String.valueOf(mEditText.getText());
                activity.saveCurrentImagePreset(text);
                mAdapter.updateCurrent();
                activity.updateUserPresetsFromAdapter(mAdapter);
                break;
        }
    }
}
