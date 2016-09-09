package org.xbmc.kore.utils.scheduling;

/**
 * A function that does something with the result or an error that occurred
 * while trying to produce a result.
 */
public interface Continuation<T> {
    /**
     * Do something with a value.
     *
     * @param result Definitely null when there's an error; possibly null
     *               on success.
     * @param error Only null when the task successfully produced a value.
     */
    void accept(T result, Throwable error);
}
