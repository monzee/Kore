package org.xbmc.kore.utils;

/**
 * Represents a computation that will eventually complete.
 *
 * This is a monad like {@link Either} and there used to be a monad
 * implementation here but they got moved to {@link Result} which adds
 * semantics for error propagation and recovery. Only the interfaces remain.
 *
 * @param <T> The type of the value being computed
 */
public interface Io<T> {

    /**
     * Start the computation and access its result.
     *
     * The computation is lazy and will not start until this method is called.
     * It is up to the implementation to cache the result so that the
     * subsequent calls receive them faster.
     *
     * @param action A procedure that receives the value.
     */
    void run(Action<? super T> action);

    /**
     * A callback that gets invoked when the computation has completed.
     *
     * @param <T> The type of the value.
     */
    interface Action<T> {
        /**
         * @param value The result of the computation.
         */
        void got(T value);
    }

}
