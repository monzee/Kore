package org.xbmc.nanisore.screens;

import android.content.SharedPreferences;

import org.xbmc.nanisore.utils.Options;

public class AndroidOptions implements Options {

    private final SharedPreferences prefs;

    public AndroidOptions(SharedPreferences prefs) {
        this.prefs = prefs;
    }

    @Override
    public boolean get(String key, boolean whenAbsent) {
        return prefs.getBoolean(key, whenAbsent);
    }

    @Override
    public String get(String key, String whenAbsent) {
        return prefs.getString(key, whenAbsent);
    }

    @Override
    public int get(String key, int whenAbsent) {
        return prefs.getInt(key, whenAbsent);
    }

}
