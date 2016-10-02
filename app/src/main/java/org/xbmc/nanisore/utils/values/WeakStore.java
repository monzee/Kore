package org.xbmc.nanisore.utils.values;

import java.util.Map;
import java.util.WeakHashMap;

public class WeakStore implements Store {

    private final Map<String, Object> store = new WeakHashMap<>();

    @Override
    public <T> void put(String key, T value) {
        store.put(key, value);
    }

    @Override
    public synchronized <T> void softPut(String key, T value) {
        if (!store.containsKey(key)) {
            store.put(key, value);
        }
    }

    @Override
    public <T> T get(String key, T orElse) {
        if (store.containsKey(key)) {
            //noinspection unchecked
            return (T) store.get(key);
        }
        return orElse;
    }

    @Override
    public <T> T hardGet(String key, T orElse) {
        if (store.containsKey(key)) {
            //noinspection unchecked
            return (T) store.get(key);
        }
        put(key, orElse);
        return orElse;
    }

}
