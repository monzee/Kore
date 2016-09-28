package org.xbmc.nanisore.utils;

public interface Try<T> {
    T get() throws Throwable;
}
