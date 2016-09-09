package org.xbmc.kore.utils.scheduling;

/**
 * Interface for objects returned by a runner
 */
public interface Canceller {
    /** Cancels a running task */
    void cancel();
}
