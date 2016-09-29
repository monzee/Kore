package org.xbmc.nanisore.utils;

public abstract class Lazy<T> {
    private T value;

    public synchronized T get() {
        if (value == null) {
            value = value();
        }
        return value;
    }

    protected abstract T value();
}
