package org.xbmc.nanisore.screens;

/**
 * Common use cases.
 *
 * @param <T> The type of the object being saved/restored on config change.
 */
public interface Conventions<T> {

    // TODO: this could have a more generic name
    // so the presenters wouldn't have to make up interfaces for each use case
    interface OnRestore<T> {
        void restored(T state);
    }

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
    void restore(OnRestore<T> then);

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
