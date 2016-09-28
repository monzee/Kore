package org.xbmc.nanisore.utils;

public class Either<L extends Throwable, R> {
    private volatile boolean done;
    private final Object lock = new Object();
    private L left;
    private R right;

    public void left(L value) {
        if (!done) {
            synchronized (lock) {
                left = value;
                done = true;
                lock.notifyAll();
            }
        }
    }

    public void right(R value) {
        if (!done) {
            synchronized (lock) {
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
