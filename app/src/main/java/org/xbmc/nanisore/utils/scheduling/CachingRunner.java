package org.xbmc.nanisore.utils.scheduling;

import org.xbmc.nanisore.utils.values.Store;

public interface CachingRunner extends Runner {

    /**
     * Puts the value in the cache if not currently present.
     */
    <T> void put(String key, T value);

    /**
     * Gets a cached value, runs the function then removes the value from
     * the cache.
     *
     * @param then Will be called with defaultValue if the key is not present.
     *             Will never be called with a non-null error.
     * @param <T> The type of the stored value.
     */
    <T> void take(String key, T defaultValue, Continuation<T> then);

    /**
     * Returns a {@link Store} that contains all the cached results so far.
     *
     * There is no guarantee that this store will be synchronized with this
     * runner. Any future changes to the store or this runner's cached values
     * may not necessarily be seen in the other.
     */
    Store toStore();

}
