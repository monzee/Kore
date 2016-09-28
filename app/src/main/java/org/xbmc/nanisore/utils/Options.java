package org.xbmc.nanisore.utils;

public interface Options {
    boolean get(String key, boolean whenAbsent);
    String get(String key, String whenAbsent);
    int get(String key, int whenAbsent);
}
