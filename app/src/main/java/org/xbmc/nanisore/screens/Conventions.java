package org.xbmc.nanisore.screens;

import org.xbmc.nanisore.utils.values.Do;

/**
 * Common use cases.
 *
 * @param <T> The type of the object being saved/restored on config change.
 */
public interface Conventions<T> {

    /**
     * Typically called during presenter unbind().
     *
     * @param state The next caller of restore() should receive this instance.
     */
    void save(T state);

    /**
     * Typically called during presenter bind().
     *
     * @param then Will receive the last instance saved or a fresh one on
     *             first run
     */
    void restore(Do.Just<T> then);

    /**
     * Run action in the background and return immediately.
     *
     * Don't care if it succeeds or fails. It will probably succeed.
     *
     * Meant for blocking actions. Async calls are already fire-and-forget
     * if you don't attach a listener.
     */
    void fireAndForget(Runnable action);

}
