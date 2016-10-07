package org.xbmc.nanisore.utils.values;

public interface Store {
    /**
     * Puts a value, replacing the old value if present.
     */
    <T> void put(String key, T value);

    /**
     * Puts a value only if it doesn't currently exist.
     */
    <T> void softPut(String key, T value);

    /**
     * Gets a value, returning a default value if absent.
     */
    <T> T get(String key, T orElse);

    /**
     * Gets a value if present, otherwise puts this value and returns it.
     */
    <T> T hardGet(String key, T orElse);

    // TODO: consider having a take(String, T) -> T method
}
