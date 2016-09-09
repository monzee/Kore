package org.xbmc.kore.utils.scheduling;

import java.util.concurrent.CountDownLatch;

/**
 * Class with default implementation for schedule() and once()
 */
public abstract class BaseRunner implements Runner {

    public static final Continuation<?> NOOP = new Continuation<Object>() {
        @Override
        public void accept(Object result, Throwable error) {}
    };

    @Override
    public Canceller schedule(Producer action) {
        //noinspection unchecked
        return schedule(action, NOOP);
    }

    /**
     * THIS WILL DEADLOCK SINGLE-THREADED RUNNERS! Make sure you override this
     * in that case.
     */
    @Override
    public <T> void once(final Producer<T> task, final Continuation<T> handler) {
        final CountDownLatch barrier = new CountDownLatch(1);
        new Object() {
            Canceller token = schedule(
                    Task.unit(task).map(new Task.Transform<T, T>() {
                        @Override
                        public T apply(T value) throws Throwable {
                            barrier.await();
                            return value;
                        }
                    }),
                    new Continuation<T>() {
                        @Override
                        public void accept(T result, Throwable error) {
                            handler.accept(result, error);
                            token.cancel();
                        }
                    }
            );
        };
        barrier.countDown();
    }

}
