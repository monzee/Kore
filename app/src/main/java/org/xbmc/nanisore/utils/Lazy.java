package org.xbmc.nanisore.utils;

public abstract class Lazy<T> {
    private T value;

    public T get() {
        if (value == null) {
            value = produce();
        }
        return value;
    }

    protected abstract T produce();
}
