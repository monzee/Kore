package org.xbmc.nanisore.utils.scheduling;

/**
 * A function that produces a value.
 */
public interface Producer<T> {
    T apply() throws Throwable;
}
