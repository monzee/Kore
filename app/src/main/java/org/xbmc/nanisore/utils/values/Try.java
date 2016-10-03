package org.xbmc.nanisore.utils.values;

public interface Try<T> {

    interface Handler<T> {
        void got(Try<T> result);
    }

    T get() throws Throwable;

}
