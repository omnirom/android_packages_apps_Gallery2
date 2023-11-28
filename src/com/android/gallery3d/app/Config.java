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

package com.android.gallery3d.app;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;

import com.android.gallery3d.R;
import com.android.gallery3d.ui.AlbumSetSlotRenderer;
import com.android.gallery3d.ui.SlotView;
import com.android.gallery3d.util.GalleryUtils;

final class Config {
    public static class AlbumSetPage {
        public SlotView.Spec slotViewSpec;
        public AlbumSetSlotRenderer.LabelSpec labelSpec;
        public int paddingTop;
        public int paddingBottom;
        public int paddingLeft;
        public int paddingRight;
        public int placeholderColor;

        public AlbumSetPage(Context context) {
            Resources r = context.getResources();

            placeholderColor = r.getColor(R.color.albumset_placeholder);

            slotViewSpec = new SlotView.Spec();
            slotViewSpec.rowsLand = r.getInteger(R.integer.albumset_rows_land);
            slotViewSpec.rowsPort = r.getInteger(R.integer.albumset_rows_port);

            int zoomLevel = GalleryUtils.getAlbumsetZoomLevel(context);
            slotViewSpec.zoomLevel = zoomLevel;

            slotViewSpec.colsLandMin = r.getInteger(R.integer.albumset_cols_land_min);
            slotViewSpec.colsPortMin = r.getInteger(R.integer.albumset_cols_port_min);
            slotViewSpec.colsLandMax = r.getInteger(R.integer.albumset_cols_land_max);
            slotViewSpec.colsPortMax = r.getInteger(R.integer.albumset_cols_port_max);
            slotViewSpec.slotGap = r.getDimensionPixelSize(R.dimen.albumset_slot_gap);
            slotViewSpec.slotHeightAdditional = 0;
            slotViewSpec.usePadding = r.getBoolean(R.bool.config_grid_use_padding);

            paddingTop = r.getDimensionPixelSize(R.dimen.albumset_padding_top);
            paddingBottom = r.getDimensionPixelSize(R.dimen.albumset_padding_bottom);
            paddingLeft = r.getDimensionPixelSize(R.dimen.albumset_padding_left);
            paddingRight = r.getDimensionPixelSize(R.dimen.albumset_padding_right);

            labelSpec = new AlbumSetSlotRenderer.LabelSpec();
            labelSpec.labelBackgroundHeight = r.getDimensionPixelSize(
                    R.dimen.albumset_label_background_height);
            labelSpec.titleOffset = r.getDimensionPixelSize(
                    R.dimen.albumset_title_offset);
            labelSpec.titleFontSize = r.getDimensionPixelSize(
                    R.dimen.albumset_title_font_size);
            labelSpec.countFontSize = r.getDimensionPixelSize(
                    R.dimen.albumset_count_font_size);
            labelSpec.leftMargin = r.getDimensionPixelSize(
                    R.dimen.albumset_left_margin);
            labelSpec.titleRightMargin = r.getDimensionPixelSize(
                    R.dimen.albumset_title_right_margin);
            /*labelSpec.backgroundColor = r.getColor(
                    R.color.albumset_label_background);
            labelSpec.titleColor = r.getColor(R.color.albumset_label_title);
            labelSpec.countColor = r.getColor(R.color.albumset_label_count);*/
            labelSpec.backgroundColor = getAttrColor(context, android.R.attr.colorBackground);
            labelSpec.titleColor = getAttrColor(context, android.R.attr.textColorSecondary);
            labelSpec.countColor = getAttrColor(context, android.R.attr.textColorSecondary);
        }

        private int getAttrColor(Context context, Integer attr) {
            TypedArray ta = context.obtainStyledAttributes(new int[]{attr});
            int color = ta.getColor(0, 0);
            ta.recycle();
            return color;
        }
    }

    public static class AlbumPage {
        public SlotView.Spec slotViewSpec;
        public int paddingTop;
        public int paddingBottom;
        public int paddingLeft;
        public int paddingRight;
        public int placeholderColor;

        public AlbumPage(Context context) {
            Resources r = context.getResources();

            placeholderColor = r.getColor(R.color.album_placeholder);

            slotViewSpec = new SlotView.Spec();
            slotViewSpec.rowsLand = r.getInteger(R.integer.album_rows_land);
            slotViewSpec.rowsPort = r.getInteger(R.integer.album_rows_port);

            int zoomLevel = GalleryUtils.getAlbumZoomLevel(context);
            slotViewSpec.zoomLevel = zoomLevel;

            slotViewSpec.colsLandMin = r.getInteger(R.integer.album_cols_land_min);
            slotViewSpec.colsPortMin = r.getInteger(R.integer.album_cols_port_min);
            slotViewSpec.colsLandMax = r.getInteger(R.integer.album_cols_land_max);
            slotViewSpec.colsPortMax = r.getInteger(R.integer.album_cols_port_max);
            slotViewSpec.slotGap = r.getDimensionPixelSize(R.dimen.album_slot_gap);
            slotViewSpec.usePadding = r.getBoolean(R.bool.config_grid_use_padding);

            paddingTop = r.getDimensionPixelSize(R.dimen.album_padding_top);
            paddingBottom = r.getDimensionPixelSize(R.dimen.album_padding_bottom);
            paddingLeft = r.getDimensionPixelSize(R.dimen.album_padding_left);
            paddingRight = r.getDimensionPixelSize(R.dimen.album_padding_right);
        }
    }
}

