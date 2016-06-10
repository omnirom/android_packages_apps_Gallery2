package com.android.gallery3d.filtershow.controller;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PaintFlagsDrawFilter;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout;

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.colorpicker.ColorListener;
import com.android.gallery3d.filtershow.colorpicker.ColorPickerDialog;
import com.android.gallery3d.filtershow.editors.Editor;

import java.util.Arrays;
import java.util.Vector;

public class ColorChooser implements Control {
    private final String LOGTAG = "StyleChooser";
    protected ParameterColor mParameter;
    protected LinearLayout mLinearLayout;
    protected Editor mEditor;
    private View mTopView;
    private Vector<Button> mIconButton = new Vector<Button>();
    protected int mLayoutID = R.layout.filtershow_control_color_chooser;
    Context mContext;
    private int mTransparent;
    private int mSelected;
    private static final int OPACITY_OFFSET = 3;
    private int[] mButtonsID = {
            R.id.draw_color_button01,
            R.id.draw_color_button02,
            R.id.draw_color_button03,
            R.id.draw_color_button04,
            R.id.draw_color_button05,
            R.id.draw_color_button06,
    };
    private ImageButton[] mButton = new ImageButton[mButtonsID.length];

    private int mSelectedButton = 0;

    @Override
    public void setUp(ViewGroup container, Parameter parameter, Editor editor) {
        container.removeAllViews();
        Resources res = container.getContext().getResources();
        mTransparent  = res.getColor(R.color.color_chooser_unslected_border);
        mSelected    = res.getColor(R.color.color_chooser_slected_border);
        mEditor = editor;
        mContext = container.getContext();
        int iconDim = res.getDimensionPixelSize(R.dimen.draw_style_icon_dim);
        mParameter = (ParameterColor) parameter;
        LayoutInflater inflater =
                (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mTopView = inflater.inflate(mLayoutID, container, true);
        mLinearLayout = (LinearLayout) mTopView.findViewById(R.id.listStyles);
        mTopView.setVisibility(View.VISIBLE);

        mIconButton.clear();
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(iconDim, iconDim);
        lp.gravity= Gravity.CENTER;
        int [] palette = mParameter.getColorPalette();
        for (int i = 0; i < mButtonsID.length; i++) {
            final ImageButton button = (ImageButton) mTopView.findViewById(mButtonsID[i]);
            button.setLayoutParams(lp);
            button.setScaleType(ScaleType.CENTER_INSIDE);
            button.setImageDrawable(createColorImage(palette[i]));
            mButton[i] = button;
            float[] hsvo = new float[4];
            Color.colorToHSV(palette[i], hsvo);
            hsvo[OPACITY_OFFSET] = (0xFF & (palette[i] >> 24)) / (float) 255;
            button.setTag(hsvo);
            GradientDrawable sd = ((GradientDrawable) button.getBackground());
            sd.setColor(mTransparent);
            sd.setStroke(3, (mSelectedButton == i) ? mSelected : mTransparent);

            final int buttonNo = i;
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View arg0) {
                    selectColor(arg0, buttonNo);
                }
            });
        }

        if (mParameter != null) {
            int value = mParameter.getValue();
            if (value == 0) {
                selectColor(mButton[0], 0);
            }
        }
        ImageView button = (ImageView) mTopView.findViewById(R.id.draw_color_popupbutton);

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                showColorPicker();
            }
        });

    }

    public void setColorSet(int[] basColors) {
        int []palette = mParameter.getColorPalette();
        for (int i = 0; i < palette.length; i++) {
            palette[i] = basColors[i];
            float[] hsvo = new float[4];
            Color.colorToHSV(palette[i], hsvo);
            hsvo[OPACITY_OFFSET] = (0xFF & (palette[i] >> 24)) / (float) 255;
            mButton[i].setTag(hsvo);
            mButton[i].setImageDrawable(createColorImage(palette[i]));
        }

    }

    public int[] getColorSet() {
        return  mParameter.getColorPalette();
    }

    private void resetBorders() {
        int []palette = mParameter.getColorPalette();
        for (int i = 0; i < mButtonsID.length; i++) {
            final ImageButton button = mButton[i];
            GradientDrawable sd = ((GradientDrawable) button.getBackground());
            sd.setStroke(3, (mSelectedButton == i) ? mSelected : mTransparent);
        }
    }

    public void selectColor(View button, int buttonNo) {
        mSelectedButton = buttonNo;
        float[] hsvo = (float[]) button.getTag();
        mParameter.setValue(Color.HSVToColor((int) (hsvo[OPACITY_OFFSET] * 255), hsvo));
        resetBorders();
        mEditor.commitLocalRepresentation();
    }

    @Override
    public View getTopView() {
        return mTopView;
    }

    @Override
    public void setPrameter(Parameter parameter) {
        mParameter = (ParameterColor) parameter;
        updateUI();
    }

    @Override
    public void updateUI() {
        if (mParameter == null) {
            return;
        }

        int value = mParameter.getValue();
        for (int i = 0; i < mButtonsID.length; i++) {
            final ImageButton button = mButton[i];
            float[] hsvo = (float[]) button.getTag();
            int buttonValue = Color.HSVToColor((int) (hsvo[OPACITY_OFFSET] * 255), hsvo);
            if (buttonValue == value) {
                mSelectedButton = i;
                break;
            }
        }

        resetBorders();
    }

    public void changeSelectedColor(float[] hsvo) {
        int []palette = mParameter.getColorPalette();
        int c = Color.HSVToColor((int) (hsvo[3] * 255), hsvo);
        final ImageButton button = mButton[mSelectedButton];
        button.setImageDrawable(createColorImage(c));
        palette[mSelectedButton] = c;
        mParameter.setValue(Color.HSVToColor((int) (hsvo[OPACITY_OFFSET] * 255), hsvo));
        button.setTag(hsvo);
        mEditor.commitLocalRepresentation();
        button.invalidate();
    }

    public void showColorPicker() {
        ColorListener cl = new ColorListener() {
            @Override
            public void setColor(float[] hsvo) {
                changeSelectedColor(hsvo);
            }
            @Override
            public void addColorListener(ColorListener l) {
            }
        };
        ColorPickerDialog cpd = new ColorPickerDialog(mContext, cl);
        float[] c = (float[]) mButton[mSelectedButton].getTag();
        cpd.setColor(Arrays.copyOf(c, 4));
        cpd.setOrigColor(Arrays.copyOf(c, 4));
        cpd.show();
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
