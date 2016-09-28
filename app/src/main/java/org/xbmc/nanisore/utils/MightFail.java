package org.xbmc.nanisore.utils;

public abstract class MightFail<T> {
    public final T ok;

    public MightFail(T callback) {
        ok = callback;
    }

    public abstract void fail(Throwable error);
}
