package org.xbmc.nanisore.utils;

public class Either<L extends Throwable, R> implements Try<R> {
    private boolean done;
    private final Object lock = new Object();
    private L left;
    private R right;

    public void left(L value) {
        synchronized (lock) {
            if (!done) {
                left = value;
                done = true;
                lock.notifyAll();
            }
        }
    }

    public void right(R value) {
        synchronized (lock) {
            if (!done) {
                right = value;
                done = true;
                lock.notifyAll();
            }
        }
    }

    public R get() throws L, InterruptedException {
        synchronized (lock) {
            while (!done) {
                lock.wait();
            }
        }
        if (left != null) {
            throw left;
        }
        return right;
    }
}
