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

package com.android.gallery3d.filtershow.editors;

import android.app.ActionBar;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PaintFlagsDrawFilter;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.colorpicker.ColorCompareView;
import com.android.gallery3d.filtershow.colorpicker.ColorHueView;
import com.android.gallery3d.filtershow.colorpicker.ColorListener;
import com.android.gallery3d.filtershow.colorpicker.ColorOpacityView;
import com.android.gallery3d.filtershow.colorpicker.ColorSVRectView;
import com.android.gallery3d.filtershow.controller.BasicParameterInt;
import com.android.gallery3d.filtershow.controller.BasicParameterStyle;
import com.android.gallery3d.filtershow.controller.BitmapCaller;
import com.android.gallery3d.filtershow.controller.ParameterColor;
import com.android.gallery3d.filtershow.filters.FilterDrawRepresentation;

import java.util.Arrays;

public class EditorDrawTabletUI {
    private EditorDraw mEditorDraw;
    private int[] mBrushIcons;
    private static int sIconDim = 120;
    private int mSelectedColorButton;
    private int mSelectedStyleButton;
    private FilterDrawRepresentation mRep;
    private ImageButton[] mColorButton;
    private ImageButton[] mStyleButton;
    private ColorHueView mHueView;
    private ColorSVRectView mSatValView;
    private ColorOpacityView mOpacityView;
    private ColorCompareView mColorCompareView;
    private TextView mDrawSizeValue;
    private Context mContext;

    private int[] mBasColors;
    private int mSelected;
    private int mSelectedBorderWith;
    private int mTransparent;
    private SeekBar mdrawSizeSeekBar;
    private int[] ids = {
            R.id.draw_color_button01,
            R.id.draw_color_button02,
            R.id.draw_color_button03,
            R.id.draw_color_button04,
            R.id.draw_color_button05,
            R.id.draw_color_button06,
    };

    public void setDrawRepresentation(FilterDrawRepresentation rep) {
        mRep = rep;
        BasicParameterInt size;
        size = (BasicParameterInt) mRep.getParam(FilterDrawRepresentation.PARAM_SIZE);
        mdrawSizeSeekBar.setMax(size.getMaximum() - size.getMinimum());
        mdrawSizeSeekBar.setProgress(size.getValue());

        ParameterColor color;
        color = (ParameterColor) mRep.getParam(FilterDrawRepresentation.PARAM_COLOR);
        color.setValue(mBasColors[mSelectedColorButton]);
        BasicParameterStyle style;
        style = (BasicParameterStyle) mRep.getParam(FilterDrawRepresentation.PARAM_STYLE);
        style.setSelected(mSelectedStyleButton);
    }

    public EditorDrawTabletUI(EditorDraw editorDraw, Context context, LinearLayout lp) {
        mContext = context;
        mEditorDraw = editorDraw;
        mBasColors = editorDraw.mBasColors;
        mBrushIcons = editorDraw.brushIcons;
        Resources res = context.getResources();
        sIconDim = res.getDimensionPixelSize(R.dimen.draw_style_icon_dim);
        mTransparent = res.getColor(R.color.color_chooser_unslected_border);
        mSelected = res.getColor(R.color.color_chooser_slected_border);
        mSelectedBorderWith = res.getDimensionPixelSize(R.dimen.selected_border_width);

        LinearLayout buttonContainer = (LinearLayout) lp.findViewById(R.id.listStyles);

        mdrawSizeSeekBar = (SeekBar) lp.findViewById(R.id.drawSizeSeekBar);
        mDrawSizeValue = (TextView) lp.findViewById(R.id.drawSizeValue);

        mdrawSizeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                BasicParameterInt size;
                size = (BasicParameterInt) mRep.getParam(FilterDrawRepresentation.PARAM_SIZE);
                size.setValue(progress + size.getMinimum());
                mEditorDraw.commitLocalRepresentation();
                int val  = progress + size.getMinimum();
                mDrawSizeValue.setText(((val>0)?"+":"")+val);
            }
        });

        ActionBar.LayoutParams params = new ActionBar.LayoutParams(sIconDim, sIconDim);
        mStyleButton = new ImageButton[mBrushIcons.length];
        for (int i = 0; i < mBrushIcons.length; i++) {
            final ImageButton button = new ImageButton(context);
            mStyleButton[i] = button;
            button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            button.setLayoutParams(params);
            editorDraw.computeIcon(i, new BitmapCaller() {
                @Override
                public void available(Bitmap bmap) {
                    if (bmap == null) {
                        return;
                    }
                    button.setImageBitmap(bmap);
                }
            });
            button.setBackgroundResource(R.drawable.filtershow_color_picker_circle);
            buttonContainer.addView(button);
            final int current = i;

            GradientDrawable sd = ((GradientDrawable) button.getBackground());
            sd.setStroke(mSelectedBorderWith, (mSelectedStyleButton == i) ? mSelected : mTransparent);

            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    mSelectedStyleButton = current;
                    if (mRep == null) {
                        return;
                    }
                    BasicParameterStyle style = (BasicParameterStyle)
                            mRep.getParam(FilterDrawRepresentation.PARAM_STYLE);
                    style.setSelected(current);
                    resetStyle();
                    mEditorDraw.commitLocalRepresentation();
                }
            });
        }

        final LinearLayout ctls = (LinearLayout) lp.findViewById(R.id.controls);
        final LinearLayout pick = (LinearLayout) lp.findViewById(R.id.colorPicker);
        ImageView b = (ImageView) lp.findViewById(R.id.draw_color_popupbutton);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                boolean b = ctls.getVisibility() == View.VISIBLE;
                ctls.setVisibility((b) ? View.GONE : View.VISIBLE);
                pick.setVisibility((!b) ? View.GONE : View.VISIBLE);
            }
        }
        );

        LinearLayout.LayoutParams colorParams = new LinearLayout.LayoutParams(sIconDim, sIconDim);
        colorParams.gravity= Gravity.CENTER;
        mColorButton = new ImageButton[ids.length];
        for (int i = 0; i < ids.length; i++) {
            final ImageButton button = (ImageButton) lp.findViewById(ids[i]);
            button.setLayoutParams(colorParams);
            button.setScaleType(ScaleType.CENTER_INSIDE);
            button.setImageDrawable(createColorImage(mBasColors[i]));

            mColorButton[i] = button;
            float[] hsvo = new float[4];
            Color.colorToHSV(mBasColors[i], hsvo);
            hsvo[3] = (0xFF & (mBasColors[i] >> 24)) / (float) 255;
            mColorButton[i].setTag(hsvo);
            GradientDrawable sd = ((GradientDrawable) mColorButton[i].getBackground());
            sd.setStroke(mSelectedBorderWith, (0 == i) ? mSelected : mTransparent);

            final int buttonNo = i;
            mColorButton[i].setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View arg0) {

                    mSelectedColorButton = buttonNo;
                    float[] hsvo = Arrays.copyOf((float[]) mColorButton[buttonNo].getTag(), 4);
                    resetBorders();
                    if (mRep == null) {
                        return;
                    }
                    ParameterColor pram;
                    pram = (ParameterColor) mRep.getParam(FilterDrawRepresentation.PARAM_COLOR);
                    pram.setValue(mBasColors[mSelectedColorButton]);
                    mEditorDraw.commitLocalRepresentation();
                    mHueView.setColor(hsvo);
                    mSatValView.setColor(hsvo);
                    mOpacityView.setColor(hsvo);
                    mColorCompareView.setColor(hsvo);
                    mColorCompareView.setOrigColor(hsvo);
                }
            });
        }

        mHueView = (ColorHueView) lp.findViewById(R.id.ColorHueView);
        mSatValView = (ColorSVRectView) lp.findViewById(R.id.colorRectView);
        mOpacityView = (ColorOpacityView) lp.findViewById(R.id.colorOpacityView);
        mColorCompareView = (ColorCompareView) lp.findViewById(R.id.btnSelect);

        float[] hsvo = new float[4];
        Color.colorToHSV(mBasColors[0], hsvo);
        hsvo[3] = (0xFF & (mBasColors[0] >> 24)) / (float) 255;

        mColorCompareView.setOrigColor(hsvo);
        ColorListener[] colorViews = {mHueView, mSatValView, mOpacityView, mColorCompareView};
        for (int i = 0; i < colorViews.length; i++) {
            colorViews[i].setColor(hsvo);

            for (int j = 0; j < colorViews.length; j++) {
                if (i == j) {
                    continue;
                }
                colorViews[i].addColorListener(colorViews[j]);
            }
        }
        ColorListener colorListener = new ColorListener() {

            @Override
            public void setColor(float[] hsvo) {
                int color = Color.HSVToColor((int) (hsvo[3] * 255), hsvo);
                final ImageButton b = mColorButton[mSelectedColorButton];
                float[] f = (float[]) b.getTag();
                System.arraycopy(hsvo, 0, f, 0, 4);
                mBasColors[mSelectedColorButton] = color;
                b.setImageDrawable(createColorImage(color));
                resetBorders();
                ParameterColor pram;
                pram = (ParameterColor) mRep.getParam(FilterDrawRepresentation.PARAM_COLOR);
                pram.setValue(color);
                mEditorDraw.commitLocalRepresentation();
            }

            @Override
            public void addColorListener(ColorListener l) {
            }
        };

        for (int i = 0; i < colorViews.length; i++) {
            colorViews[i].addColorListener(colorListener);
        }

    }

    public void resetStyle() {
        for (int i = 0; i < mStyleButton.length; i++) {
            final ImageButton button = mStyleButton[i];

            GradientDrawable sd = ((GradientDrawable)  button.getBackground());
            sd.setStroke(mSelectedBorderWith, (mSelectedStyleButton == i) ? mSelected : mTransparent);
        }
    }

    private void resetBorders() {
        for (int i = 0; i < ids.length; i++) {
            final ImageButton button = mColorButton[i];

            GradientDrawable sd = ((GradientDrawable) button.getBackground());
            sd.setStroke(mSelectedBorderWith, (mSelectedColorButton == i) ? mSelected : mTransparent);
        }
    }

    private BitmapDrawable createColorImage(int color){
        final int width = mContext.getResources().getDimensionPixelSize(R.dimen.color_rect_width);
        final Canvas canvas = new Canvas();
        canvas.setDrawFilter(new PaintFlagsDrawFilter(Paint.ANTI_ALIAS_FLAG,Paint.FILTER_BITMAP_FLAG));
        final Bitmap bmp = Bitmap.createBitmap(width, width, Bitmap.Config.ARGB_8888);
        canvas.setBitmap(bmp);
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        canvas.drawRect(0, 0, width, width, paint);
        return new BitmapDrawable(mContext.getResources(), bmp);
    }
}
