/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

import com.android.gallery3d.R;
import com.android.gallery3d.data.DataSourceType;
import com.android.gallery3d.data.GalleryBitmapPool;
import com.android.gallery3d.util.ThreadPool;
import com.android.gallery3d.util.ThreadPool.JobContext;

import java.util.Locale;

public class AlbumLabelMaker {
    private static final int BORDER_SIZE = 0;

    private final AlbumSetSlotRenderer.LabelSpec mSpec;
    private final TextPaint mTitlePaint;
    private final TextPaint mCountPaint;
    private final Context mContext;

    private int mLabelWidth;
    private int mBitmapWidth;
    private int mBitmapHeight;

    public AlbumLabelMaker(Context context, AlbumSetSlotRenderer.LabelSpec spec) {
        mContext = context;
        mSpec = spec;
        mTitlePaint = getTextPaint(spec.titleFontSize, spec.titleColor);
        mCountPaint = getTextPaint(spec.countFontSize, spec.countColor);
    }

    public static int getBorderSize() {
        return BORDER_SIZE;
    }

    private static TextPaint getTextPaint(int textSize, int color) {
        TextPaint paint = new TextPaint();
        paint.setTextSize(textSize);
        paint.setAntiAlias(true);
        paint.setColor(color);
        paint.setTypeface(Typeface.create("sans-serif-condensed", Typeface.NORMAL));
        return paint;
    }

    public synchronized void setLabelWidth(int width) {
        if (mLabelWidth == width) return;
        mLabelWidth = width;
        int borders = 2 * BORDER_SIZE;
        mBitmapWidth = width + borders;
        mBitmapHeight = mSpec.labelBackgroundHeight + borders;
    }

    public ThreadPool.Job<Bitmap> requestLabel(
            String title, String count, int sourceType) {
        return new AlbumLabelJob(title, count, sourceType);
    }

    static void drawText(Canvas canvas,
            int x, int y, String text, int lengthLimit, TextPaint p) {
        // The TextPaint cannot be used concurrently
        synchronized (p) {
            text = TextUtils.ellipsize(
                    text, p, lengthLimit, TextUtils.TruncateAt.END).toString();
            canvas.drawText(text, x, y - p.getFontMetricsInt().ascent, p);
        }
    }

    private class AlbumLabelJob implements ThreadPool.Job<Bitmap> {
        private final String mTitle;
        private final String mCount;
        private final int mSourceType;

        public AlbumLabelJob(String title, String count, int sourceType) {
            mTitle = title;
            mCount = count;
            mSourceType = sourceType;
        }

        @Override
        public Bitmap run(JobContext jc) {
            AlbumSetSlotRenderer.LabelSpec s = mSpec;

            String title = mTitle;
            String count = mCount;

            Bitmap bitmap;
            int labelWidth;
            int countWidth = (int) mCountPaint.measureText(count);

            synchronized (this) {
                labelWidth = mLabelWidth;
                bitmap = GalleryBitmapPool.getInstance().get(mBitmapWidth, mBitmapHeight);
            }

            if (bitmap == null) {
                int borders = 2 * BORDER_SIZE;
                bitmap = Bitmap.createBitmap(labelWidth + borders,
                        s.labelBackgroundHeight + borders, Config.ARGB_8888);
            }

            Canvas canvas = new Canvas(bitmap);
            canvas.clipRect(BORDER_SIZE, BORDER_SIZE,
                    bitmap.getWidth() - BORDER_SIZE,
                    bitmap.getHeight() - BORDER_SIZE);
            canvas.drawColor(mSpec.backgroundColor, PorterDuff.Mode.SRC);

            canvas.translate(BORDER_SIZE, BORDER_SIZE);

            int lineHeight = s.labelBackgroundHeight / 2;
            if (View.LAYOUT_DIRECTION_RTL == TextUtils
                    .getLayoutDirectionFromLocale(Locale.getDefault())) {// RTL
                // draw title
                if (jc.isCancelled()) return null;
                int strLength = (int) mTitlePaint.measureText(title);
                int x = labelWidth - s.leftMargin - strLength;
                int y = (lineHeight - s.titleFontSize) / 2;
                drawText(canvas, x, y, title, labelWidth - s.leftMargin - x -
                        s.titleRightMargin, mTitlePaint);

                // draw count
                if (jc.isCancelled()) return null;
                strLength = (int) mCountPaint.measureText(count);
                x = labelWidth - s.leftMargin - strLength;
                y = lineHeight + (lineHeight - s.countFontSize) / 2 - s.titleOffset * 2;
                drawText(canvas, x, y, count,
                        labelWidth - x, mCountPaint);
            } else { // LTR
                // draw title
                if (jc.isCancelled()) return null;
                int x = s.leftMargin;
                int y = (lineHeight - s.titleFontSize) / 2;
                drawText(canvas, x, y, title, labelWidth - s.leftMargin - x -
                        s.titleRightMargin, mTitlePaint);

                // draw count
                if (jc.isCancelled()) return null;
                y = lineHeight + (lineHeight - s.countFontSize) / 2 - s.titleOffset * 2;
                drawText(canvas, x, y, count,
                        labelWidth - x, mCountPaint);
            }

            return bitmap;
        }
    }

    public void recycleLabel(Bitmap label) {
        GalleryBitmapPool.getInstance().put(label);
    }
}
