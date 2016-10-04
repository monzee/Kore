package org.xbmc.nanisore.utils.values;

import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

public class SoftStore implements Store {

    private final Map<String, SoftReference<Object>> store = new HashMap<>();

    @Override
    public synchronized <T> void put(String key, T value) {
        store.put(key, new SoftReference<Object>(value));
    }

    @Override
    public synchronized <T> void softPut(String key, T value) {
        if (!store.containsKey(key)) {
            store.put(key, new SoftReference<Object>(value));
        }
    }

    @Override
    public <T> T get(String key, T orElse) {
        if (store.containsKey(key)) {
            //noinspection unchecked
            return (T) store.get(key).get();
        }
        return orElse;
    }

    @Override
    public <T> T hardGet(String key, T orElse) {
        if (store.containsKey(key)) {
            //noinspection unchecked
            return (T) store.get(key).get();
        }
        put(key, orElse);
        return orElse;
    }

}
