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

package com.android.gallery3d.filtershow.colorpicker;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ToggleButton;

import androidx.appcompat.app.AlertDialog;

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.FilterShowActivity;

import java.util.Arrays;

public class ColorPickerDialog  {
    ToggleButton mSelectedButton;
    ColorHueView mColorHueView;
    ColorSVRectView mColorSVRectView;
    ColorOpacityView mColorOpacityView;
    ColorCompareView mColorCompareView;
    ColorListener mListener;
    Context mContext;
    private static final int OK_ID = 1;
    
    float[] mHSVO = new float[4]; // hue=0..360, sat & val opacity = 0...1
    float[] mOrigHSVO;
    float[] mOrigColorHSVO;

    public static AlertDialog newInstance(Context context, ColorListener cl, float[] c) {
        ColorPickerDialog frag = new ColorPickerDialog(context, cl, c);
        return frag.doCreateDialog();
    }
    
    public ColorPickerDialog(Context context, ColorListener cl, float[] c) {
        mContext = context;
        mListener = cl;
        mOrigHSVO = Arrays.copyOf(c, 4);
        mOrigColorHSVO = Arrays.copyOf(c, 4);
    }

    private View doCreateView() {
        LayoutInflater inflater =
                (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.filtershow_color_picker, null);

        mColorHueView = (ColorHueView) view.findViewById(R.id.ColorHueView);
        mColorSVRectView = (ColorSVRectView) view.findViewById(R.id.colorRectView);
        mColorOpacityView = (ColorOpacityView) view.findViewById(R.id.colorOpacityView);
        mColorCompareView = (ColorCompareView) view.findViewById(R.id.btnSelect);

        float[] hsvo = new float[] {
                123, .9f, 1, 1 };

        ColorListener [] c = {mColorCompareView,mColorSVRectView,mColorOpacityView,mColorHueView};
        for (int i = 0; i < c.length; i++) {
            c[i].setColor(hsvo);
            for (int j = 0; j < c.length; j++) {
                if (i==j) {
                     continue;
                }
               c[i].addColorListener(c[j]);
            }
        }

        ColorListener colorListener = new ColorListener(){
            @Override
            public void setColor(float[] hsvo) {
                System.arraycopy(hsvo, 0, mHSVO, 0, mHSVO.length);
                int color = Color.HSVToColor(hsvo);
                setButtonColor(mSelectedButton, hsvo);
            }

            @Override
            public void addColorListener(ColorListener l) {
            }
        };

        for (int i = 0; i < c.length; i++) {
            c[i].addColorListener(colorListener);
        }
        
        setColor(mOrigHSVO);
        setOrigColor(mOrigColorHSVO);
        return view;
    }

    public void setOrigColor(float[] hsvo) {
        mColorCompareView.setOrigColor(hsvo);
    }

    public void setColor(float[] hsvo) {
        mColorOpacityView.setColor(hsvo);
        mColorHueView.setColor(hsvo);
        mColorSVRectView.setColor(hsvo);
        mColorCompareView.setColor(hsvo);
    }

    private void setButtonColor(ToggleButton button, float[] hsv) {
        if (button == null) {
            return;
        }
        int color = Color.HSVToColor(hsv);
        button.setBackgroundColor(color);
        float[] fg = new float[] {
                (hsv[0] + 180) % 360,
                hsv[1],
                        (hsv[2] > .5f) ? .1f : .9f
        };
        button.setTextColor(Color.HSVToColor(fg));
        button.setTag(hsv);
    }
    
    private AlertDialog doCreateDialog() {
        View view = doCreateView();
        
        return new AlertDialog.Builder(mContext)
                .setTitle(mContext.getString(R.string.draw_color))
                .setView(view)
                .setPositiveButton(android.R.string.ok,
                        (dialogInterface, i) -> this.onClickId(OK_ID))
                .setNegativeButton(android.R.string.cancel, null)
                .create();
     }
    
    private void onClickId(int id) {
        switch (id) {
            case OK_ID:
                mListener.setColor(mHSVO);
                break;
        }
    }
}
