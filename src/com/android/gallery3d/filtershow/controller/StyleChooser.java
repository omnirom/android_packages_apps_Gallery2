package com.android.gallery3d.filtershow.controller;

import android.app.ActionBar.LayoutParams;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout;

import com.android.gallery3d.R;
import com.android.gallery3d.filtershow.pipeline.RenderingRequest;
import com.android.gallery3d.filtershow.pipeline.RenderingRequestCaller;
import com.android.gallery3d.filtershow.editors.Editor;

import java.util.Vector;

public class StyleChooser implements Control {
    private final String LOGTAG = "StyleChooser";
    protected ParameterStyles mParameter;
    protected LinearLayout mLinearLayout;
    protected Editor mEditor;
    private View mTopView;
    private Vector<ImageButton> mIconButton = new Vector<ImageButton>();
    protected int mLayoutID = R.layout.filtershow_control_style_chooser;
    private int mTransparent;
    private int mSelected;
    private int mSelectedStyle;
    private int mSelectedBorderWith;

    @Override
    public void setUp(ViewGroup container, Parameter parameter, Editor editor) {
        container.removeAllViews();
        mEditor = editor;
        Context context = container.getContext();
        mParameter = (ParameterStyles) parameter;
        LayoutInflater inflater =
                (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mTopView = inflater.inflate(mLayoutID, container, true);
        mLinearLayout = (LinearLayout) mTopView.findViewById(R.id.listStyles);
        mTopView.setVisibility(View.VISIBLE);
        int n = mParameter.getNumberOfStyles();
        mIconButton.clear();
        Resources res = context.getResources();
        mTransparent  = res.getColor(R.color.color_chooser_unslected_border);
        mSelected    = res.getColor(R.color.color_chooser_slected_border);
        mSelectedBorderWith = res.getDimensionPixelSize(R.dimen.selected_border_width);
        int dim = res.getDimensionPixelSize(R.dimen.draw_color_icon_dim);
        LayoutParams lp = new LayoutParams(dim, dim);
        for (int i = 0; i < n; i++) {
            final ImageButton button = new ImageButton(context);
            button.setScaleType(ScaleType.CENTER_INSIDE);
            button.setLayoutParams(lp);
            button.setBackgroundResource(R.drawable.filtershow_color_picker_circle);
            mIconButton.add(button);

            GradientDrawable sd = ((GradientDrawable) button.getBackground());
            sd.setColor(mTransparent);
            sd.setStroke(mSelectedBorderWith, (mSelectedStyle == i) ? mSelected : mTransparent);

            final int buttonNo = i;
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View arg0) {
                    mSelectedStyle = buttonNo;
                    mParameter.setSelected(buttonNo);
                    resetBorders();
                }
            });
            mLinearLayout.addView(button);
            mParameter.getIcon(i, new BitmapCaller() {
                @Override
                public void available(Bitmap bmap) {

                    if (bmap == null) {
                        return;
                    }
                    button.setImageBitmap(tintImage(bmap, getAttrColor(context, android.R.attr.colorControlNormal)));
                }
            });
        }
    }
    
    public static Bitmap tintImage(Bitmap bitmap, int color) {
        Paint paint = new Paint();
        paint.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
        Bitmap bitmapResult = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmapResult);
        canvas.drawBitmap(bitmap, 0, 0, paint);
        return bitmapResult;
    }

    private int getAttrColor(Context context, Integer attr) {
        TypedArray ta = context.obtainStyledAttributes(new int[]{attr});
        int color = ta.getColor(0, 0);
        ta.recycle();
        return color;
    }

    @Override
    public View getTopView() {
        return mTopView;
    }

    @Override
    public void setPrameter(Parameter parameter) {
        mParameter = (ParameterStyles) parameter;
        updateUI();
    }

    @Override
    public void updateUI() {
        if (mParameter == null) {
            return;
        }
        mSelectedStyle = mParameter.getSelected();
        resetBorders();
    }

    private void resetBorders() {
        for (int i = 0; i < mParameter.getNumberOfStyles(); i++) {
            final ImageButton button = mIconButton.get(i);
            GradientDrawable sd = ((GradientDrawable) button.getBackground());
            sd.setColor(mTransparent);
            sd.setStroke(mSelectedBorderWith, (mSelectedStyle == i) ? mSelected : mTransparent);
        }
    }
}
