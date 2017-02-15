package org.xbmc.kore.utils;

public interface Transform<T, U> {
    U from(T value);
}
