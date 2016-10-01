package org.xbmc.nanisore.utils.scheduling;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class ForeverRunner extends BaseRunner {

    private class Repeater<T> implements Canceller {

        boolean firstRun = true;
        boolean cancelled = false;
        Canceller lastCanceller;
        final Task<T> task;
        final Continuation<T> handler;

        Repeater(Task<T> task, Continuation<T> handler) {
            this.task = task.map(new Task.Transform<T, T>() {
                @Override
                public T apply(T value) throws Throwable {
                    sleep(firstRun ? delay : interval);
                    return value;
                }
            });
            this.handler = handler;
        }

        void sleep(final int millis) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (!cancelled) {
                            Thread.sleep(millis);
                        }
                        synchronized (this) {
                            if (!cancelled) {
                                lastCanceller.cancel();
                                lastCanceller = delegate.schedule(task, handler);
                            }
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        cancel();
                    }
                }
            });
            firstRun = false;
        }

        void start() {
            lastCanceller = delegate.schedule(task, handler);
        }

        @Override
        public synchronized void cancel() {
            if (lastCanceller != null) {
                lastCanceller.cancel();
            }
            cancelled = true;
            lastCanceller = null;
        }
    }

    private final Runner delegate;
    private final Executor executor;
    private final int delay;
    private final int interval;

    public ForeverRunner(Runner delegate, int interval) {
        this(delegate, interval, interval);
    }

    public ForeverRunner(Runner delegate, int delay, int interval) {
        this(delegate, Executors.newSingleThreadExecutor(), delay, interval);
    }

    public ForeverRunner(Runner delegate, Executor executor, int interval) {
        this(delegate, executor, interval, interval);
    }

    public ForeverRunner(Runner delegate, Executor executor, int delay, int interval) {
        this.delegate = delegate;
        this.executor = executor;
        this.delay = delay;
        this.interval = interval;
    }

    @Override
    public <T> Canceller schedule(Producer<T> task, Continuation<T> handler) {
        Repeater<T> repeater = new Repeater<>(Task.unit(task), handler);
        repeater.start();
        return repeater;
    }

}
