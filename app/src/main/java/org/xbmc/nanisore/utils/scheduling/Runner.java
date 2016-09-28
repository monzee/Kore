package org.xbmc.nanisore.utils.scheduling;

/**
 * Interface for running tasks and handling the (possibly erroneous) result
 */
public interface Runner {

    /**
     * Schedule a task to run and handle the result.
     *
     * @param task A function that returns value; intended to be run in
     *             a background thread.
     * @param handler Action to take when the task returns; intended to be
     *                run in the main thread or the thread of the caller.
     * @param <T> Return type of the task
     * @return A cancellation token object
     */
    <T> Canceller schedule(Producer<T> task, Continuation<T> handler);

    /**
     * Fire-and-forget
     *
     * @return Who cares about the canceller of a fire-and-forget task?
     */
    @SuppressWarnings("rawtypes")
    Canceller schedule(Producer action);

    /**
     * Calls the canceller after the handler has been called.
     *
     * Intended for runners that cache the results so that future invocations
     * of schedule() would replace the value produced by this task. Does not
     * make much sense otherwise because you typically want to cancel a task
     * before the handler is called.
     */
    <T> void once(Producer<T> task, Continuation<T> handler);

    /**
     * Fire-forget-then-destroy
     */
    @SuppressWarnings("rawtypes")
    void once(Producer task);

}
