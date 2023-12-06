package com.android.gallery3d.screensaver;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import com.android.gallery3d.app.FilterUtils;
import com.android.gallery3d.data.DataManager;

import java.util.HashSet;
import java.util.Set;

public class ScreenSaverConfig {
    private static final String KEY_RANDOM_SLIDESHOW = "screensaver_slideshow_random";
    private static final String KEY_DURATION_SLIDESHOW = "screensaver_slideshow_duration";
    private static final String KEY_FILL_SCREEN_SLIDESHOW = "screensaver_slideshow_fill_screen";
    private static final String KEY_ALL_ALBUMS_SLIDESHOW = "screensaver_slideshow_all_albums";
    public static final String KEY_ALBUMS_LIST_SLIDESHOW = "screensaver_slideshow_albums_list";

    public boolean random;
    public int duration;
    public boolean fillScreen;
    public boolean allAlbums;
    public Set<String> albumList = new HashSet();
    
    public static ScreenSaverConfig getFromPreferences(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        ScreenSaverConfig c = new ScreenSaverConfig();
        c.random = prefs.getBoolean(KEY_RANDOM_SLIDESHOW, false);
        c.duration = prefs.getInt(KEY_DURATION_SLIDESHOW, 3) * 1000;
        c.fillScreen = prefs.getBoolean(KEY_FILL_SCREEN_SLIDESHOW, false);
        c.allAlbums = prefs.getBoolean(KEY_ALL_ALBUMS_SLIDESHOW, true);
        c.albumList = new HashSet<String>(prefs.getStringSet(KEY_ALBUMS_LIST_SLIDESHOW, new HashSet<String>()));
        return c;
    }

    public static void restorePreferences(Context context, ScreenSaverConfig config) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().putBoolean(KEY_RANDOM_SLIDESHOW, config.random).apply();
        prefs.edit().putInt(KEY_DURATION_SLIDESHOW, config.duration / 1000).apply();
        prefs.edit().putBoolean(KEY_FILL_SCREEN_SLIDESHOW, config.fillScreen).apply();
        prefs.edit().putBoolean(KEY_ALL_ALBUMS_SLIDESHOW, config.allAlbums).apply();
        prefs.edit().putStringSet(KEY_ALBUMS_LIST_SLIDESHOW, config.albumList).apply();
    }
}
