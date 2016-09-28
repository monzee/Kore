package org.xbmc.nanisore.utils;

public interface Console {
    void tell(String message);
    void log(Log level, String message);
}
